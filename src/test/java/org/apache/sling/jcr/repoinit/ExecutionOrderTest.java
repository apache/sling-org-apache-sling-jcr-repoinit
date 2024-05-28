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
package org.apache.sling.jcr.repoinit;

import ch.qos.logback.classic.Level;
import org.apache.sling.jcr.repoinit.impl.TestUtil;
import org.apache.sling.repoinit.parser.RepoInitParsingException;
import org.apache.sling.testing.mock.sling.ResourceResolverType;
import org.apache.sling.testing.mock.sling.junit.SlingContext;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/** Verify that namespaces and nodetypes are executed before path creation. But otherwise,
 * the execution order should match the order of the statements.
 * */
public class ExecutionOrderTest {

    @Rule
    public final SlingContext context = new SlingContext(ResourceResolverType.JCR_OAK);

    private TestUtil U;

    private static final String TEST_ID = UUID.randomUUID().toString();
    private static final String NS_PREFIX = ExecutionOrderTest.class.getSimpleName();
    private static final String NS_URI = "uri:" + NS_PREFIX + ":" + TEST_ID;
    private static final String PATH = "/" + NS_PREFIX + "-" + TEST_ID;
    private static final String GROUP_NAME = "testGroup";

    @Before
    public void setup() throws RepositoryException, RepoInitParsingException {
        U = new TestUtil(context);
    }

    @Test
    public void pathCreated() throws RepositoryException, RepoInitParsingException {
        U.parseAndExecute(
                "create path (" + NS_PREFIX + ":foo) " + PATH,
                "register nodetypes",
                "<<===",
                "[" + NS_PREFIX + ":foo] > nt:unstructured",
                "===>>"
                ,"register namespace (" + NS_PREFIX + ") " + NS_URI
        );
        final Node n = U.getAdminSession().getNode(PATH);
        assertEquals(NS_PREFIX + ":foo", n.getProperty("jcr:primaryType").getString());
    }

    @Test
    public void createGroupWithACLsThenDeleteGroup() throws RepositoryException, RepoInitParsingException {
        U.parseAndExecute(
                "create path (nt:folder) " + PATH,
                "create group " + GROUP_NAME,
                "set ACL for " + GROUP_NAME,
                "  allow jcr:read on " + PATH,
                "  deny jcr:write on " + PATH,
                "end",
                "delete group " + GROUP_NAME
        );

        U.assertGroup("Group " + GROUP_NAME + " should have been deleted", GROUP_NAME, false);

        // ACLs should still be present
        U.assertPrivileges(GROUP_NAME, PATH, true, "jcr:read");
        U.assertPrivileges(GROUP_NAME, PATH, false, "jcr:write");
    }

    @Test
    public void legacySetAclBeforeCreatingGroupTest() throws Exception {
        try (LogCapture capture = new LogCapture("org.apache.sling.jcr.repoinit.impl.JcrRepoInitOpsProcessorImpl", true)) {
            U.parseAndExecute(
                    "create path (nt:folder) " + PATH,
                    "set ACL for " + GROUP_NAME,
                    "  allow jcr:read on " + PATH,
                    "  deny jcr:write on " + PATH,
                    "end",
                    "create group " + GROUP_NAME
            );

            capture.assertContains(Level.WARN, "DEPRECATION", "set ACL for " + GROUP_NAME);
        } catch (RuntimeException e) {
            fail("Legacy support should have reordered \"create group\" before \"set ACL\"");
        }
    }

    @Test
    public void legacyAddMemberBeforeCreatingGroupTest() throws Exception {
        try (LogCapture capture = new LogCapture("org.apache.sling.jcr.repoinit.impl.JcrRepoInitOpsProcessorImpl", true)) {
            U.parseAndExecute(
                    "create group " + GROUP_NAME + "_1",
                    "add " + GROUP_NAME + "_1 to group " + GROUP_NAME,
                    "create group " + GROUP_NAME // reordered by legacy code
            );

            capture.assertContains(Level.WARN, "DEPRECATION", "add " + GROUP_NAME + "_1 to group ");
        } catch (RuntimeException e) {
            fail("Legacy support should have reordered \"create group\" before \"add ... to group\"");
        }
    }
}
