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

import org.apache.jackrabbit.api.JackrabbitRepository;
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
import org.apache.sling.jcr.repoinit.impl.TestUtil;
import org.apache.sling.repoinit.parser.RepoInitParsingException;
import org.apache.sling.testing.mock.osgi.junit.OsgiContext;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import javax.jcr.Node;
import javax.jcr.Property;
import javax.jcr.Repository;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.SimpleCredentials;
import javax.jcr.security.AccessControlManager;
import javax.security.auth.Subject;
import java.security.PrivilegedExceptionAction;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class PrincipalBasedAclTest {

    @Rule
    public final OsgiContext context = new OsgiContext();

    private Repository repository;
    private Session adminSession;

    private TestUtil U;

    private String path;
    private String propPath;

    private Session testSession;

    @Before
    public void before() throws Exception {
        SecurityProvider sp = createSecurityProvider();
        repository = new Jcr().with(sp).createRepository();

        String uid = sp.getParameters(UserConfiguration.NAME).getConfigValue(UserConstants.PARAM_ADMIN_ID, UserConstants.DEFAULT_ADMIN_ID);
        adminSession = repository.login(new SimpleCredentials(uid, uid.toCharArray()), null);
        U = new TestUtil(adminSession);

        Node tmp = adminSession.getRootNode().addNode("tmp_" + U.id);
        Property prop = tmp.setProperty("prop", "value");
        path = tmp.getPath();
        propPath = prop.getPath();
        adminSession.save();

        U.parseAndExecute("create service user " + U.username);

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
        SecurityProvider sp = SecurityProviderBuilder.newBuilder().with(ConfigurationParameters.of("authorizationCompositionType", "OR")).build();

        ConfigurationParameters userParams = sp.getParameters(UserConfiguration.NAME);
        String userRoot = userParams.getConfigValue(UserConstants.PARAM_USER_PATH, UserConstants.DEFAULT_USER_PATH);
        String systemRelPath = userParams.getConfigValue(UserConstants.PARAM_SYSTEM_RELATIVE_PATH, UserConstants.DEFAULT_SYSTEM_RELATIVE_PATH);
        FilterProviderImpl fp = new FilterProviderImpl();
        context.registerInjectActivateService(fp, Collections.singletonMap("path", PathUtils.concat(userRoot, systemRelPath)));

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
        SystemUserPrincipal principal = new SystemUserPrincipal() {
            @Override
            public String getName() {
                return systemUserPrincipalName;
            }
        };
        Subject subject = new Subject(true, Collections.singleton(principal), Collections.emptySet(), Collections.emptySet());
        return Subject.doAs(subject, new PrivilegedExceptionAction<Session>() {
            @Override
            public Session run() throws Exception {
                return repository.login(null, null);
            }
        });
    }

    private static void assertPermission(Session userSession, String absolutePath, String actions, boolean successExpected) throws RepositoryException {
        assertEquals("Expecting "+actions+" for path " + absolutePath + " to be " + (successExpected ? "granted" : "denied"), successExpected, userSession.hasPermission(absolutePath, actions));
    }

    @Test
    public void readGranted() throws Exception {
        String setup =
                "set principal ACL for " + U.username + "\n"
                        + "allow jcr:read on " + path + "\n"
                        + "end";
        U.parseAndExecute(setup);
        testSession.refresh(false);

        assertPermission(testSession, path, Session.ACTION_READ, true);
        assertPermission(testSession, propPath, Session.ACTION_READ, true);
        assertPermission(testSession, propPath, Session.ACTION_SET_PROPERTY, false);
    }

    @Test
    public void grantedNonExistingPath() throws Exception {
        String nonExistingPath = path +"/nonExisting";
        String setup =
                "set principal ACL for " + U.username + "\n"
                        + "allow jcr:read on " + nonExistingPath + "\n"
                        + "end";
        U.parseAndExecute(setup);
        testSession.refresh(false);

        assertPermission(testSession, nonExistingPath, Session.ACTION_READ, true);
    }

    @Test
    public void grantAtRootPath() throws Exception {
        String setup =
                "set principal ACL for " + U.username + "\n"
                        + "allow jcr:all on /\n"
                        + "end"
                ;

        U.parseAndExecute(setup);
        testSession.refresh(false);

        assertPermission(testSession, "/newChild", Session.ACTION_READ +","+ Session.ACTION_ADD_NODE +","+  Session.ACTION_REMOVE, true);
    }

    @Test
    public void multiplePrivileges() throws Exception {
        String setup =
                "set principal ACL for " + U.username + "\n"
                        + "allow jcr:read, jcr:versionManagement on " + path + "\n"
                        + "end";
        U.parseAndExecute(setup);
        testSession.refresh(false);

        String permissions = Permissions.getString(Permissions.READ|Permissions.VERSION_MANAGEMENT);
        assertPermission(testSession, path, permissions, true);
        assertPermission(testSession, path + "/newchild", permissions, true);
    }

    @Test(expected = RuntimeException.class)
    public void invalidPrivilege() throws Exception {
        String setup =
                "set principal ACL for " + U.username + "\n"
                        + "allow my:invalidPrivilege on " + path + "\n"
                        + "end";
        U.parseAndExecute(setup);
    }

    @Test(expected = RuntimeException.class)
    public void denyEntry() throws Exception  {
        String setup =
                "set principal ACL for " + U.username + "\n"
                        + "deny jcr:read on " + path + "\n"
                        + "end";
        U.parseAndExecute(setup);
    }

    @Test
    public void repoLevelPermission() throws Exception {
        String setup =
                "set principal ACL for " + U.username + "\n"
                        + "allow jcr:namespaceManagement on :repository\n"
                        + "end";
        U.parseAndExecute(setup);
        testSession.refresh(false);

        assertTrue(testSession.getAccessControlManager().hasPrivileges(null, AccessControlUtils.privilegesFromNames(testSession, "jcr:namespaceManagement")));
    }

    @Test
    public void repoLevelAndPath() throws Exception {
        String setup =
                "set principal ACL for " + U.username + "\n"
                        + "allow jcr:all on :repository, "+path+"\n"
                        + "end";
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

        String setup =
                "set principal ACL for " + U.username + "\n"
                        + "allow jcr:read on "+path+" restriction(rep:glob,*abc*)\n"
                        + "end"
                ;

        U.parseAndExecute(setup);
        testSession.refresh(false);

        assertPermission(testSession, path + allowed, Session.ACTION_READ, true);
        assertPermission(testSession, path + notAllowed, Session.ACTION_READ, false);
    }

    @Test
    public void emptyGlobRestriction() throws Exception {
        String setup =
                "set principal ACL for " + U.username + "\n"
                        + "allow jcr:read on "+path+" restriction (rep:glob)\n"
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
        String setup =
                "set principal ACL for " + U.username + "\n"
                        + "allow jcr:modifyProperties on / restriction(rep:itemNames,propName,prop)\n"
                        + "end"
                ;

        U.parseAndExecute(setup);
        testSession.refresh(false);

        assertPermission(testSession, propPath, Session.ACTION_SET_PROPERTY, true);
        assertPermission(testSession, path + "/propName", Session.ACTION_SET_PROPERTY, true);
    }


    @Test
    public void emptyMvRestrictionTest() throws Exception {
        String setup =
                "set principal ACL for " + U.username + "\n"
                        + "allow jcr:read on "+path+" restriction(rep:ntNames)\n"
                        + "end"
                ;
        U.parseAndExecute(setup);
        testSession.refresh(false);

        assertPermission(testSession, propPath, Session.ACTION_READ, false);
    }

    @Test(expected = RuntimeException.class)
    public void unsupportedRestriction() throws Exception {
        String setup =
                "set principal ACL for " + U.username + "\n"
                        + "allow jcr:read on "+path+" restriction(jcr:unsupported,value)\n"
                        + "end"
                ;
        U.parseAndExecute(setup);
    }

    @Test
    public void multiplePaths() throws Exception {
        String setup =
                "set principal ACL for " + U.username + "\n"
                        + "allow jcr:read on "+path+", /content\n"
                        + "end";

        U.parseAndExecute(setup);
        testSession.refresh(false);

        assertPermission(testSession, PathUtils.ROOT_PATH, Session.ACTION_READ, false);
        assertPermission(testSession, path, Session.ACTION_READ, true);
        assertPermission(testSession, "/content", Session.ACTION_READ, true);
    }

    @Test(expected = RepoInitParsingException.class)
    public void missingPath() throws Exception {
        String setup =
                "set principal ACL for " + U.username + "\n"
                        + "allow jcr:read on \n"
                        + "end";

        U.parseAndExecute(setup);
    }

    @Test
    public void multiplePrincipals() throws Exception {
        Session s = null;
        try {
            U.parseAndExecute("create service user otherSystemPrincipal");
            String setup =
                    "set principal ACL for " + U.username + ",otherSystemPrincipal \n"
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
        String setup =
                "set principal ACL for \n"
                        + "allow jcr:read on "+path+"\n"
                        + "end";

        U.parseAndExecute(setup);
    }
}