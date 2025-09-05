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

import java.util.Random;

import org.apache.sling.jcr.repoinit.impl.TestUtil;
import org.apache.sling.repoinit.parser.RepoInitParsingException;
import org.apache.sling.testing.mock.sling.ResourceResolverType;
import org.apache.sling.testing.mock.sling.junit.SlingContext;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

/** Test the group membership */
public class GroupMembershipTest {

    @Rule
    public final SlingContext context = new SlingContext(ResourceResolverType.JCR_OAK);

    private static final Random random = new Random(42);
    private String grpNamePrefix;
    private String userNamePrefix;
    private String groupId;
    private String userId;
    private String secondUserId;
    private TestUtil U;

    @Before
    public void setup() throws RepositoryException, RepoInitParsingException {
        U = new TestUtil(context);
        grpNamePrefix = "group_" + random.nextInt();
        userNamePrefix = "user_" + random.nextInt();
        groupId = grpNamePrefix + "_cg";
        userId = userNamePrefix + "_cdst";
        secondUserId = userNamePrefix + "_cdst_2";
        U.parseAndExecute("create user " + userId);
        U.parseAndExecute("create user " + secondUserId);
        U.parseAndExecute("create group " + groupId);
    }

    @Test
    public void addMemberToGroup() throws Exception {
        U.parseAndExecute("add " + userId + " to group " + groupId);
        U.assertGroupMembership(userId, groupId, true);
    }

    @Test
    public void removeMemberFromGroup() throws Exception {
        U.parseAndExecute("add " + secondUserId + " to group " + groupId);
        U.assertGroupMembership(secondUserId, groupId, true);
        U.parseAndExecute("remove " + secondUserId + " from group " + groupId);
        U.assertGroupMembership(secondUserId, groupId, false);
    }

    @Test
    public void addNonExistingMemberToGroup() throws Exception {
        String nonExistingUserId = userNamePrefix + "_non";
        U.assertUser("User should not exist", nonExistingUserId, false);
        U.parseAndExecute("add " + nonExistingUserId + " to group " + groupId);
        U.assertGroupMembership(userId, groupId, false);
    }

    @Test
    public void cyclicMembership() throws Exception {
        U.parseAndExecute("add " + groupId + " to group " + groupId);
        U.assertGroupMembership(userId, groupId, false);
    }

    @Test
    public void addMemberToNonGroupAuthorizable() throws Exception {
        String otherUserId = userNamePrefix + "_abc";
        U.parseAndExecute("create user " + otherUserId);
        try {
            U.parseAndExecute("add " + userId + " to group " + otherUserId);
            Assert.fail();
        } catch (RuntimeException e) {
            Assert.assertEquals("expected runtime exception", otherUserId + " is not a group", e.getMessage());
        }
    }

    @Test
    public void addMemberToNonExistingGroup() throws Exception {
        String nonExistingGroupId = grpNamePrefix + "_non";
        U.assertGroup("Group should not exist", nonExistingGroupId, false);
        try {
            U.parseAndExecute("add " + userId + " to group " + nonExistingGroupId);
            Assert.fail();
        } catch (RuntimeException e) {
            Assert.assertEquals("expected runtime exception", nonExistingGroupId + " is not a group", e.getMessage());
        }
    }
}
