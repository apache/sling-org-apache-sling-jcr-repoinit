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

import javax.jcr.Repository;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.SimpleCredentials;
import javax.jcr.security.AccessControlPolicy;

import java.security.Principal;
import java.util.Arrays;
import java.util.Collection;

import org.apache.jackrabbit.api.JackrabbitRepository;
import org.apache.jackrabbit.api.security.JackrabbitAccessControlList;
import org.apache.jackrabbit.api.security.JackrabbitAccessControlManager;
import org.apache.jackrabbit.commons.jackrabbit.authorization.AccessControlUtils;
import org.apache.jackrabbit.oak.jcr.Jcr;
import org.apache.jackrabbit.oak.security.internal.SecurityProviderBuilder;
import org.apache.jackrabbit.oak.spi.security.ConfigurationParameters;
import org.apache.jackrabbit.oak.spi.security.SecurityProvider;
import org.apache.jackrabbit.oak.spi.security.authorization.AuthorizationConfiguration;
import org.apache.jackrabbit.oak.spi.security.principal.PrincipalImpl;
import org.apache.jackrabbit.oak.spi.security.user.UserConfiguration;
import org.apache.jackrabbit.oak.spi.security.user.UserConstants;
import org.apache.jackrabbit.oak.spi.xml.ImportBehavior;
import org.apache.jackrabbit.oak.spi.xml.ProtectedItemImporter;
import org.apache.sling.jcr.repoinit.impl.AclUtil;
import org.apache.sling.jcr.repoinit.impl.TestUtil;
import org.apache.sling.repoinit.parser.RepoInitParsingException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(Parameterized.class)
public class DeleteAclTest {

    @Parameterized.Parameters(name = "ImportBehavior={0}")
    public static Collection<Object[]> parameters() {
        return Arrays.asList(
                new Object[] {ImportBehavior.NAME_IGNORE},
                new Object[] {ImportBehavior.NAME_BESTEFFORT},
                new Object[] {ImportBehavior.NAME_ABORT});
    }

    private final String importBehaviorName;

    private Repository repository;
    private Session adminSession;
    private JackrabbitAccessControlManager acMgr;

    private TestUtil U;
    private String userA;

    public DeleteAclTest(String name) {
        this.importBehaviorName = name;
    }

    @Before
    public void setup() throws RepositoryException, RepoInitParsingException {
        SecurityProvider sp = createSecurityProvider();
        repository = new Jcr().with(sp).createRepository();

        String uid = sp.getParameters(UserConfiguration.NAME)
                .getConfigValue(UserConstants.PARAM_ADMIN_ID, UserConstants.DEFAULT_ADMIN_ID);
        adminSession = repository.login(new SimpleCredentials(uid, uid.toCharArray()), null);
        acMgr = AclUtil.getJACM(adminSession);
        U = new TestUtil(adminSession);

        userA = "userA_" + U.id;

        U.parseAndExecute("create service user " + U.username);
        U.parseAndExecute("create service user " + userA);
        U.parseAndExecute(""
                + "create path (nt:unstructured) /content\n"
                + "create path (nt:unstructured) /var\n"
                + "create path (nt:unstructured) /no/policy\n"
                + "set ACL for " + U.username + "\n"
                + "allow jcr:read on /content, /var, home(" + userA + ")\n"
                + "allow jcr:namespaceManagement on :repository\n"
                + "end\n"
                + "set ACL for " + userA + "\n"
                + "allow jcr:read on /content, /var\n"
                + "end\n");
    }

    private SecurityProvider createSecurityProvider() {
        return SecurityProviderBuilder.newBuilder()
                .with(ConfigurationParameters.of(
                        AuthorizationConfiguration.NAME,
                        ConfigurationParameters.of(ProtectedItemImporter.PARAM_IMPORT_BEHAVIOR, importBehaviorName)))
                .build();
    }

    @After
    public void after() throws Exception {
        try {
            adminSession.removeItem("/content");
            adminSession.removeItem("/var");
            adminSession.save();
            U.cleanupUser();
            U.cleanupServiceUser(userA);
        } finally {
            adminSession.logout();
            if (repository instanceof JackrabbitRepository) {
                ((JackrabbitRepository) repository).shutdown();
            }
        }
    }

    @Test
    public void testDeleteAcl() throws Exception {
        U.parseAndExecute("delete ACL on /content, :repository\n");

        assertArrayEquals(new AccessControlPolicy[0], acMgr.getPolicies("/content"));
        assertArrayEquals(new AccessControlPolicy[0], acMgr.getPolicies((String) null));
    }

    @Test
    public void testDeleteAclNonExistingPath() throws Exception {
        assertFalse(adminSession.nodeExists("/nonExisting"));
        U.parseAndExecute("delete ACL on /nonExisting\n");
        assertFalse(adminSession.nodeExists("/nonExisting"));
    }

    @Test
    public void testDeleteNonExistingAcl() throws Exception {
        assertTrue(adminSession.nodeExists("/no/policy"));
        assertArrayEquals(new AccessControlPolicy[0], acMgr.getPolicies("/no/policy"));

        U.parseAndExecute("delete ACL on /no/policy\n");

        assertTrue(adminSession.nodeExists("/no/policy"));
        assertArrayEquals(new AccessControlPolicy[0], acMgr.getPolicies("/no/policy"));
    }

    @Test
    public void testDeleteAclByPrincipal() throws Exception {
        U.parseAndExecute("delete ACL for " + userA + "\n");

        // removing resource-based ac setup by principal must leave entries for other principals intact
        for (String path : new String[] {"/content", "/var"}) {
            JackrabbitAccessControlList acl = AccessControlUtils.getAccessControlList(adminSession, path);
            assertNotNull(acl);
            assertEquals(1, acl.size()); // entry for U.username must not have been removed
            assertEquals(
                    U.username, acl.getAccessControlEntries()[0].getPrincipal().getName());
        }
    }

    @Test
    public void testDeleteAclByNonExistingPrincipal() throws Exception {
        Principal p = new PrincipalImpl("non-existing-service-user");
        assertEquals(0, acMgr.getPolicies(p).length);

        // execution for non-existing principal must not fail
        U.parseAndExecute("delete ACL for non-existing-service-user\n");

        assertEquals(0, acMgr.getPolicies(p).length);
    }

    @Test
    public void testDeletesUserAndAcl() throws Exception {
        Principal p = new PrincipalImpl(userA);
        assertEquals(1, acMgr.getPolicies(p).length);

        U.parseAndExecute("delete service user " + userA + "\n");
        assertEquals(1, acMgr.getPolicies(p).length);

        U.parseAndExecute("delete ACL for " + userA + "\n");
        assertEquals(0, acMgr.getPolicies(p).length);
    }
}
