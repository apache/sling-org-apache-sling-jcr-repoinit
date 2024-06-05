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

import java.util.Collections;
import java.util.Random;

import org.apache.jackrabbit.api.security.user.AuthorizableTypeException;
import org.apache.sling.jcr.repoinit.impl.TestUtil;
import org.apache.sling.jcr.repoinit.impl.UserUtil;
import org.apache.sling.testing.mock.sling.ResourceResolverType;
import org.apache.sling.testing.mock.sling.junit.SlingContext;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/** Test the creation of groups */
public class CreateGroupsTest {

    @Rule
    public final SlingContext context = new SlingContext(ResourceResolverType.JCR_OAK);

    private static final Random random = new Random(42);
    private String groupId;
    private TestUtil U;

    @Before
    public void setup() throws RepositoryException {
        U = new TestUtil(context);
        groupId = "group_" + random.nextInt() + "_cg";
    }

    @After
    public void after() throws RepositoryException {
        U.cleanup(Collections.singletonList(groupId));
    }

    @Test
    public void createGroup() throws Exception {
        U.parseAndExecute("create group " + groupId);
        U.assertGroup("after creating group " + groupId, groupId, true);
    }

    @Test
    public void createDeleteSingleTest() throws Exception {
        U.assertGroup("at start of test", groupId, false);
        U.parseAndExecute("create group " + groupId);
        U.assertGroup("after creating group", groupId, true);
        U.parseAndExecute("delete group " + groupId);
        U.assertGroup("after deleting group", groupId, false);
    }

    @Test
    public void createGroupWithRelativePathTest() throws Exception {
        final String path = "testgroup/folder_for_" + groupId;
        U.parseAndExecute("create group " + groupId + " with path " + path);
        U.assertGroup("after creating group " + groupId, groupId, true, path);
    }

    @Test
    public void createGroupWithAbsolutePathTest() throws Exception {
        final String path = "/rep:security/rep:authorizables/rep:groups/testgroup/folder_for_" + groupId;
        U.parseAndExecute("create group " + groupId + " with path " + path);
        U.assertGroup("after creating group " + groupId, groupId, true, path);
    }

    @Test
    public void createGroupWithForcedRelativePathTest() throws Exception {
        final String path = "testgroup/folder_for_" + groupId;
        final String forcedPath = "testgroup/folder_for_" + groupId + "_forced";
        U.parseAndExecute("create group " + groupId + " with path " + path);
        U.assertGroup("after creating group " + groupId, groupId, true, path);
        U.parseAndExecute("create group " + groupId + " with forced path " + forcedPath);
        U.assertGroup("after creating group " + groupId, groupId, true, forcedPath);
    }

    @Test
    public void createGroupWithForcedAbsolutePathTest() throws Exception {
        final String path = "/rep:security/rep:authorizables/rep:groups/testgroup/folder_for_" + groupId;
        final String forcedPath =
                "/rep:security/rep:authorizables/rep:groups/testgroup/folder_for_" + groupId + "_forced";
        U.parseAndExecute("create group " + groupId + " with path " + path);
        U.assertGroup("after creating group " + groupId, groupId, true, path);
        U.parseAndExecute("create group " + groupId + " with forced path " + forcedPath);
        U.assertGroup("after creating group " + groupId, groupId, true, forcedPath);
    }

    @Test
    public void createGroupWithForcedPathNoClashTest() throws Exception {
        final String forcedPath =
                "/rep:security/rep:authorizables/rep:groups/testgroup/folder_for_" + groupId + "_forced";
        U.parseAndExecute("create group " + groupId + " with forced path " + forcedPath);
        U.assertGroup("after creating group " + groupId, groupId, true, forcedPath);
    }

    @Test
    public void createGroupMultipleTimes() throws Exception {
        U.assertGroup("before test", groupId, false);
        final String input = "create group " + groupId;
        for (int i = 0; i < 50; i++) {
            U.parseAndExecute(input);
        }
        U.assertGroup("after creating it multiple times", groupId, true);
    }

    @Test
    public void createGroupUserExistsTest() throws Exception {
        UserUtil.getUserManager(U.adminSession).createSystemUser(groupId, null);
        U.adminSession.save();
        // creating group with repoinit must fail
        try {
            U.parseAndExecute("create group " + groupId);
            fail("Group creation with conflicting user must fail.");
        } catch (RuntimeException e) {
            assertTrue(e.getCause() instanceof AuthorizableTypeException);
        }
    }

    @Test
    public void createGroupForcePathUserExistsTest() throws Exception {
        UserUtil.getUserManager(U.adminSession).createUser(groupId, null);
        U.adminSession.save();
        // creating group with repoinit must fail
        try {
            U.parseAndExecute("create group " + groupId
                    + " with forced path /rep:security/rep:authorizables/rep:users/intermediate/path");
            fail("Group creation with conflicting user must fail.");
        } catch (RuntimeException e) {
            assertTrue(e.getCause() instanceof AuthorizableTypeException);
        }
    }
}
