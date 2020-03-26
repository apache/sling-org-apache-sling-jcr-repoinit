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

import java.util.List;
import java.util.Calendar;

import javax.jcr.Session;
import javax.jcr.Node;
import javax.jcr.Value;
import javax.jcr.PropertyType;
import javax.jcr.RepositoryException;

import org.apache.jackrabbit.value.BooleanValue;
import org.apache.jackrabbit.value.DateValue;
import org.apache.jackrabbit.value.DoubleValue;
import org.apache.jackrabbit.value.LongValue;
import org.apache.jackrabbit.value.StringValue;

import org.apache.sling.repoinit.parser.operations.PropertyLine;
import org.apache.sling.repoinit.parser.operations.SetProperties;

/**
 * OperationVisitor which processes only operations related to setting node properties. Having several such specialized visitors makes it easy to control the
 * execution order.
 */
class NodePropertiesVisitor extends DoNothingVisitor {

    /**
     * Create a visitor using the supplied JCR Session.
     *
     * @param s must have sufficient rights to set properties on a path.
     */
    public NodePropertiesVisitor(Session s) {
        super(s);
    }

    @Override
    public void visitSetProperties(SetProperties sp) {
        List<String> nodePaths = sp.getPaths();
        List<PropertyLine> propertyLines = sp.getPropertyLines();
        for (String nodePath : nodePaths) {
            try {
                log.info("Setting properties on nodePath '{}'", nodePath);
                Node n = session.getNode(nodePath);
                for (PropertyLine pl : propertyLines) {
                    String pName = pl.getPropertyName();
                    PropertyLine.PropertyType pType = pl.getPropertyType();
                    List<Object> values = pl.getPropertyValues();
                    boolean setOnlyIfNull = pl.isDefault();
                    int type = PropertyType.valueFromName(pType.name());
                    boolean isExistingNonNullProperty = n.hasProperty(pName) && n.getProperty(pName) != null;
                    if (!setOnlyIfNull || (setOnlyIfNull && !isExistingNonNullProperty)) {
                        if (values.size() > 1) {
                            Value[] pValues = convertToValues(values);
                            n.setProperty(pName, pValues, type);
                        } else {
                            Value pValue = convertToValue(values.get(0));
                            n.setProperty(pName, pValue, type);
                        }
                    } else {
                        log.info("Property '{}' is already set on path '{}'. Will not overwrite the default", pName, nodePath);
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
