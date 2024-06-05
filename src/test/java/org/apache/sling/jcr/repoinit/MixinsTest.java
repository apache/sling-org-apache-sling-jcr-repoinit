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
package org.apache.sling.jcr.repoinit;

import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.nodetype.NoSuchNodeTypeException;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;

import ch.qos.logback.classic.Level;
import org.apache.sling.jcr.repoinit.impl.TestUtil;
import org.apache.sling.testing.mock.sling.ResourceResolverType;
import org.apache.sling.testing.mock.sling.junit.SlingContext;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Test the creation of paths with specific node types */
public class MixinsTest {

    @Rule
    public final SlingContext context = new SlingContext(ResourceResolverType.JCR_OAK);

    private TestUtil U;

    @Before
    public void setup() throws RepositoryException, IOException {
        U = new TestUtil(context);
    }

    @Test
    public void addOneMixinOnOnePath() throws Exception {
        U.parseAndExecute("create path /addOneMixinOnOnePath(nt:unstructured)");
        U.assertNodeExists("/addOneMixinOnOnePath", "nt:unstructured", Collections.emptyList());

        U.parseAndExecute("add mixin mix:lockable to /addOneMixinOnOnePath");
        U.assertNodeExists("/addOneMixinOnOnePath", "nt:unstructured", Collections.singletonList("mix:lockable"));
    }

    @Test
    public void addTwoMixinsOnTwoPaths() throws Exception {
        U.parseAndExecute("create path /addTwoMixinsOnOnePath1(nt:unstructured)\n"
                + "create path /addTwoMixinsOnOnePath2(nt:unstructured)");
        U.assertNodeExists("/addTwoMixinsOnOnePath1", "nt:unstructured", Collections.emptyList());
        U.assertNodeExists("/addTwoMixinsOnOnePath1", "nt:unstructured", Collections.emptyList());

        U.parseAndExecute(
                "add mixin mix:lockable,mix:referenceable to /addTwoMixinsOnOnePath1,/addTwoMixinsOnOnePath2");
        U.assertNodeExists(
                "/addTwoMixinsOnOnePath1", "nt:unstructured", Arrays.asList("mix:lockable", "mix:referenceable"));
        U.assertNodeExists(
                "/addTwoMixinsOnOnePath1", "nt:unstructured", Arrays.asList("mix:lockable", "mix:referenceable"));
    }

    @Test
    public void removeOneMixinFromOnePath() throws Exception {
        U.parseAndExecute("create path /removeOneMixinOnOnePath(nt:unstructured mixin mix:lockable,mix:referenceable)");
        U.assertNodeExists(
                "/removeOneMixinOnOnePath", "nt:unstructured", Arrays.asList("mix:lockable", "mix:referenceable"));

        U.parseAndExecute("remove mixin mix:lockable from /removeOneMixinOnOnePath");
        U.assertNodeExists(
                "/removeOneMixinOnOnePath", "nt:unstructured", Collections.singletonList("mix:referenceable"));

        U.parseAndExecute("remove mixin mix:referenceable from /removeOneMixinOnOnePath");
        U.assertNodeExists("/removeOneMixinOnOnePath", "nt:unstructured", Collections.emptyList());
    }

    @Test
    public void removeTwoMixinsFromTwoPaths() throws Exception {
        U.parseAndExecute(
                "create path /removeTwoMixinsOnOnePath1(nt:unstructured mixin mix:lockable,mix:referenceable,mix:lastModified)\n"
                        + "create path /removeTwoMixinsOnOnePath2(nt:unstructured mixin mix:lockable,mix:referenceable)");
        U.assertNodeExists(
                "/removeTwoMixinsOnOnePath1",
                "nt:unstructured",
                Arrays.asList("mix:lockable", "mix:referenceable", "mix:lastModified"));
        U.assertNodeExists(
                "/removeTwoMixinsOnOnePath2", "nt:unstructured", Arrays.asList("mix:lockable", "mix:referenceable"));

        U.parseAndExecute(
                "remove mixin mix:lockable,mix:referenceable from /removeTwoMixinsOnOnePath1,/removeTwoMixinsOnOnePath2");
        U.assertNodeExists(
                "/removeTwoMixinsOnOnePath1", "nt:unstructured", Collections.singletonList("mix:lastModified"));
        U.assertNodeExists("/removeTwoMixinsOnOnePath2", "nt:unstructured", Collections.emptyList());
    }

    @Test
    public void addMixinOnNotExistingPath() throws Exception {
        try (LogCapture capture = new LogCapture("org.apache.sling.jcr.repoinit.impl.NodeVisitor", true)) {
            // this should just log a warning and continue
            U.parseAndExecute("add mixin mix:lockable to /addMixinOnNotExistingPath");
            assertNodeNotExists("/addMixinOnNotExistingPath");

            // verify the warning was logged
            capture.assertContains(Level.WARN, "Path does not exist, not adding mixins: /addMixinOnNotExistingPath");
        }
    }

    @Test
    public void removeMixinFromNotExistingPath() throws Exception {
        try (LogCapture capture = new LogCapture("org.apache.sling.jcr.repoinit.impl.NodeVisitor", true)) {
            // this should just log a warning and continue
            U.parseAndExecute("remove mixin mix:lockable from /removeMixinFromNotExistingPath");
            assertNodeNotExists("/removeMixinFromNotExistingPath");

            // verify the warning was logged
            capture.assertContains(
                    Level.WARN, "Path does not exist, not removing mixins: /removeMixinFromNotExistingPath");
        }
    }

    @Test
    public void addNonExistingMixinToPath() throws Exception {
        U.parseAndExecute("create path /addNonExistingMixinToPath(nt:unstructured)");
        try {
            U.parseAndExecute("add mixin mix:invalid to /addNonExistingMixinToPath");
        } catch (Exception e) {
            assertTrue("Expected NoSuchNodeTypeException", e.getCause() instanceof NoSuchNodeTypeException);
        }
    }

    @Test
    public void removeNonExistingMixinFromPath() throws Exception {
        U.parseAndExecute("create path /removeNonExistingMixinFromPath(nt:unstructured)");
        try {
            U.parseAndExecute("remove mixin mix:invalid from /removeNonExistingMixinFromPath");
        } catch (Exception e) {
            assertTrue("Expected NoSuchNodeTypeException", e.getCause() instanceof NoSuchNodeTypeException);
        }
    }

    protected void assertNodeNotExists(String path) throws RepositoryException {
        Session adminSession = context.resourceResolver().adaptTo(Session.class);
        assertFalse("Node should not exist", adminSession.nodeExists(path));
    }
}
