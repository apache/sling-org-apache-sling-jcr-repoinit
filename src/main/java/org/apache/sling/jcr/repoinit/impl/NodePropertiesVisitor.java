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
import java.util.Calendar;
import java.util.List;

import javax.jcr.Node;
import javax.jcr.PathNotFoundException;
import javax.jcr.PropertyType;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.Value;

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

/**
 * OperationVisitor which processes only operations related to setting node
 * properties. Having several such specialized visitors makes it easy to control
 * the execution order.
 */
class NodePropertiesVisitor extends DoNothingVisitor {
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
     * True if the property needs to be set - if false, it is not touched. This
     * handles the "default" repoinit instruction, which means "do not change the
     * property if already set"
     *
     * @throws RepositoryException
     * @throws PathNotFoundException
     */
    private static boolean needToSetProperty(Node n, PropertyLine line) throws RepositoryException {
        if(!line.isDefault()) {
            // It's a "set" line -> overwrite existing value if any
            return true;
        }

        // Otherwise set the property only if not set yet
        final String name = line.getPropertyName();
        return(!n.hasProperty(name) || n.getProperty(name) == null);
    }

    /**
     * True if the property needs to be set - if false, it is not touched. This
     * handles the "default" repoinit instruction, which means "do not change the
     * property if already set"
     *
     * @throws RepositoryException
     * @throws PathNotFoundException
     */
    private static boolean needToSetProperty(Authorizable a, String pRelPath, boolean isDefault) throws RepositoryException {
        if (!isDefault) {
            // It's a "set" line -> overwrite existing value if any
            return true;
        }

        // Otherwise set the property only if not set yet
        return(!a.hasProperty(pRelPath) || a.getProperty(pRelPath) == null);
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

    @Override
    public void visitSetProperties(SetProperties sp) {
        for (String nodePath : sp.getPaths()) {
            try {
                if (nodePath.startsWith(PATH_AUTHORIZABLE)) {
                    // special case for setting properties on authorizable
                    int lastHashIndex = nodePath.lastIndexOf(SUBTREE_DELIMINATOR);
                    if (lastHashIndex == -1) {
                        throw new IllegalStateException("Invalid format of authorizable path: # deliminator expected.");
                    }
                    String ids = nodePath.substring(PATH_AUTHORIZABLE.length(), lastHashIndex);
                    String subTreePath = nodePath.substring(lastHashIndex + 1);
                    for (Authorizable a : getAuthorizables(session, ids)) {
                        log.info("Setting properties on authorizable '{}'", a.getID());
                        for (PropertyLine pl : sp.getPropertyLines()) {
                            final String pName = pl.getPropertyName();
                            final String pRelPath = toRelPath(subTreePath, pName);
                            final List<Object> values = pl.getPropertyValues();
                            if (needToSetProperty(a, pRelPath, pl.isDefault())) {
                                if (values.size() > 1) {
                                    Value[] pValues = convertToValues(values);
                                    a.setProperty(pRelPath, pValues);
                                } else {
                                    Value pValue = convertToValue(values.get(0));
                                    a.setProperty(pRelPath, pValue);
                                }
                            } else {
                                log.info(
                                    "Property '{}' already set on authorizable '{}', existing value will not be overwritten in 'default' mode",
                                    pRelPath, a.getID());
                            }
                        }
                    }
                } else {
                    log.info("Setting properties on nodePath '{}'", nodePath);
                    Node n = session.getNode(nodePath);
                    for (PropertyLine pl : sp.getPropertyLines()) {
                        final String pName = pl.getPropertyName();
                        final PropertyLine.PropertyType pType = pl.getPropertyType();
                        final List<Object> values = pl.getPropertyValues();
                        final int type = PropertyType.valueFromName(pType.name());
                        if (needToSetProperty(n, pl)) {
                            if (values.size() > 1) {
                                Value[] pValues = convertToValues(values);
                                n.setProperty(pName, pValues, type);
                            } else {
                                Value pValue = convertToValue(values.get(0));
                                n.setProperty(pName, pValue, type);
                            }
                        } else {
                            log.info(
                                "Property '{}' already set on path '{}', existing value will not be overwritten in 'default' mode",
                                pName, nodePath);
                        }
                    }
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
            throw new RuntimeException("Unable to convert " + value + " to jcr Value");
        }
        return convertedValue;
    }
}
