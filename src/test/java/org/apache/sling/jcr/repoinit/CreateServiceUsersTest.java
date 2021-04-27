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

import java.util.ArrayList;
import java.util.List;
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

/** Test the creation and delete of service users */
public class CreateServiceUsersTest {
    
    @Rule
    public final SlingContext context = new SlingContext(ResourceResolverType.JCR_OAK);
    
    private static final Random random = new Random(42);

    private String namePrefix;
    private String userId;
    private TestUtil U;

    private final List<String> toRemove = new ArrayList();
    
    @Before
    public void setup() throws RepositoryException {
        U = new TestUtil(context);
        namePrefix = "user_" + random.nextInt();
        userId = namePrefix + "_cdst";
        toRemove.add(userId);
    }

    @After
    public void after() throws RepositoryException {
        U.cleanup(toRemove);
    }

    @Test
    public void createDeleteSingleTest() throws Exception {
        U.assertServiceUser("at start of test", userId, false);
        U.parseAndExecute("create service user " + userId);
        U.assertServiceUser("after creating user", userId, true);
        U.parseAndExecute("delete service user " + userId);
        U.assertServiceUser("after deleting user", userId, false);
    }

    @Test
    public void disableTest() throws Exception {
        U.assertServiceUser("at start of test", userId, false);
        U.parseAndExecute("create service user " + userId);
        U.assertServiceUser("after creating user", userId, true);
        U.assertEnabledUser("after creating user", userId);

        final String disabledReason = "disabled-" + random.nextInt();
        U.parseAndExecute("disable service user " + userId + " : \"" + disabledReason + "\"");
        U.assertServiceUser("after disable user", userId, true);
        U.assertDisabledUser("after disable user", userId, disabledReason);
    }

    @Test
    public void disableRegularUserTest() throws Exception {
        U.assertServiceUser("at start of test", userId, false);
        U.parseAndExecute("create user " + userId);
        U.assertEnabledUser("after creating user", userId);

        final String disabledReason = "disabled-" + random.nextInt();
        U.parseAndExecute("disable user " + userId + " : \"" + disabledReason + "\"");
        U.assertDisabledUser("after disable user", userId, disabledReason);
    }

    @Test
    public void disableDoesntCheckUserTypeOnServiceUserTest() throws Exception {
        U.assertServiceUser("at start of test", userId, false);
        U.parseAndExecute("create service user " + userId);
        U.assertServiceUser("after creating user", userId, true);
        U.assertEnabledUser("after creating user", userId);

        final String disabledReason = "disabled-" + random.nextInt();
        U.parseAndExecute("disable user " + userId + " : \"" + disabledReason + "\"");
        U.assertServiceUser("after creating user", userId, true);
        U.assertDisabledUser("after disable user", userId, disabledReason);
    }

    @Test
    public void disableDoesntCheckUserTypeOnRegularUserTest() throws Exception {
        U.assertServiceUser("at start of test", userId, false);
        U.parseAndExecute("create user " + userId);
        U.assertEnabledUser("after creating user", userId);

        final String disabledReason = "disabled-" + random.nextInt();
        U.parseAndExecute("disable service user " + userId + " : \"" + disabledReason + "\"");
        U.assertDisabledUser("after disable user", userId, disabledReason);
    }

    @Test
    public void deleteNonExistingUserTest() throws Exception {
        U.assertServiceUser("at start of test", userId, false);
        U.parseAndExecute("delete service user " + userId);
        U.assertServiceUser("after deleting user", userId, false);
    }

    @Test
    public void disableNonExistingUserTest() throws Exception {
        U.assertServiceUser("at start of test", userId, false);
        U.parseAndExecute("disable service user " + userId + " : \"Test\"");
        U.assertServiceUser("after disable service user", userId, false);
        U.parseAndExecute("disable user " + userId + " : \"Test\"");
        U.assertServiceUser("after disable regular user", userId, false);
    }
    
    private String user(int index) {
        return namePrefix + "_" + index;
    }
    
    @Test
    public void createUserMultipleTimes() throws Exception {
        U.assertServiceUser("before test", userId, false);
        final String input = "create service user " + userId;
        for(int i=0; i < 50; i++) {
            U.parseAndExecute(input);
        }
        U.assertServiceUser("after creating it multiple times", userId, true);
    }
    
    @Test
    public void createDeleteMultipleTest() throws Exception {
        final int n = 50;
        
        {
            final StringBuilder input = new StringBuilder();
            for(int i=0; i < n; i++) {
                U.assertServiceUser("at start of test", user(i), false);
                input.append("create service user ").append(user(i)).append("\n");
            }
            U.parseAndExecute(input.toString());
        }
        
        {
            final StringBuilder input = new StringBuilder();
            for(int i=0; i < n; i++) {
                U.assertServiceUser("before deleting user", user(i), true);
                input.append("delete service user ").append(user(i)).append("\n");
            }
            U.parseAndExecute(input.toString());
        }
        

        for(int i=0; i < n; i++) {
            U.assertServiceUser("after deleting users", user(i), false);
        }
    }

    @Test
    public void createServiceUserWithRelativePathTest() throws Exception {
        // Oak requires system/ prefix for service users
        final String path = "system/forServiceUser/test";
        U.assertServiceUser("at start of test", userId, false);
        U.parseAndExecute("create service user " + userId + " with path " + path);
        U.assertServiceUser("after creating user", userId, true, path);
    }

    @Test
    public void createServiceUserWithForcedRelativePathTest() throws Exception {
        // Oak requires system/ prefix for service users
        final String path = "system/forServiceUser/test";
        final String pathForced = "system/forServiceUser/test2";
        U.assertServiceUser("at start of test", userId, false);
        U.parseAndExecute("create service user " + userId + " with path " + path);
        U.assertServiceUser("after creating user", userId, true, path);

        U.parseAndExecute("create service user " + userId + " with forced path " + pathForced);
        U.assertServiceUser("after forcing creating user", userId, true, pathForced);
    }

    @Test
    public void createServiceUserWithAbsPathTest() throws Exception {
        final String path = "/rep:security/rep:authorizables/rep:users/system/forServiceUser/test";
        U.assertServiceUser("at start of test", userId, false);
        U.parseAndExecute("create service user " + userId + " with path " + path);
        U.assertServiceUser("after creating user", userId, true, path);
    }

    @Test
    public void createServiceUserWithForcedAbsPathTest() throws Exception {
        final String path = "/rep:security/rep:authorizables/rep:users/system/forServiceUser/test1";
        final String pathForced = "/rep:security/rep:authorizables/rep:users/system/forServiceUser/test2";
        U.assertServiceUser("at start of test", userId, false);
        U.parseAndExecute("create service user " + userId + " with path " + path);
        U.assertServiceUser("after creating user", userId, true, path);
        U.parseAndExecute("create service user " + userId + " with forced path " + pathForced);
        U.assertServiceUser("after forcing creating user", userId, true, pathForced);
    }

    @Test
    public void createServiceUserWithForcedPathNoClashTest() throws Exception {
        final String pathForced = "/rep:security/rep:authorizables/rep:users/system/forServiceUser/test2";
        U.assertServiceUser("at start of test", userId, false);
        U.parseAndExecute("create service user " + userId + " with forced path " + pathForced);
        U.assertServiceUser("after forcing creating user", userId, true, pathForced);
    }

    @Test
    public void createServiceUserGroupExistsTest() throws Exception {
        UserUtil.getUserManager(U.adminSession).createGroup(userId);
        U.adminSession.save();
        // creating service user with repoinit must fail
        try {
            U.parseAndExecute("create service user " + userId);
            fail("Service user creating with conflicting group must fail.");
        } catch (RuntimeException e) {
            assertTrue(e.getCause() instanceof AuthorizableTypeException);
        }
    }

    @Test
    public void createServiceUserForcePathGroupExistsTest() throws Exception {
        UserUtil.getUserManager(U.adminSession).createGroup(userId);
        U.adminSession.save();
        // creating service user with repoinit must fail
        try {
            U.parseAndExecute("create service user " + userId + " with forced path /rep:security/rep:authorizables/rep:users/system");
            fail("Service user creating with conflicting group must fail.");
        } catch (RuntimeException e) {
            assertTrue(e.getCause() instanceof AuthorizableTypeException);
        }
    }

    @Test(expected = RuntimeException.class)
    public void createServiceUserRegularUserExists() throws Exception {
        UserUtil.getUserManager(U.adminSession).createUser(userId, null);
        U.adminSession.save();

        U.parseAndExecute("create service user " + userId + " with forced path system/test");
    }
}
