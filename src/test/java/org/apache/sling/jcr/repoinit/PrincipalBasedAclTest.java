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

import javax.jcr.Node;
import javax.jcr.Property;
import javax.jcr.Repository;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.SimpleCredentials;
import javax.jcr.security.AccessControlManager;
import javax.jcr.security.AccessControlPolicy;
import javax.jcr.security.Privilege;
import javax.security.auth.Subject;

import java.security.Principal;
import java.security.PrivilegedExceptionAction;
import java.util.Collections;
import java.util.UUID;

import org.apache.jackrabbit.api.JackrabbitRepository;
import org.apache.jackrabbit.api.JackrabbitSession;
import org.apache.jackrabbit.api.security.JackrabbitAccessControlManager;
import org.apache.jackrabbit.api.security.authorization.PrincipalAccessControlList;
import org.apache.jackrabbit.api.security.user.Authorizable;
import org.apache.jackrabbit.api.security.user.User;
import org.apache.jackrabbit.api.security.user.UserManager;
import org.apache.jackrabbit.commons.jackrabbit.authorization.AccessControlUtils;
import org.apache.jackrabbit.oak.commons.PathUtils;
import org.apache.jackrabbit.oak.jcr.Jcr;
import org.apache.jackrabbit.oak.security.internal.SecurityProviderBuilder;
import org.apache.jackrabbit.oak.security.internal.SecurityProviderHelper;
import org.apache.jackrabbit.oak.spi.mount.Mounts;
import org.apache.jackrabbit.oak.spi.security.ConfigurationParameters;
import org.apache.jackrabbit.oak.spi.security.SecurityProvider;
import org.apache.jackrabbit.oak.spi.security.authorization.AuthorizationConfiguration;
import org.apache.jackrabbit.oak.spi.security.authorization.permission.Permissions;
import org.apache.jackrabbit.oak.spi.security.authorization.principalbased.impl.FilterProviderImpl;
import org.apache.jackrabbit.oak.spi.security.authorization.principalbased.impl.PrincipalBasedAuthorizationConfiguration;
import org.apache.jackrabbit.oak.spi.security.principal.SystemUserPrincipal;
import org.apache.jackrabbit.oak.spi.security.user.UserConfiguration;
import org.apache.jackrabbit.oak.spi.security.user.UserConstants;
import org.apache.sling.jcr.repoinit.impl.AclUtil;
import org.apache.sling.jcr.repoinit.impl.CachingSessionWrapper;
import org.apache.sling.jcr.repoinit.impl.RepoInitException;
import org.apache.sling.jcr.repoinit.impl.TestUtil;
import org.apache.sling.repoinit.parser.RepoInitParsingException;
import org.apache.sling.repoinit.parser.operations.AclLine;
import org.apache.sling.testing.mock.osgi.junit.OsgiContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.Test.None;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class PrincipalBasedAclTest {

    private static final String PRINCIPAL_BASED_SUBTREE = "principalbased";
    private static final String REMOVE_NOT_SUPPORTED_REGEX = ".*REMOVE[a-zA-Z ]+not supported.*";
    private static final String NO_PRINCIPAL_CONTROL_LIST_AVAILABLE = ".*No PrincipalAccessControlList available.*";

    @Rule
    public final OsgiContext context = new OsgiContext();

    private Repository repository;
    private JackrabbitSession adminSession;
    private JackrabbitAccessControlManager acMgr;

    private TestUtil U;

    private String path;
    private String propPath;
    private String relPath;

    private Session testSession;

    @Before
    public void before() throws Exception {
        SecurityProvider sp = createSecurityProvider();
        repository = new Jcr().with(sp).createRepository();

        String uid = sp.getParameters(UserConfiguration.NAME)
                .getConfigValue(UserConstants.PARAM_ADMIN_ID, UserConstants.DEFAULT_ADMIN_ID);
        adminSession = (JackrabbitSession) repository.login(new SimpleCredentials(uid, uid.toCharArray()), null);
        acMgr = AclUtil.getJACM(adminSession);
        U = new TestUtil(adminSession);

        Node tmp = adminSession.getRootNode().addNode("tmp_" + U.id);
        Property prop = tmp.setProperty("prop", "value");
        path = tmp.getPath();
        propPath = prop.getPath();
        adminSession.save();

        relPath = sp.getParameters(UserConfiguration.NAME)
                        .getConfigValue(
                                UserConstants.PARAM_SYSTEM_RELATIVE_PATH, UserConstants.DEFAULT_SYSTEM_RELATIVE_PATH)
                + "/" + PRINCIPAL_BASED_SUBTREE;
        U.parseAndExecute("create service user " + U.username + " with path " + relPath);

        testSession = loginSystemUserPrincipal(U.username);

        assertPermission(testSession, PathUtils.ROOT_PATH, Session.ACTION_READ, false);
        assertPermission(testSession, path, Session.ACTION_READ, false);
    }

    @After
    public void after() throws Exception {
        try {
            adminSession.removeItem(path);
            adminSession.save();
            U.cleanupUser();
        } finally {
            adminSession.logout();
            testSession.logout();
            if (repository instanceof JackrabbitRepository) {
                ((JackrabbitRepository) repository).shutdown();
            }
        }
    }

    private SecurityProvider createSecurityProvider() {
        /*
         * use composition-type OR in order in order to simplify the permission setup. since the tests in this class
         * don't setup permissions using the default authorization, the OR-setup will be sufficient to check for permissions
         * granted with the principal-based module only.
         *
         * in an AND setup scenario one would need to inject the aggregation filter defined by oak-authorization-princialbased
         */
        SecurityProvider sp = SecurityProviderBuilder.newBuilder()
                .with(ConfigurationParameters.of("authorizationCompositionType", "OR"))
                .build();

        ConfigurationParameters userParams = sp.getParameters(UserConfiguration.NAME);
        String userRoot = userParams.getConfigValue(UserConstants.PARAM_USER_PATH, UserConstants.DEFAULT_USER_PATH);
        String systemRelPath = userParams.getConfigValue(
                UserConstants.PARAM_SYSTEM_RELATIVE_PATH, UserConstants.DEFAULT_SYSTEM_RELATIVE_PATH);

        FilterProviderImpl fp = new FilterProviderImpl();
        context.registerInjectActivateService(
                fp,
                Collections.singletonMap("path", PathUtils.concat(userRoot, systemRelPath, PRINCIPAL_BASED_SUBTREE)));

        PrincipalBasedAuthorizationConfiguration authorizationConfig = new PrincipalBasedAuthorizationConfiguration();
        authorizationConfig.bindMountInfoProvider(Mounts.defaultMountInfoProvider());
        authorizationConfig.bindFilterProvider(fp);
        SecurityProviderHelper.updateConfig(sp, authorizationConfig, AuthorizationConfiguration.class);
        return sp;
    }

    /**
     * Create a JCR Session for the given system principal that is based on a Subject that only contains SystemUserPrincipal(s).
     * The result from U.loginService with result in a Session that additionally contains group-membership, which is not
     * supported by the default FilterProvider implementation (i.e. access control setup and permission evaluation is not supported
     * by the PrincipalBasedAuthorizationConfiguration as needed for these tests).
     *
     * @param systemUserPrincipalName Name of a system user principal
     * @return A JCR Session
     * @throws Exception If the repository login fails.
     */
    private Session loginSystemUserPrincipal(final String systemUserPrincipalName) throws Exception {
        SystemUserPrincipal principal = () -> systemUserPrincipalName;
        Subject subject =
                new Subject(true, Collections.singleton(principal), Collections.emptySet(), Collections.emptySet());
        return Subject.doAs(subject, (PrivilegedExceptionAction<Session>) () -> repository.login(null, null));
    }

    private static void assertPermission(
            Session userSession, String absolutePath, String actions, boolean successExpected)
            throws RepositoryException {
        assertEquals(
                "Expecting " + actions + " for path " + absolutePath + " to be "
                        + (successExpected ? "granted" : "denied"),
                successExpected,
                userSession.hasPermission(absolutePath, actions));
    }

    private static void assertRegex(String regex, String shouldMatch) {
        assertTrue("Expecting '" + shouldMatch + "'' to match " + regex, shouldMatch.matches(regex));
    }

    @NotNull
    private static PrincipalAccessControlList assertPolicy(
            @NotNull Principal principal, @NotNull Session session, int expectedSize) throws RepositoryException {
        PrincipalAccessControlList acl = getAcl(principal, session);
        assertNotNull(acl);
        assertEquals(expectedSize, acl.size());
        return acl;
    }

    private Authorizable getServiceUser(@NotNull String uid) throws RepositoryException {
        UserManager uMgr = adminSession.getUserManager();
        Authorizable a = uMgr.getAuthorizable(uid);
        if (a != null) {
            return a;
        } else {
            throw new RepositoryException("Expected service user " + uid + " to exist.");
        }
    }

    private Principal getPrincipal(@NotNull String serviceUserId) throws RepositoryException {
        return getServiceUser(serviceUserId).getPrincipal();
    }

    @Test
    public void readGranted() throws Exception {
        String setup = "set principal ACL for " + U.username + "\n" + "allow jcr:read on " + path + "\n" + "end";
        U.parseAndExecute(setup);
        testSession.refresh(false);

        assertPermission(testSession, path, Session.ACTION_READ, true);
        assertPermission(testSession, propPath, Session.ACTION_READ, true);
        assertPermission(testSession, propPath, Session.ACTION_SET_PROPERTY, false);
    }

    @Test
    public void grantedNonExistingPath() throws Exception {
        String nonExistingPath = path + "/nonExisting";
        String setup =
                "set principal ACL for " + U.username + "\n" + "allow jcr:read on " + nonExistingPath + "\n" + "end";
        U.parseAndExecute(setup);
        testSession.refresh(false);

        assertPermission(testSession, nonExistingPath, Session.ACTION_READ, true);
    }

    @Test
    public void grantAtRootPath() throws Exception {
        String setup = "set principal ACL for " + U.username + "\n" + "allow jcr:all on /\n" + "end";

        U.parseAndExecute(setup);
        testSession.refresh(false);

        assertPermission(
                testSession,
                "/newChild",
                Session.ACTION_READ + "," + Session.ACTION_ADD_NODE + "," + Session.ACTION_REMOVE,
                true);
    }

    @Test
    public void multiplePrivileges() throws Exception {
        String setup = "set principal ACL for " + U.username + "\n"
                + "allow jcr:read, jcr:versionManagement on " + path + "\n"
                + "end";
        U.parseAndExecute(setup);
        testSession.refresh(false);

        String permissions = Permissions.getString(Permissions.READ | Permissions.VERSION_MANAGEMENT);
        assertPermission(testSession, path, permissions, true);
        assertPermission(testSession, path + "/newchild", permissions, true);
    }

    @Test(expected = RepoInitException.class)
    public void invalidPrivilege() throws Exception {
        String setup =
                "set principal ACL for " + U.username + "\n" + "allow my:invalidPrivilege on " + path + "\n" + "end";
        U.parseAndExecute(setup);
    }

    @Test(expected = RepoInitException.class)
    public void denyEntry() throws Exception {
        String setup = "set principal ACL for " + U.username + "\n" + "deny jcr:read on " + path + "\n" + "end";
        U.parseAndExecute(setup);
    }

    @Test
    public void repoLevelPermission() throws Exception {
        String setup =
                "set principal ACL for " + U.username + "\n" + "allow jcr:namespaceManagement on :repository\n" + "end";
        U.parseAndExecute(setup);
        testSession.refresh(false);

        assertTrue(testSession
                .getAccessControlManager()
                .hasPrivileges(null, AccessControlUtils.privilegesFromNames(testSession, "jcr:namespaceManagement")));
    }

    @Test
    public void repoLevelAndPath() throws Exception {
        String setup =
                "set principal ACL for " + U.username + "\n" + "allow jcr:all on :repository, " + path + "\n" + "end";
        U.parseAndExecute(setup);
        testSession.refresh(false);

        AccessControlManager acMgr = testSession.getAccessControlManager();
        assertTrue(acMgr.hasPrivileges(path, AccessControlUtils.privilegesFromNames(testSession, "jcr:all")));
        assertTrue(acMgr.hasPrivileges(null, AccessControlUtils.privilegesFromNames(testSession, "jcr:all")));
    }

    @Test
    public void globRestriction() throws Exception {
        String notAllowed = "/testxyz_" + U.id;
        String allowed = "/testabc_" + U.id;

        String setup = "set principal ACL for " + U.username + "\n"
                + "allow jcr:read on " + path + " restriction(rep:glob,*abc*)\n"
                + "end";

        U.parseAndExecute(setup);
        testSession.refresh(false);

        assertPermission(testSession, path + allowed, Session.ACTION_READ, true);
        assertPermission(testSession, path + notAllowed, Session.ACTION_READ, false);
    }

    @Test
    public void emptyGlobRestriction() throws Exception {
        String setup = "set principal ACL for " + U.username + "\n"
                + "allow jcr:read on " + path + " restriction (rep:glob)\n"
                + "end";

        U.parseAndExecute(setup);
        testSession.refresh(false);

        assertPermission(testSession, "/", Session.ACTION_READ, false);
        assertPermission(testSession, path, Session.ACTION_READ, true);
        assertPermission(testSession, propPath, Session.ACTION_READ, false);
        assertPermission(testSession, "/path/child", Session.ACTION_READ, false);
    }

    @Test
    public void mvItemNamesRestriction() throws Exception {
        String setup = "set principal ACL for " + U.username + "\n"
                + "allow jcr:modifyProperties on / restriction(rep:itemNames,propName,prop)\n"
                + "end";

        U.parseAndExecute(setup);
        testSession.refresh(false);

        assertPermission(testSession, propPath, Session.ACTION_SET_PROPERTY, true);
        assertPermission(testSession, path + "/propName", Session.ACTION_SET_PROPERTY, true);
    }

    @Test
    public void emptyMvRestrictionTest() throws Exception {
        String setup = "set principal ACL for " + U.username + "\n"
                + "allow jcr:read on " + path + " restriction(rep:ntNames)\n"
                + "end";
        U.parseAndExecute(setup);
        testSession.refresh(false);

        assertPermission(testSession, propPath, Session.ACTION_READ, false);
    }

    @Test(expected = RepoInitException.class)
    public void unsupportedRestriction() throws Exception {
        String setup = "set principal ACL for " + U.username + "\n"
                + "allow jcr:read on " + path + " restriction(jcr:unsupported,value)\n"
                + "end";
        U.parseAndExecute(setup);
    }

    @Test
    public void multiplePaths() throws Exception {
        String setup =
                "set principal ACL for " + U.username + "\n" + "allow jcr:read on " + path + ", /content\n" + "end";

        U.parseAndExecute(setup);
        testSession.refresh(false);

        assertPermission(testSession, PathUtils.ROOT_PATH, Session.ACTION_READ, false);
        assertPermission(testSession, path, Session.ACTION_READ, true);
        assertPermission(testSession, "/content", Session.ACTION_READ, true);
    }

    @Test(expected = RepoInitParsingException.class)
    public void missingPath() throws Exception {
        String setup = "set principal ACL for " + U.username + "\n" + "allow jcr:read on \n" + "end";

        U.parseAndExecute(setup);
    }

    @Test
    public void multiplePrincipals() throws Exception {
        Session s = null;
        try {
            U.parseAndExecute("create service user otherSystemPrincipal with path " + relPath);
            String setup = "set principal ACL for " + U.username + ",otherSystemPrincipal \n"
                    + "allow jcr:read on " + path + "\n"
                    + "end";
            U.parseAndExecute(setup);
            testSession.refresh(false);

            assertPermission(testSession, propPath, Session.ACTION_READ, true);
            s = loginSystemUserPrincipal("otherSystemPrincipal");
            assertPermission(s, propPath, Session.ACTION_READ, true);
        } finally {
            if (s != null) {
                s.logout();
            }
            U.cleanupServiceUser("otherSystemPrincipal");
        }
    }

    @Test(expected = RepoInitParsingException.class)
    public void missingPrincipal() throws Exception {
        String setup = "set principal ACL for \n" + "allow jcr:read on " + path + "\n" + "end";

        U.parseAndExecute(setup);
    }

    @Test
    public void redundantEntry() throws Exception {
        String setup = "set principal ACL for " + U.username + "\n"
                + "allow jcr:read on " + path + "\n"
                + "allow jcr:read on " + path + "\n"
                + "end";
        U.parseAndExecute(setup);

        Principal principal =
                adminSession.getUserManager().getAuthorizable(U.username).getPrincipal();
        JackrabbitAccessControlManager acMgr = (JackrabbitAccessControlManager) adminSession.getAccessControlManager();
        PrincipalAccessControlList pacl = null;
        for (AccessControlPolicy policy : acMgr.getPolicies(principal)) {
            if (policy instanceof PrincipalAccessControlList) {
                pacl = (PrincipalAccessControlList) policy;
                break;
            }
        }
        assertNotNull(pacl);
        // an identical entry should only be present once
        assertEquals(1, pacl.size());
    }

    @Test
    public void grantWithSecondSetup() throws Exception {
        String setup = "set principal ACL for " + U.username + "\n" + "allow jcr:read on " + path + "\n" + "end";
        U.parseAndExecute(setup);

        setup = "set principal ACL for " + U.username + "\n" + "allow jcr:write on " + path + "\n" + "end";
        U.parseAndExecute(setup);

        Principal principal =
                adminSession.getUserManager().getAuthorizable(U.username).getPrincipal();
        JackrabbitAccessControlManager acMgr = (JackrabbitAccessControlManager) adminSession.getAccessControlManager();
        PrincipalAccessControlList pacl = null;
        for (AccessControlPolicy policy : acMgr.getPolicies(principal)) {
            if (policy instanceof PrincipalAccessControlList) {
                pacl = (PrincipalAccessControlList) policy;
                break;
            }
        }
        assertNotNull(pacl);
        assertEquals(2, pacl.size());
    }

    @Test(expected = None.class)
    public void principalAclNotAvailable() throws Exception {
        try {
            // create service user outside of supported tree for principal-based access control
            U.parseAndExecute("create service user otherSystemPrincipal");
            String setup = "set principal ACL for otherSystemPrincipal \n" + "allow jcr:read on " + path + "\n" + "end";
            U.parseAndExecute(setup);
        } finally {
            U.cleanupServiceUser("otherSystemPrincipal");
        }
    }

    @Test(expected = None.class)
    public void principalAclNotAvailableStrict() throws Exception {
        try {
            // create service user outside of supported tree for principal-based access control
            U.parseAndExecute("create service user otherSystemPrincipal");
            // principal-based ac-setup must fail as service user is not located below supported path
            String setup =
                    "ensure principal ACL for otherSystemPrincipal \n" + "allow jcr:read on " + path + "\n" + "end";
            try {
                U.parseAndExecute(setup);
                fail("Setting a principal ACL outside a supported path must not succeed");
            } catch (RuntimeException e) {
                assertRegex(NO_PRINCIPAL_CONTROL_LIST_AVAILABLE, e.getMessage());
            }
        } finally {
            U.cleanupServiceUser("otherSystemPrincipal");
        }
    }

    @Test
    public void principalAclNotAvailableRestrictionMismatch() throws Exception {
        JackrabbitAccessControlManager acMgr = (JackrabbitAccessControlManager) adminSession.getAccessControlManager();
        try {
            // create service user outside of supported tree for principal-based access control
            U.parseAndExecute("create service user otherSystemPrincipal");
            // setup path-based access control to establish effective permission setup
            String setup = "set ACL for otherSystemPrincipal \n" + "allow jcr:read on " + path + "\n" + "end";
            U.parseAndExecute(setup);

            Principal principal = adminSession
                    .getUserManager()
                    .getAuthorizable("otherSystemPrincipal")
                    .getPrincipal();
            assertTrue(acMgr.hasPrivileges(
                    path,
                    Collections.singleton(principal),
                    AccessControlUtils.privilegesFromNames(adminSession, Privilege.JCR_READ)));

            setup = "set principal ACL for otherSystemPrincipal \n"
                    + "allow jcr:read on " + path + " restriction(rep:glob,*mismatch)\n"
                    + "end";
            U.parseAndExecute(setup);
        } finally {
            U.cleanupServiceUser("otherSystemPrincipal");
        }
    }

    @Test
    public void principalAclNotAvailableRestrictionMismatchStrict() throws Exception {
        JackrabbitAccessControlManager acMgr = (JackrabbitAccessControlManager) adminSession.getAccessControlManager();
        try {
            // create service user outside of supported tree for principal-based access control
            U.parseAndExecute("create service user otherSystemPrincipal");
            // setup path-based access control to establish effective permission setup
            String setup = "set ACL for otherSystemPrincipal \n" + "allow jcr:read on " + path + "\n" + "end";
            U.parseAndExecute(setup);

            Principal principal = adminSession
                    .getUserManager()
                    .getAuthorizable("otherSystemPrincipal")
                    .getPrincipal();
            assertTrue(acMgr.hasPrivileges(
                    path,
                    Collections.singleton(principal),
                    AccessControlUtils.privilegesFromNames(adminSession, Privilege.JCR_READ)));

            // setting up principal-acl will not succeed (principal not located below supported path)
            // since effective entry doesn't match the restriction -> setup must fail
            setup = "ensure principal ACL for otherSystemPrincipal \n"
                    + "allow jcr:read on " + path + " restriction(rep:glob,*mismatch)\n"
                    + "end";
            try {
                U.parseAndExecute(setup);
                fail("Setting a principal ACL outside a supported path must not succeed");
            } catch (RuntimeException e) {
                assertRegex(NO_PRINCIPAL_CONTROL_LIST_AVAILABLE, e.getMessage());
            }
        } finally {
            U.cleanupServiceUser("otherSystemPrincipal");
        }
    }

    @Test
    public void principalAclNotAvailableEntryPresent() throws Exception {
        JackrabbitAccessControlManager acMgr = (JackrabbitAccessControlManager) adminSession.getAccessControlManager();
        try {
            // create service user outside of supported tree for principal-based access control
            U.parseAndExecute("create service user otherSystemPrincipal");
            // setup path-based access control to establish effective permission setup
            String setup = "set ACL for otherSystemPrincipal \n" + "allow jcr:read on " + path + "\n" + "end";
            U.parseAndExecute(setup);

            Principal principal = adminSession
                    .getUserManager()
                    .getAuthorizable("otherSystemPrincipal")
                    .getPrincipal();
            assertTrue(acMgr.hasPrivileges(
                    path,
                    Collections.singleton(principal),
                    AccessControlUtils.privilegesFromNames(adminSession, Privilege.JCR_READ)));

            // setting up principal-acl will not succeed (principal not located below supported path)
            // but there exists an effective entry with the same definition -> no exception
            setup = "set principal ACL for otherSystemPrincipal \n" + "allow jcr:read on " + path + "\n" + "end";
            U.parseAndExecute(setup);

            for (AccessControlPolicy policy : acMgr.getPolicies(principal)) {
                assertFalse(policy instanceof PrincipalAccessControlList);
            }
        } finally {
            U.cleanupServiceUser("otherSystemPrincipal");
        }
    }

    @Test
    public void principalAclNotAvailableEntryPresentStrict() throws Exception {
        JackrabbitAccessControlManager acMgr = (JackrabbitAccessControlManager) adminSession.getAccessControlManager();
        try {
            // create service user outside of supported tree for principal-based access control
            U.parseAndExecute("create service user otherSystemPrincipal");
            // setup path-based access control to establish effective permission setup
            String setup = "set ACL for otherSystemPrincipal \n" + "allow jcr:read on " + path + "\n" + "end";
            U.parseAndExecute(setup);

            Principal principal = adminSession
                    .getUserManager()
                    .getAuthorizable("otherSystemPrincipal")
                    .getPrincipal();
            assertTrue(acMgr.hasPrivileges(
                    path,
                    Collections.singleton(principal),
                    AccessControlUtils.privilegesFromNames(adminSession, Privilege.JCR_READ)));

            // setting up principal-acl will not succeed (principal not located below supported path)
            setup = "ensure principal ACL for otherSystemPrincipal \n" + "allow jcr:read on " + path + "\n" + "end";
            try {
                U.parseAndExecute(setup);
                fail("Setting a principal ACL outside a supported path must not succeed");
            } catch (RuntimeException e) {
                assertRegex(NO_PRINCIPAL_CONTROL_LIST_AVAILABLE, e.getMessage());
            }
        } finally {
            U.cleanupServiceUser("otherSystemPrincipal");
        }
    }

    @Test
    public void principalAclNotAvailableEntryWithRestrictionPresent() throws Exception {
        JackrabbitAccessControlManager acMgr = (JackrabbitAccessControlManager) adminSession.getAccessControlManager();
        try {
            // create service user outside of supported tree for principal-based access control
            U.parseAndExecute("create service user otherSystemPrincipal");
            // setup path-based access control to establish effective permission setup
            String setup = "set ACL for otherSystemPrincipal \n"
                    + "allow jcr:read on " + path + " restriction(rep:glob,*abc*)\n"
                    + "end";
            U.parseAndExecute(setup);

            // setting up principal-acl will not succeed (principal not located below supported path)
            // but there exists an equivalent entry with the same definition -> no exception
            setup = "set principal ACL for otherSystemPrincipal \n"
                    + "allow jcr:read on " + path + " restriction(rep:glob,*abc*)\n"
                    + "end";
            U.parseAndExecute(setup);

            Principal principal = adminSession
                    .getUserManager()
                    .getAuthorizable("otherSystemPrincipal")
                    .getPrincipal();
            for (AccessControlPolicy policy : acMgr.getPolicies(principal)) {
                assertFalse(policy instanceof PrincipalAccessControlList);
            }
        } finally {
            U.cleanupServiceUser("otherSystemPrincipal");
        }
    }

    @Test
    public void principalAclNotAvailableEntryWithRestrictionPresentStrict() throws Exception {
        JackrabbitAccessControlManager acMgr = (JackrabbitAccessControlManager) adminSession.getAccessControlManager();
        try {
            // create service user outside of supported tree for principal-based access control
            U.parseAndExecute("create service user otherSystemPrincipal");
            // setup path-based access control to establish effective permission setup
            String setup = "set ACL for otherSystemPrincipal \n"
                    + "allow jcr:read on " + path + " restriction(rep:glob,*abc*)\n"
                    + "end";
            U.parseAndExecute(setup);

            // setting up principal-acl will not succeed (principal not located below supported path)
            setup = "ensure principal ACL for otherSystemPrincipal \n"
                    + "allow jcr:read on " + path + " restriction(rep:glob,*abc*)\n"
                    + "end";
            try {
                U.parseAndExecute(setup);
                fail("Setting a principal ACL outside a supported path must not succeed");
            } catch (RuntimeException e) {
                assertRegex(NO_PRINCIPAL_CONTROL_LIST_AVAILABLE, e.getMessage());
            }
        } finally {
            U.cleanupServiceUser("otherSystemPrincipal");
        }
    }

    @Test
    public void principalAclNotAvailableRepoLevelPermissions() throws Exception {
        JackrabbitAccessControlManager acMgr = (JackrabbitAccessControlManager) adminSession.getAccessControlManager();
        try {
            // create service user outside of supported tree for principal-based access control
            U.parseAndExecute("create service user otherSystemPrincipal");
            // setup path-based access control to establish effective permission setup
            String setup =
                    "set ACL for otherSystemPrincipal \n" + "allow jcr:namespaceManagement on :repository\n" + "end";
            U.parseAndExecute(setup);

            // setting up principal-acl will not succeed (principal not located below supported path)
            // but there exists an equivalent entry with the same definition -> no exception
            setup = "set principal ACL for otherSystemPrincipal \n"
                    + "allow jcr:namespaceManagement on :repository\n"
                    + "end";
            U.parseAndExecute(setup);

            Principal principal = adminSession
                    .getUserManager()
                    .getAuthorizable("otherSystemPrincipal")
                    .getPrincipal();
            for (AccessControlPolicy policy : acMgr.getPolicies(principal)) {
                assertFalse(policy instanceof PrincipalAccessControlList);
            }
        } finally {
            U.cleanupServiceUser("otherSystemPrincipal");
        }
    }

    @Test
    public void principalAclNotAvailableRepoLevelPermissionsStrict() throws Exception {
        JackrabbitAccessControlManager acMgr = (JackrabbitAccessControlManager) adminSession.getAccessControlManager();
        try {
            // create service user outside of supported tree for principal-based access control
            U.parseAndExecute("create service user otherSystemPrincipal");
            // setup path-based access control to establish effective permission setup
            String setup =
                    "set ACL for otherSystemPrincipal \n" + "allow jcr:namespaceManagement on :repository\n" + "end";
            U.parseAndExecute(setup);

            // setting up principal-acl will not succeed (principal not located below supported path)
            setup = "ensure principal ACL for otherSystemPrincipal \n"
                    + "allow jcr:namespaceManagement on :repository\n"
                    + "end";
            try {
                U.parseAndExecute(setup);
                fail("Setting a principal ACL outside a supported path must not succeed");
            } catch (RuntimeException e) {
                assertRegex(NO_PRINCIPAL_CONTROL_LIST_AVAILABLE, e.getMessage());
            }

        } finally {
            U.cleanupServiceUser("otherSystemPrincipal");
        }
    }

    @Test
    public void principalAclNotAvailableNonExistingNode() throws Exception {
        JackrabbitAccessControlManager acMgr = (JackrabbitAccessControlManager) adminSession.getAccessControlManager();
        try {
            // create service user outside of supported tree for principal-based access control
            U.parseAndExecute("create service user otherSystemPrincipal");

            // setting up principal-acl will not succeed (principal not located below supported path)
            // but since the target node does not exist we cannot verify if an equivalent resource-based ac-setup exists
            // (AccessControlManager.getPolicies would fail with PathNotFoundException) => relaxed behavior (SLING-9412)
            String setup =
                    "set principal ACL for otherSystemPrincipal \n" + "allow jcr:read on /non/existing/path\n" + "end";
            U.parseAndExecute(setup);

            Principal principal = getPrincipal("otherSystemPrincipal");
            for (AccessControlPolicy policy : acMgr.getPolicies(principal)) {
                assertFalse(policy instanceof PrincipalAccessControlList);
            }
        } finally {
            U.cleanupServiceUser("otherSystemPrincipal");
        }
    }

    @Test
    public void principalAclNotAvailableNonExistingNodeStrict() throws Exception {
        JackrabbitAccessControlManager acMgr = (JackrabbitAccessControlManager) adminSession.getAccessControlManager();
        try {
            // create service user outside of supported tree for principal-based access control
            U.parseAndExecute("create service user otherSystemPrincipal");

            // setting up principal-acl will not succeed (principal not located below supported path)
            String setup = "ensure principal ACL for otherSystemPrincipal \n"
                    + "allow jcr:read on /non/existing/path\n"
                    + "end";
            try {
                U.parseAndExecute(setup);
                fail("Setting a principal ACL outside a supported path must not succeed");
            } catch (RuntimeException e) {
                assertRegex(NO_PRINCIPAL_CONTROL_LIST_AVAILABLE, e.getMessage());
            }

            Principal principal = getPrincipal("otherSystemPrincipal");
            for (AccessControlPolicy policy : acMgr.getPolicies(principal)) {
                assertFalse(policy instanceof PrincipalAccessControlList);
            }

        } finally {
            U.cleanupServiceUser("otherSystemPrincipal");
        }
    }

    @Test
    public void testHomePath() throws Exception {
        Authorizable su = getServiceUser(U.username);
        Principal principal = su.getPrincipal();

        assertNull(getAcl(principal, U.adminSession));

        AclLine line = new AclLine(AclLine.Action.ALLOW);
        line.setProperty(AclLine.PROP_PRINCIPALS, Collections.singletonList(principal.getName()));
        line.setProperty(AclLine.PROP_PRIVILEGES, Collections.singletonList(Privilege.JCR_READ));
        line.setProperty(AclLine.PROP_PATHS, Collections.singletonList(":home:" + U.username + "#"));
        AclUtil.setPrincipalAcl(new CachingSessionWrapper(U.adminSession), U.username, Collections.singletonList(line), false);

        PrincipalAccessControlList acl = getAcl(principal, U.adminSession);
        assertNotNull(acl);
        assertEquals(1, acl.size());
        PrincipalAccessControlList.Entry entry = (PrincipalAccessControlList.Entry) acl.getAccessControlEntries()[0];
        assertArrayEquals(
                AccessControlUtils.privilegesFromNames(AclUtil.getJACM(U.adminSession), Privilege.JCR_READ),
                entry.getPrivileges());
        assertEquals(su.getPath(), entry.getEffectivePath());
    }

    @Test
    public void testTransientUser() throws Exception {
        UserManager uMgr = ((JackrabbitSession) U.adminSession).getUserManager();
        String id = "systemUser_" + UUID.randomUUID().toString();
        try {
            User su = uMgr.createSystemUser(id, relPath);
            String setup = "set principal ACL for " + su.getPrincipal().getName() + " \n"
                    + "allow jcr:read on " + path + "\n"
                    + "end";
            U.parseAndExecute(setup);

            PrincipalAccessControlList acl = getAcl(su.getPrincipal(), U.adminSession);
            assertNotNull(acl);
            assertEquals(1, acl.size());
        } finally {
            U.adminSession.refresh(false);
            Authorizable a = uMgr.getAuthorizable(id);
            if (a != null) {
                a.remove();
                U.adminSession.save();
            }
        }
    }

    @Test
    public void testRemoveAction() throws Exception {
        String setup = "set principal ACL for " + U.username + "\n" + "allow jcr:write on " + path + "\n" + "end";
        U.parseAndExecute(setup);

        setup = "set principal ACL for " + U.username + "\n" + "remove jcr:write on " + path + "\n" + "end";

        try {
            U.parseAndExecute(setup);
            fail("Expecting REMOVE to fail");
        } catch (RuntimeException rex) {
            assertRegex(REMOVE_NOT_SUPPORTED_REGEX, rex.getMessage());
        }
    }

    @Test(expected = None.class)
    public void testRemoveNoExistingPolicy() throws Exception {
        String setup = "remove principal ACE for " + U.username + "\n" + "allow jcr:read on " + path + "\n" + "end";
        U.parseAndExecute(setup);
    }

    @Test
    public void testRemoveMatchingEntry() throws Exception {
        Principal principal = getPrincipal(U.username);
        String setup = "set principal ACL for " + U.username + "\n" + "allow jcr:write on " + path + "\n" + "end";
        U.parseAndExecute(setup);
        assertPolicy(principal, U.adminSession, 1);

        setup = "remove principal ACE for " + U.username + "\n" + "allow jcr:write on " + path + "\n" + "end";
        U.parseAndExecute(setup);
        assertPolicy(principal, U.adminSession, 0);
    }

    @Test
    public void testRemoveNoMatchingEntry() throws Exception {
        Principal principal = getPrincipal(U.username);
        String setup = "set principal ACL for " + U.username + "\n" + "allow jcr:write on " + path + "\n" + "end";
        U.parseAndExecute(setup);
        assertPolicy(principal, U.adminSession, 1);

        // privilege mismatch
        setup = "remove principal ACE for " + U.username + "\n" + "allow jcr:read on " + path + "\n" + "end";
        U.parseAndExecute(setup);
        assertPolicy(principal, U.adminSession, 1);

        // privilege mismatch 2
        setup = "remove principal ACE for " + U.username + "\n" + "allow jcr:read,jcr:write on " + path + "\n" + "end";
        U.parseAndExecute(setup);
        assertPolicy(principal, U.adminSession, 1);

        // path mismatch
        setup = "remove principal ACE for " + U.username + "\n" + "allow jcr:write on " + path + "/mismatch\n" + "end";
        U.parseAndExecute(setup);
        assertPolicy(principal, U.adminSession, 1);

        // restriction mismatch
        setup = "remove principal ACE for " + U.username + "\n"
                + "allow jcr:write on " + path + " restriction(rep:glob, /*/jcr:content/*)\n"
                + "end";
        U.parseAndExecute(setup);
        assertPolicy(principal, U.adminSession, 1);
    }

    @Test(expected = RepoInitException.class)
    public void testRemoveNonExistingPrincipal() throws Exception {
        String setup = "remove principal ACE for nonExistingPrincipal\n" + "deny jcr:write on " + path + "\n" + "end";
        U.parseAndExecute(setup);
    }

    @Test
    public void testRemovePrincipalMismatch() throws Exception {
        String setup = "set principal ACL for " + U.username + "\n" + "allow jcr:write on " + path + "\n" + "end";
        U.parseAndExecute(setup);
        U.parseAndExecute("create service user otherSystemPrincipal");
        assertPolicy(getPrincipal(U.username), U.adminSession, 1);

        setup = "remove principal ACE for otherSystemPrincipal\n" + "allow jcr:write on " + path + "\n" + "end";
        U.parseAndExecute(setup);

        // ace must not have been removed
        assertPolicy(getPrincipal(U.username), U.adminSession, 1);
        assertNull(getAcl(getPrincipal("otherSystemPrincipal"), U.adminSession));
    }

    @Test
    public void testRemovePrincipalMismatchStrict() throws Exception {
        String setup = "ensure principal ACL for " + U.username + "\n" + "allow jcr:write on " + path + "\n" + "end";
        U.parseAndExecute(setup);
        U.parseAndExecute("create service user otherSystemPrincipal");
        assertPolicy(getPrincipal(U.username), U.adminSession, 1);

        setup = "remove principal ACE for otherSystemPrincipal\n" + "allow jcr:write on " + path + "\n" + "end";
        U.parseAndExecute(setup);
        // ace must not have been removed
        assertPolicy(getPrincipal(U.username), U.adminSession, 1);
        assertNull(getAcl(getPrincipal("otherSystemPrincipal"), U.adminSession));

        try {
            setup = "ensure principal ACL for otherSystemPrincipal\n" + "remove jcr:write on " + path + "\n" + "end";
            U.parseAndExecute(setup);
            fail("Expecting REMOVE to fail");
        } catch (RuntimeException rex) {
            assertRegex(NO_PRINCIPAL_CONTROL_LIST_AVAILABLE, rex.getMessage());
        }
    }

    @Test
    public void testRemoveAllNoExistingPolicy() throws Exception {
        String setup = "set principal ACL for " + U.username + "\n" + "remove * on " + path + "\n" + "end";
        U.parseAndExecute(setup);

        // removal must be ignored and no policy must be created
        assertNull(getAcl(getPrincipal(U.username), U.adminSession));
    }

    @Test(expected = RepoInitException.class)
    public void testAllRemoveNonExistingPrincipal() throws Exception {
        String setup = "set principal ACL for nonExistingPrincipal\n" + "remove * on " + path + "\n" + "end";
        U.parseAndExecute(setup);
    }

    @Test
    public void testRemoveAll() throws Exception {
        String setup = "set principal ACL for " + U.username + "\n"
                + "allow jcr:write on " + path + "\n"
                + "allow jcr:read on " + path + "\n"
                + "end";
        U.parseAndExecute(setup);

        setup = "set principal ACL for " + U.username + "\n" + "remove * on " + path + "\n" + "end";
        U.parseAndExecute(setup);

        assertPolicy(getPrincipal(U.username), U.adminSession, 0);
    }

    @Test
    public void testRemoveAllRepositoryPath() throws Exception {
        String setup = "set principal ACL for " + U.username + "\n"
                + "allow jcr:write on " + path + "\n"
                + "allow jcr:namespaceManagement on :repository\n"
                + "end";
        U.parseAndExecute(setup);

        assertPolicy(getPrincipal(U.username), U.adminSession, 2);

        setup = "set principal ACL for " + U.username + "\n" + "remove * on :repository\n" + "end";
        U.parseAndExecute(setup);

        assertPolicy(getPrincipal(U.username), U.adminSession, 1);
    }

    @Test
    public void testRemoveAllPartialPathMatch() throws Exception {
        String setup = "set principal ACL for " + U.username + "\n"
                + "allow jcr:write on " + path + "\n"
                + "allow jcr:write on /another/path\n"
                + "end";
        U.parseAndExecute(setup);

        assertPolicy(getPrincipal(U.username), U.adminSession, 2);

        setup = "set principal ACL for " + U.username + "\n" + "remove * on " + path + "\n" + "end";
        U.parseAndExecute(setup);

        assertPolicy(getPrincipal(U.username), U.adminSession, 1);
    }

    @Test
    public void testRemoveAllMultiplePaths() throws Exception {
        String setup = "set principal ACL for " + U.username + "\n"
                + "allow jcr:write on " + path + "\n"
                + "allow jcr:write on home(" + U.username + ")\n"
                + "end";
        U.parseAndExecute(setup);

        assertPolicy(getPrincipal(U.username), U.adminSession, 2);

        setup = "set principal ACL for " + U.username + "\n"
                + "remove * on " + path + ", home(" + U.username + ")\n"
                + "end";
        U.parseAndExecute(setup);

        assertPolicy(getPrincipal(U.username), U.adminSession, 0);
    }

    @Test
    public void testRemoveAllPathMismatch() throws Exception {
        String setup = "set principal ACL for " + U.username + "\n"
                + "allow jcr:write on " + path + "\n"
                + "allow jcr:read on " + path + "\n"
                + "end";
        U.parseAndExecute(setup);

        setup = "set principal ACL for " + U.username + "\n" + "remove * on /another/path\n" + "end";
        U.parseAndExecute(setup);

        assertPolicy(getPrincipal(U.username), U.adminSession, 2);
    }

    @Test(expected = None.class)
    public void testRemoveAllPrincipalMismatch() throws Exception {
        String setup = "set principal ACL for " + U.username + "\n" + "allow jcr:write on " + path + "\n" + "end";
        U.parseAndExecute(setup);
        U.parseAndExecute("create service user otherSystemPrincipal");

        setup = "set principal ACL for otherSystemPrincipal\n" + "remove * on " + path + "\n" + "end";
        U.parseAndExecute(setup);
    }

    @Test(expected = None.class)
    public void testRemoveAllPrincipalMismatchStrict() throws Exception {
        String setup = "ensure principal ACL for " + U.username + "\n" + "allow jcr:write on " + path + "\n" + "end";
        U.parseAndExecute(setup);
        U.parseAndExecute("create service user otherSystemPrincipal");

        setup = "ensure principal ACL for otherSystemPrincipal\n" + "remove * on " + path + "\n" + "end";
        try {
            U.parseAndExecute(setup);
            fail("Setting a principal ACL outside a supported path must not succeed");
        } catch (RuntimeException e) {
            assertRegex(NO_PRINCIPAL_CONTROL_LIST_AVAILABLE, e.getMessage());
        }
    }

    @Test
    public void testRemoveAllPrincipalDuplicated() throws Exception {
        U.parseAndExecute(""
                + "set principal ACL for " + U.username + "\n"
                + "allow jcr:write on " + path + "\n"
                + "allow jcr:read on " + path + "\n"
                + "end\n"
                + "set principal ACL for " + U.username + "\n"
                // duplicating the remove statement exposes SLING-10145
                + "remove * on " + path + "\n"
                + "remove * on " + path + "\n"
                + "end\n");

        assertPolicy(getPrincipal(U.username), U.adminSession, 0);
    }

    @Test
    public void testAddAndDeleteAcl() throws Exception {
        U.parseAndExecute(""
                + "set principal ACL for " + U.username + "\n"
                + "allow jcr:write on " + path + "\n"
                + "end\n"
                + "delete principal ACL for " + U.username + "\n");

        assertNull(getAcl(getPrincipal(U.username), U.adminSession));
    }

    @Test
    public void testDeleteAcl() throws Exception {
        PrincipalAccessControlList acl = getApplicableAcl(getPrincipal(U.username), U.adminSession);
        acl.addEntry("/content", AccessControlUtils.privilegesFromNames(U.adminSession, Privilege.JCR_READ));
        U.adminSession.getAccessControlManager().setPolicy(acl.getPath(), acl);
        U.adminSession.save();

        assertNotNull(getAcl(getPrincipal(U.username), U.adminSession));
        U.parseAndExecute("delete principal ACL for " + U.username + "\n");
        assertNull(getAcl(getPrincipal(U.username), U.adminSession));
    }

    @Test
    public void testDeleteNonExistingAcl() throws Exception {
        assertNull(getAcl(getPrincipal(U.username), U.adminSession));
        // removing non-existing policy must not fail
        U.parseAndExecute("delete principal ACL for " + U.username + "\n");
        assertNull(getAcl(getPrincipal(U.username), U.adminSession));
    }

    @Test
    public void testDeleteAclNonExistingPrincipal() throws Exception {
        assertNull(getAcl(getPrincipal(U.username), U.adminSession));
        // removing policy for non-existing principal must not fail
        U.parseAndExecute("delete principal ACL for non-existing-service-user\n");
        assertNull(getAcl(getPrincipal(U.username), U.adminSession));
    }

    @Test
    public void testDeleteResourceBasedAclByPrincipal() throws Exception {
        U.parseAndExecute(""
                + "create path (nt:unstructured) /var\n"
                + "set ACL for " + U.username + "\n"
                + "allow jcr:read on /var\n"
                + "end\n"
                + "set principal ACL for " + U.username + "\n"
                + "allow jcr:read on /var\n"
                + "end\n");

        assertEquals(1, acMgr.getPolicies("/var").length);

        U.parseAndExecute("delete ACL for " + U.username + "\n");
        // resource-based acl at /var must be removed as it only contains a single entry for U.userName
        assertEquals(0, acMgr.getPolicies("/var").length);

        // removing resource-based ac-setup by principal must not delete any principal-based ac setup.
        Principal p = getPrincipal(U.username);
        AccessControlPolicy[] policies = acMgr.getPolicies(p);
        assertEquals(1, policies.length);
        assertTrue(policies[0] instanceof PrincipalAccessControlList);
    }

    @Nullable
    private static PrincipalAccessControlList getApplicableAcl(@NotNull Principal principal, @NotNull Session session)
            throws RepositoryException {
        for (AccessControlPolicy policy : AclUtil.getJACM(session).getApplicablePolicies(principal)) {
            if (policy instanceof PrincipalAccessControlList) {
                return (PrincipalAccessControlList) policy;
            }
        }
        return null;
    }

    @Nullable
    private static PrincipalAccessControlList getAcl(@NotNull Principal principal, @NotNull Session session)
            throws RepositoryException {
        for (AccessControlPolicy policy : AclUtil.getJACM(session).getPolicies(principal)) {
            if (policy instanceof PrincipalAccessControlList) {
                return (PrincipalAccessControlList) policy;
            }
        }
        return null;
    }
}
