/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.sling.jcr.repoinit.impl;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;

import java.io.IOException;

import org.apache.sling.testing.mock.sling.ResourceResolverType;
import org.apache.sling.testing.mock.sling.junit.SlingContext;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class MultiValuePropertyTest {

    @Rule
    public final SlingContext context = new SlingContext(ResourceResolverType.JCR_OAK);

    private TestUtil U;

    @Before
    public void setup() throws RepositoryException, IOException {
        U = new TestUtil(context);
    }

    // See SLING-13124
    @Test
    public void setSingleValueToMultiValueProperty() throws Exception {
        Session s = context.resourceResolver().adaptTo(Session.class);
        Node root = s.getRootNode();
        Node child = root.addNode("child");
        child.setProperty("mv", new String[] {"value1", "value2"});
        s.save();
        U.parseAndExecute("ensure nodes /child with properties \n set mv to singlevalue\n end");
    }
}
