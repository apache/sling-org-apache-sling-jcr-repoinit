/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.sling.jcr.repoinit.impl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;
import java.util.stream.Stream;

import javax.jcr.Node;
import javax.jcr.PathNotFoundException;
import javax.jcr.Property;
import javax.jcr.PropertyType;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.Value;
import javax.jcr.nodetype.NodeType;
import javax.jcr.nodetype.PropertyDefinition;

import org.apache.jackrabbit.api.security.user.Authorizable;
import org.apache.jackrabbit.util.Text;
import org.apache.jackrabbit.value.BooleanValue;
import org.apache.jackrabbit.value.DateValue;
import org.apache.jackrabbit.value.DoubleValue;
import org.apache.jackrabbit.value.LongValue;
import org.apache.jackrabbit.value.StringValue;
import org.apache.sling.repoinit.parser.operations.PropertyLine;
import org.apache.sling.repoinit.parser.operations.SetProperties;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


/**
 * OperationVisitor which processes only operations related to setting node
 * properties. Having several such specialized visitors makes it easy to control
 * the execution order.
 * @see NodeVisitor NodeVisitor, for operations related to nodes and their types
 */
class NodePropertiesVisitor extends DoNothingVisitor {
    /**
     * The repoinit.parser transforms the authorizable(ids)[/relative_path] path
     * syntax from the original source into ":authorizable:ids#/relative_path" in the 
     * values provided from {@link SetProperties#getPaths()}
     * 
     * These constants are used to unwind those values into the parts for processing
     */
    private static final String PATH_AUTHORIZABLE = ":authorizable:";
    private static final char ID_DELIMINATOR = ',';
    private static final char SUBTREE_DELIMINATOR = '#';

    /**
     * Create a visitor using the supplied JCR Session.
     *
     * @param s must have sufficient rights to set properties on a path.
     */
    public NodePropertiesVisitor(Session s) {
        super(s);
    }

    /**
     * Find the PropertyDefinition for the specified propName
     *
     * @param propName the propertyName to check
     * @param parentNode the parent node where the property will be set
     * @return the property definition of the property or null if it could not be determined
     */
    private static @Nullable PropertyDefinition resolvePropertyDefinition(@NotNull String propName, @NotNull Node parentNode) throws RepositoryException {
        NodeType primaryNodeType = parentNode.getPrimaryNodeType();
        // try the primary type
        PropertyDefinition propDef = resolvePropertyDefinition(propName, primaryNodeType);
        if (propDef == null) {
            // not found in the primary type, so try the mixins
            NodeType[] mixinNodeTypes = parentNode.getMixinNodeTypes();
            for (NodeType mixinNodeType : mixinNodeTypes) {
                propDef = resolvePropertyDefinition(propName, mixinNodeType);
                if (propDef != null) {
                    break;
                }
            }
        }
        return propDef;
    }

    /**
     * Inspect the NodeType definition to try to determine the
     * requiredType for the specified property
     *
     * @param propName the propertyName to check
     * @param nodeType the node type to be searched for the property definition
     * @return the required type of the property or {@link PropertyType#UNDEFINED} if it could not be determined
     */
    private static @Nullable PropertyDefinition resolvePropertyDefinition(@NotNull String propName, @NotNull NodeType nodeType) {
        return Stream.of(nodeType.getPropertyDefinitions())
            .filter(pd -> propName.equals(pd.getName()))
            .findFirst()
            .orElse(null);
    }

    /**
     * SLING-11293 - Check if a property is defined as autocreated and the current value
     * is the same as the autocreated default value
     *
     * @param n the node to check
     * @param propertyPath the property relative path to check
     * @return true or false
     */
    protected static boolean isUnchangedAutocreatedProperty(Node n, final String propertyPath)
            throws RepositoryException {
        if (n.hasProperty(propertyPath)) {
            final Property property = n.getProperty(propertyPath);
            final String name = property.getName();
            final PropertyDefinition pd = resolvePropertyDefinition(name, property.getParent());
            if (pd != null && pd.isAutoCreated()) {
                // if the current value is the same as the autocreated default values
                //  then allow the value to be changed.
                return Arrays.equals(
                        pd.getDefaultValues(),
                        property.isMultiple() ? property.getValues() : new Value[] {property.getValue()}
                );
            }
        }
        return false;
    }

    /**
     * True if the property needs to be set - if false, it is not touched. This
     * handles the "default" repoinit instruction, which means "do not change the
     * property if already set"
     *
     * @throws RepositoryException
     * @throws PathNotFoundException
     */
    private static boolean needToSetProperty(@NotNull Node n, @NotNull PropertyLine line) throws RepositoryException {
        if (!line.isDefault()) {
            // It's a "set" line -> overwrite existing value if any
            return true;
        }

        // Otherwise set the property only if not set yet
        final String name = line.getPropertyName();
        boolean needToSet;
        if (isUnchangedAutocreatedProperty(n, name)) { // SLING-11293
            needToSet = true;
        } else {
            needToSet = (!n.hasProperty(name) || n.getProperty(name) == null);
        }
        return needToSet;
    }

    /**
     * True if the property needs to be set - if false, it is not touched. This
     * handles the "default" repoinit instruction, which means "do not change the
     * property if already set"
     *
     * @throws RepositoryException
     * @throws PathNotFoundException
     */
    private static boolean needToSetProperty(Session session, Authorizable a, String pRelPath, PropertyLine line) throws RepositoryException {
        if (!line.isDefault()) {
            // It's a "set" line -> overwrite existing value if any
            return true;
        }

        // Otherwise set the property only if not set yet
        boolean needToSet;
        Node n = null;
        if (session.nodeExists(a.getPath())) {
            n = session.getNode(a.getPath());
        }
        if (n != null && isUnchangedAutocreatedProperty(n, pRelPath)) { // SLING-11293
            needToSet = true;
        } else {
            needToSet = (!a.hasProperty(pRelPath) || a.getProperty(pRelPath) == null);
        }
        return needToSet;
    }

    /**
     * Build relative property path from a subtree path and a property name
     * @param subTreePath the subtree path (may be null or empty)
     * @param name the property name
     * @return the relative path of the property
     */
    private static String toRelPath(String subTreePath, final String name) {
        final String pRelPath;
        if (subTreePath == null || subTreePath.isEmpty()) {
            pRelPath = name;
        } else {
            if (subTreePath.startsWith("/")) {
                subTreePath = subTreePath.substring(1);
            }
            pRelPath = String.format("%s/%s", subTreePath, name);
        }
        return pRelPath;
    }

    /**
     * Lookup the authorizables for the given ids
     * @param session the jcr session
     * @param ids delimited list of authorizable ids
     * @return iterator over the found authorizables
     */
    @NotNull
    private static Iterable<Authorizable> getAuthorizables(@NotNull Session session, @NotNull String ids) throws RepositoryException {
        List<Authorizable> authorizables = new ArrayList<>();
        for (String id : Text.explode(ids, ID_DELIMINATOR)) {
            Authorizable a = UserUtil.getAuthorizable(session, id);
            if (a == null) {
                throw new PathNotFoundException("Cannot resolve path of authorizable with id '" + id + "'.");
            }
            authorizables.add(a);
        }
        return authorizables;
    }

    /**
     * Set properties on a user or group
     * 
     * @param nodePath the target path
     * @param propertyLines the property lines to process to set the properties
     */
    private void setAuthorizableProperties(String nodePath, List<PropertyLine> propertyLines) throws RepositoryException {
        int lastHashIndex = nodePath.lastIndexOf(SUBTREE_DELIMINATOR);
        if (lastHashIndex == -1) {
            throw new IllegalStateException("Invalid format of authorizable path: # deliminator expected.");
        }
        String ids = nodePath.substring(PATH_AUTHORIZABLE.length(), lastHashIndex);
        String subTreePath = nodePath.substring(lastHashIndex + 1);
        for (Authorizable a : getAuthorizables(session, ids)) {
            log.info("Setting properties on authorizable '{}'", a.getID());
            for (PropertyLine pl : propertyLines) {
                final String pName = pl.getPropertyName();
                final String pRelPath = toRelPath(subTreePath, pName);
                if (needToSetProperty(session, a, pRelPath, pl)) {
                    final List<Object> values = pl.getPropertyValues();
                    if (values.size() > 1) {
                        Value[] pValues = convertToValues(values);
                        a.setProperty(pRelPath, pValues);
                    } else {
                        Value pValue = convertToValue(values.get(0));
                        a.setProperty(pRelPath, pValue);
                    }
                } else {
                    log.info("Property '{}' already set on authorizable '{}', existing value will not be overwritten in 'default' mode",
                        pRelPath, a.getID());
                }
            }
        }
    }

    /**
     * Set properties on a JCR node
     * 
     * @param nodePath the target path
     * @param propertyLines the property lines to process to set the properties
     */
    private void setNodeProperties(String nodePath, List<PropertyLine> propertyLines) throws RepositoryException {
        log.info("Setting properties on nodePath '{}'", nodePath);
        Node n = session.getNode(nodePath);
        for (PropertyLine pl : propertyLines) {
            final String pName = pl.getPropertyName();
            if (needToSetProperty(n, pl)) {
                final int newType = PropertyType.valueFromName(pl.getPropertyType().name());
                Value[] newValues = convertToValues(pl.getPropertyValues());
                Property oldProperty = n.hasProperty(pName) ? n.getProperty(pName)  : null;
                // only set property if type and/or values change
                // note: Node#setProperty() touches the node even if the property value is unchanged
                //       and thus needs to be explicitly avoided
                if(hasPropertyChange(oldProperty, newType, newValues)) {
                    if (newValues.length == 1) {
                        n.setProperty(pName, newValues[0], newType);
                    } else {
                        n.setProperty(pName, newValues, newType);
                    }
                }
            } else {
                log.info("Property '{}' already set on path '{}', existing value will not be overwritten in 'default' mode",
                    pName, nodePath);
            }
        }
    }

    private boolean hasPropertyChange(Property oldProperty, int newType, Value... newValues) throws RepositoryException {
        if (oldProperty == null || oldProperty.getType() != newType) {
            return true;
        }

        final Value[] oldValues = oldProperty.isMultiple() ? oldProperty.getValues() : new Value[]{ oldProperty.getValue() };
        if (oldValues.length != newValues.length) {
            return true;
        }

        for (int i = 0; i < oldValues.length; i++) {
            final Value oldValue = oldValues[i];
            final Value newValue = newValues[i];
            if (!valueEquals(oldValue, newValue)) {
                return true;
            }
        }
        return false;
    }

    private static boolean valueEquals(Value oldValue, Value newValue) throws RepositoryException {
        return oldValue.getType() == newValue.getType()
                && oldValue.getString().equals(newValue.getString());
    }

    @Override
    public void visitSetProperties(SetProperties sp) {
        for (String nodePath : sp.getPaths()) {
            try {
                if (nodePath.startsWith(PATH_AUTHORIZABLE)) {
                    // special case for setting properties on authorizable
                    setAuthorizableProperties(nodePath, sp.getPropertyLines());
                } else {
                    setNodeProperties(nodePath, sp.getPropertyLines());
                }
            } catch (RepositoryException e) {
                report(e, "Unable to set properties on path [" + nodePath + "]:" + e);
            }
        }
    }

    private Value[] convertToValues(List<Object> values) {
        int size = values.size();
        Value[] valueArray = new Value[size];
        for(int i = 0; i < size; i++) {
           valueArray[i] = convertToValue(values.get(i));
        }
        return valueArray;
    }

    private Value convertToValue(Object value) {
        Value convertedValue = null;
        if (value instanceof String) {
            convertedValue = new StringValue((String) value);
        } else if (value instanceof Double) {
            convertedValue = new DoubleValue((Double) value);
        } else if (value instanceof Long) {
            convertedValue = new LongValue((Long) value);
        } else if (value instanceof Boolean) {
            convertedValue = new BooleanValue((Boolean) value);
        } else if (value instanceof Calendar) {
            convertedValue = new DateValue((Calendar) value);
        } else {
            report("Unable to convert " + value + " to jcr Value");
        }
        return convertedValue;
    }
}
