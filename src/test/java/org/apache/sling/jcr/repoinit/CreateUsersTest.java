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

import java.util.Collections;
import java.util.Random;

import javax.jcr.RepositoryException;

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

/** Test the creation and delete of users */
public class CreateUsersTest {

    @Rule
    public final SlingContext context = new SlingContext(ResourceResolverType.JCR_OAK);

    private static final Random random = new Random(42);
    private String namePrefix;
    private String userId;
    private TestUtil U;

    @Before
    public void setup() throws RepositoryException {
        U = new TestUtil(context);
        namePrefix = "user_" + random.nextInt();
        userId = namePrefix + "_testId";
    }

    @After
    public void after() throws RepositoryException {
        U.cleanup(Collections.singletonList(userId));
    }

    @Test
    public void createDeleteSingleTest() throws Exception {
        U.assertUser("at start of test", userId, false);
        U.parseAndExecute("create user " + userId);
        U.assertUser("after creating user", userId, true);
        U.parseAndExecute("delete user " + userId);
        U.assertUser("after deleting user", userId, false);
    }

    @Test
    public void createDeleteSingleWithPasswordTest() throws Exception {
        U.assertUser("at start of test", userId, false);
        U.parseAndExecute("create user " + userId + " with password mypw");
        U.assertUser("after creating user", userId, true);
        U.parseAndExecute("delete user " + userId);
        U.assertUser("after deleting user", userId, false);
    }

    @Test
    public void createUserWithRelativePathTest() throws Exception {
        final String path = "testusers/folder_for_" + userId;
        U.parseAndExecute("create user " + userId + " with path " + path);
        U.assertUser("after creating user " + userId, userId, true, path);
    }

    @Test
    public void createUserWithAbsolutePathTest() throws Exception {
        final String path = "/rep:security/rep:authorizables/rep:users/testusers/folder_for_" + userId;
        U.parseAndExecute("create user " + userId + " with path " + path);
        U.assertUser("after creating user " + userId, userId, true, path);
    }

    @Test
    public void createUserWithPathAndPasswordTest() throws Exception {
        final String path = "testuserwithpassword/folder_for_" + userId;
        U.parseAndExecute("create user " + userId + " with path " + path + " with password asdf");
        U.assertUser("after creating user " + userId, userId, true, path);
    }

    @Test
    public void createUserWithForcedRelativePathTest() throws Exception {
        final String path = "testusers/folder_for_" + userId;
        U.parseAndExecute("create user " + userId + " with path " + path);
        U.assertUser("after creating user " + userId, userId, true, path);
        final String forcedPath = "testusers/folder_for_" + userId + "_forced";
        U.parseAndExecute("create user " + userId + " with forced path " + forcedPath);
        U.assertUser("after creating user " + userId, userId, true, forcedPath);
    }

    @Test
    public void createUserWithForcedAbsolutePathTest() throws Exception {
        final String path = "/rep:security/rep:authorizables/rep:users/testusers/folder_for_" + userId;
        U.parseAndExecute("create user " + userId + " with path " + path);
        U.assertUser("after creating user " + userId, userId, true, path);
        final String forcedPath = "/rep:security/rep:authorizables/rep:users/testusers/folder_for_" + userId + "_forced";
        U.parseAndExecute("create user " + userId + " with forced path " + forcedPath);
        U.assertUser("after creating user " + userId, userId, true, forcedPath);
    }

    @Test
    public void createUserWithForcedPathNoClashTest() throws Exception {
        final String forcedPath = "/rep:security/rep:authorizables/rep:users/testusers/folder_for_" + userId + "_forced";
        U.parseAndExecute("create user " + userId + " with forced path " + forcedPath);
        U.assertUser("after creating user " + userId, userId, true, forcedPath);
    }

    @Test
    public void createUserWithForcedPathAndPasswordTest() throws Exception {
        final String path = "testuserwithpassword/folder_for_" + userId;
        U.parseAndExecute("create user " + userId + " with path " + path + " with password asdf");
        U.assertUser("after creating user " + userId, userId, true, path);
        final String forcedPath = "testuserwithpassword/folder_for_" + userId + "_forced";
        U.parseAndExecute("create user " + userId + " with forced path " + forcedPath + " with password asdf");
        U.assertUser("after creating user " + userId, userId, true, forcedPath);
    }

    private String user(int index) {
        return namePrefix + "_" + index;
    }

    @Test
    public void createUserMultipleTimes() throws Exception {
        U.assertUser("before test", userId, false);
        final String input = "create user " + userId;
        for(int i=0; i < 50; i++) {
            U.parseAndExecute(input);
        }
        U.assertUser("after creating it multiple times", userId, true);
    }

    @Test
    public void createDeleteMultipleTest() throws Exception {
        final int n = 50;

        {
            final StringBuilder input = new StringBuilder();
            for(int i=0; i < n; i++) {
                U.assertUser("at start of test", user(i), false);
                input.append("create user ").append(user(i)).append("\n");
            }
            U.parseAndExecute(input.toString());
        }

        {
            final StringBuilder input = new StringBuilder();
            for(int i=0; i < n; i++) {
                U.assertUser("before deleting user", user(i), true);
                input.append("delete user ").append(user(i)).append("\n");
            }
            U.parseAndExecute(input.toString());
        }


        for(int i=0; i < n; i++) {
            U.assertUser("after deleting users", user(i), false);
        }
    }

    @Test
    public void createUserGroupExistsTest() throws Exception {
        UserUtil.getUserManager(U.adminSession).createGroup(userId);
        U.adminSession.save();
        // creating service user with repoinit must fail
        try {
            U.parseAndExecute("create user " + userId);
            fail("User creation with conflicting group must fail.");
        } catch (RuntimeException e) {
            assertTrue(e.getCause() instanceof AuthorizableTypeException);
        }
    }

    @Test
    public void createUserForcePathGroupExistsTest() throws Exception {
        UserUtil.getUserManager(U.adminSession).createGroup(userId);
        U.adminSession.save();
        // creating service user with repoinit must fail
        try {
            U.parseAndExecute("create user " + userId + " with forced path /rep:security/rep:authorizables/rep:users/intermediate/path");
            fail("User creation with conflicting group must fail.");
        } catch (RuntimeException e) {
            assertTrue(e.getCause() instanceof AuthorizableTypeException);
        }
    }

    @Test(expected = RuntimeException.class)
    public void createUserSystemUserExists() throws Exception {
        UserUtil.getUserManager(U.adminSession).createSystemUser(userId, null);
        U.adminSession.save();

        U.parseAndExecute("create user " + userId + " with forced path intermediate/relpath");
    }
}
