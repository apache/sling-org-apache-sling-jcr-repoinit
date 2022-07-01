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
package org.apache.sling.jcr.repoinit.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.security.Principal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import javax.jcr.AccessDeniedException;
import javax.jcr.PathNotFoundException;
import javax.jcr.PropertyType;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.UnsupportedRepositoryOperationException;
import javax.jcr.Value;
import javax.jcr.ValueFactory;
import javax.jcr.security.Privilege;

import org.apache.jackrabbit.api.JackrabbitSession;
import org.apache.jackrabbit.api.security.JackrabbitAccessControlList;
import org.apache.jackrabbit.api.security.user.AuthorizableExistsException;
import org.apache.jackrabbit.api.security.user.Group;
import org.apache.jackrabbit.api.security.user.UserManager;
import org.apache.jackrabbit.commons.jackrabbit.authorization.AccessControlUtils;
import org.apache.jackrabbit.oak.commons.PathUtils;
import org.apache.jackrabbit.oak.spi.security.principal.EveryonePrincipal;
import org.apache.jackrabbit.oak.spi.security.principal.PrincipalImpl;
import org.apache.sling.repoinit.parser.RepoInitParsingException;
import org.apache.sling.testing.mock.sling.ResourceResolverType;
import org.apache.sling.testing.mock.sling.junit.SlingContext;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;

public class AclUtilTest {

    @Rule
    public final SlingContext context = new SlingContext(ResourceResolverType.JCR_OAK);
    
    private TestUtil U;

    private JackrabbitAccessControlList acl;
    
    @Before
    public void setup() throws RepositoryException, RepoInitParsingException {
        U = new TestUtil(context);
        U.parseAndExecute("create service user " + U.username);
        
        final String aclSetup = 
                "set ACL for " + U.username + "\n"
                + "allow jcr:read on /\n"
                + "allow jcr:write on /\n"                    
                + "end"
                ;
            
        U.parseAndExecute(aclSetup);
        acl = AccessControlUtils.getAccessControlList(U.adminSession, "/");
    }

    @After
    public void cleanup() throws RepositoryException, RepoInitParsingException {
        if (U.adminSession != null) {
            U.adminSession.refresh(false);
        }
        U.cleanupUser();
    }

    @Test
    public void entryIsContained() throws Exception {
        
        // validates that an exact match of the username, privilege list and isAllow is contained
        
        assertIsContained(acl, U.username, new String[]{ Privilege.JCR_READ, Privilege.JCR_WRITE }, true);
    }

    @Test
    public void testDeclaredAggregatePrivilegesAreContained() throws Exception {
        String [] privileges = new String[]{
          //JCR_READ
                "rep:readNodes","rep:readProperties",
          // JCR_WRITE
                Privilege.JCR_ADD_CHILD_NODES,Privilege.JCR_MODIFY_PROPERTIES,
                Privilege.JCR_REMOVE_CHILD_NODES, Privilege.JCR_REMOVE_NODE
        };
        assertIsContained(acl, U.username, privileges, true);
    }

    @Test
    public void testAllAggregatePrivilegesAreContained() throws Exception {
        String [] privileges = new String[]{
                //JCR_READ
                "rep:readNodes","rep:readProperties",
                // JCR_WRITE
                Privilege.JCR_ADD_CHILD_NODES,"rep:addProperties",
                "rep:alterProperties","rep:removeProperties",
                Privilege.JCR_REMOVE_CHILD_NODES, Privilege.JCR_REMOVE_NODE
        };
        assertIsContained(acl, U.username, privileges, true);
    }

    
    @Test
    public void entryWithFewerPrivilegesIsContained() throws Exception {
        
        // validates that an exact match of the username and isAllow but with fewer privileges is contained
        assertIsContained(acl, U.username, new String[]{ Privilege.JCR_WRITE }, true);
    }

    @Test
    public void entryWithDifferentPrivilegeIsNotContained() throws Exception {
        // validates that an exact match of the username and isAllow but with different privileges is contained
        assertIsNotContained(acl, U.username, new String[]{ Privilege.JCR_ALL }, true);
    }
    
    @Test
    public void entryWithPartiallyMatchingPrivilegesIsContained() throws Exception {
        // validates that an exact match of the username and isAllow but with privileges partially overlapping is contained
        // existing: JCR_READ, JCR_WRITE 
        // new: JCR_READ, JCR_MODIFY_PROPERTIES
        assertIsContained(acl, U.username, new String[]{Privilege.JCR_READ, Privilege.JCR_MODIFY_PROPERTIES }, true);
    }    
    
    @Test
    public void entryWithDifferentUserIsNotContained() throws Exception {

        // validates that an exact match of the privileges and isAllow but with different username is not contained
        String otherPrincipalName = U.username + "_";
        try {
            U.parseAndExecute("create service user " + otherPrincipalName);
            assertIsNotContained(acl, otherPrincipalName, new String[]{ Privilege.JCR_READ, Privilege.JCR_WRITE }, true);
        } finally {
            U.cleanupServiceUser(otherPrincipalName);
        }
    }
    
    @Test
    public void entryWithDifferentIsAllowIsNotContained() throws Exception {
        // validates that an exact match of the username and privileges but with different is allow is not contained
        assertIsNotContained(acl, U.username, new String[]{ Privilege.JCR_READ, Privilege.JCR_WRITE }, false);
    }
    
    private void assertArrayCompare(Value[] a, Value[] b, boolean expected) {
        final boolean actual = AclUtil.compareValues(a, b);
        assertEquals("Expecting compareArrays to return " + expected, actual, expected);
    }

    @Test
    public void compareValuesTest() throws RepositoryException {
        ValueFactory vf =  U.adminSession.getValueFactory();
        final Value[] a1 = new Value[] {vf.createValue("jcr:content", PropertyType.NAME), vf.createValue("jcr:data", PropertyType.NAME)};
        final Value[] a2 = new Value[] {vf.createValue("a", PropertyType.STRING), vf.createValue("b", PropertyType.STRING)};
        final Value[] a3 = new Value[] {vf.createValue("b", PropertyType.STRING), vf.createValue("c", PropertyType.STRING)};

        final Value[] a4 = new Value[] {vf.createValue("a", PropertyType.STRING), vf.createValue("b", PropertyType.STRING),  vf.createValue("c", PropertyType.STRING)};

        final Value[] a5 = new Value[] {vf.createValue("b", PropertyType.STRING), vf.createValue("a", PropertyType.STRING)};
        final Value[] a6 = new Value[] {vf.createValue("b", PropertyType.STRING), vf.createValue("a", PropertyType.STRING), vf.createValue("c", PropertyType.STRING)};
        final Value[] a7 = new Value[] {vf.createValue("a", PropertyType.STRING), vf.createValue("b", PropertyType.STRING)};
        final Value[] a8 = new Value[] {vf.createValue("jcr:data", PropertyType.NAME), vf.createValue("jcr:content", PropertyType.NAME)};
        final Value[] a9 = new Value[] {vf.createValue("jcr:data", PropertyType.NAME), vf.createValue("jcr:content", PropertyType.STRING)};
        final Value[] emptyA = {};
        final Value[] emptyB = {};

        assertArrayCompare(null, a3, false);
        assertArrayCompare(a3, null, false);
        assertArrayCompare(null, null, false);

        assertArrayCompare(emptyA, emptyB, true);
        assertArrayCompare(emptyA, emptyA, true);
        assertArrayCompare(emptyA, a1, false);
        assertArrayCompare(a1, emptyA, false);

        assertArrayCompare(a1, a3, false);
        assertArrayCompare(a3, a1, false);
        assertArrayCompare(a1, a1, true);
        assertArrayCompare(a1, a4, false);
        assertArrayCompare(a4, a4, true);
        assertArrayCompare(a2, a7, true);
        assertArrayCompare(a5, a7, true);
        assertArrayCompare(a4, a6, true);
        assertArrayCompare(a1, a8, true);
        assertArrayCompare(a8, a9, false);
    }

    /**
     * Repo init should work for principals even if they are not present as user/group with the user manager.
     *
     * @see <a href="https://issues.apache.org/jira/browse/SLING-8604">SLING-8604</>
     */
    @Test
    public void testSetAclForEveryone() throws Exception {
        Session session = U.adminSession;
        try {
        assertTrue(session instanceof JackrabbitSession);
        assertNull(((JackrabbitSession) session).getUserManager().getAuthorizable(EveryonePrincipal.getInstance()));

        AclUtil.setAcl(session, Collections.singletonList(EveryonePrincipal.NAME), Collections.singletonList(PathUtils.ROOT_PATH), Collections.singletonList(Privilege.JCR_READ), false);

        assertIsContained(AccessControlUtils.getAccessControlList(session, PathUtils.ROOT_PATH), EveryonePrincipal.NAME, new String[] {Privilege.JCR_READ}, false);
        } finally {
            session.refresh(false);
        }
    }

    /**
     * @see <a href="https://issues.apache.org/jira/browse/SLING-8604">SLING-8604</>
     */
    @Test
    public void testSetAclForPrincipalNameDifferentFromId() throws Exception {
        U.cleanupUser();
        try {
            Principal principal = new PrincipalImpl(U.id + "_principalName");
            assertTrue(U.adminSession instanceof JackrabbitSession);
            UserManager uMgr = ((JackrabbitSession) U.adminSession).getUserManager();
            uMgr.createUser(U.username, null, principal, null);
            U.adminSession.save();

            AclUtil.setAcl(U.adminSession, Collections.singletonList(principal.getName()), Collections.singletonList(PathUtils.ROOT_PATH), Collections.singletonList(Privilege.JCR_READ), false);

            assertIsContained(AccessControlUtils.getAccessControlList(U.adminSession, PathUtils.ROOT_PATH), principal.getName(), new String[] {Privilege.JCR_READ}, false);
        } finally {
            U.adminSession.refresh(false);
        }
    }

    /**
     * Test backwards compatibility of SLING-8604: test that the fallback mechanism still tries to
     * resolve the given 'principalName' to an authorizable by treating it as the identifier, if there exists no principal
     * for the given name (i.e ID is not equal to the principal name.
     *
     * @see <a href="https://issues.apache.org/jira/browse/SLING-8604">SLING-8604</>
     */
    @Test
    public void testSetAclUsingUserId() throws Exception {
        U.cleanupUser();
        try {
            Principal principal = new PrincipalImpl(U.id + "_principalName");
            assertTrue(U.adminSession instanceof JackrabbitSession);
            UserManager uMgr = ((JackrabbitSession) U.adminSession).getUserManager();
            uMgr.createUser(U.username, null, principal, null);
            U.adminSession.save();

            AclUtil.setAcl(U.adminSession, Collections.singletonList(U.username), Collections.singletonList(PathUtils.ROOT_PATH), Collections.singletonList(Privilege.JCR_READ), false);

            // assert that the entries is created for the principal name (and not the identifier)
            assertIsContained(AccessControlUtils.getAccessControlList(U.adminSession, PathUtils.ROOT_PATH), principal.getName(), new String[] {Privilege.JCR_READ}, false);
            // assert no entry has been created for the identifier (which cannot be resolved to a principal)
            assertIsNotContained(AccessControlUtils.getAccessControlList(U.adminSession, PathUtils.ROOT_PATH), U.username, new String[] {Privilege.JCR_READ}, false);
        } finally {
            U.adminSession.refresh(false);
        }
    }

    @Test
    public void testSetAclWithHomePath() throws Exception {
        UserManager uMgr = ((JackrabbitSession) U.adminSession).getUserManager();
        String userHomePath = uMgr.getAuthorizable(U.username).getPath();
        assertIsNotContained(AccessControlUtils.getAccessControlList(U.adminSession, userHomePath), U.username, new String[] {Privilege.JCR_READ}, true);

        List<String> paths = Collections.singletonList(":home:"+U.username+"#");
        AclUtil.setAcl(U.adminSession, Collections.singletonList(U.username), paths, Collections.singletonList(Privilege.JCR_READ), true);
        assertIsContained(AccessControlUtils.getAccessControlList(U.adminSession, userHomePath), U.username, new String[] {Privilege.JCR_READ}, true);
    }

    @Test
    public void testSetAclWithHomePathMultipleIds() throws Exception {
        UserManager uMgr = ((JackrabbitSession) U.adminSession).getUserManager();
        String userHomePath = uMgr.getAuthorizable(U.username).getPath();
        assertIsNotContained(AccessControlUtils.getAccessControlList(U.adminSession, userHomePath), U.username, new String[] {Privilege.JCR_READ}, true);

        Group gr = uMgr.createGroup("groupId");
        String groupHomePath = gr.getPath();
        assertIsNotContained(AccessControlUtils.getAccessControlList(U.adminSession, groupHomePath), U.username, new String[] {Privilege.JCR_READ}, true);

        List<String> paths = Collections.singletonList(":home:"+U.username+","+gr.getID()+"#");
        AclUtil.setAcl(U.adminSession, Collections.singletonList(U.username), paths, Collections.singletonList(Privilege.JCR_READ), true);

        assertIsContained(AccessControlUtils.getAccessControlList(U.adminSession, userHomePath), U.username, new String[] {Privilege.JCR_READ}, true);
        assertIsContained(AccessControlUtils.getAccessControlList(U.adminSession, groupHomePath), U.username, new String[] {Privilege.JCR_READ}, true);
    }

    @Test
    public void testSetAclWithHomePathMultiplePath() throws Exception {
        UserManager uMgr = ((JackrabbitSession) U.adminSession).getUserManager();
        String userHomePath = uMgr.getAuthorizable(U.username).getPath();

        List<String> paths = Arrays.asList(":home:" + U.username + "#", ":repository", PathUtils.ROOT_PATH);
        AclUtil.setAcl(U.adminSession, Collections.singletonList(U.username), paths, Collections.singletonList(Privilege.JCR_ALL), true);

        assertIsContained(AccessControlUtils.getAccessControlList(U.adminSession, userHomePath), U.username, new String[] {Privilege.JCR_ALL}, true);
        assertIsContained(AccessControlUtils.getAccessControlList(U.adminSession, null), U.username, new String[] {Privilege.JCR_ALL}, true);
        assertIsContained(AccessControlUtils.getAccessControlList(U.adminSession, PathUtils.ROOT_PATH), U.username, new String[] {Privilege.JCR_ALL}, true);
    }

    @Test
    public void testSetAclWithHomePathAndSubtree() throws Exception {
        UserManager uMgr = ((JackrabbitSession) U.adminSession).getUserManager();
        String userHomePath = U.adminSession.getNode(uMgr.getAuthorizable(U.username).getPath()).addNode("profiles").addNode("private").getPath();
        assertIsNotContained(AccessControlUtils.getAccessControlList(U.adminSession, userHomePath), U.username, new String[] {Privilege.JCR_READ}, true);

        Group gr = uMgr.createGroup("groupId");
        String groupHomePath = U.adminSession.getNode(gr.getPath()).addNode("profiles").addNode("private").getPath();
        assertIsNotContained(AccessControlUtils.getAccessControlList(U.adminSession, groupHomePath), U.username, new String[] {Privilege.JCR_READ}, true);

        List<String> paths = Collections.singletonList(":home:"+U.username+","+gr.getID()+"#/profiles/private");
        AclUtil.setAcl(U.adminSession, Collections.singletonList(U.username), paths, Collections.singletonList(Privilege.JCR_READ), true);

        assertIsContained(AccessControlUtils.getAccessControlList(U.adminSession, userHomePath), U.username, new String[] {Privilege.JCR_READ}, true);
        assertIsContained(AccessControlUtils.getAccessControlList(U.adminSession, groupHomePath), U.username, new String[] {Privilege.JCR_READ}, true);
    }

    @Test
    public void testSetAclWithHomePathAndMissingSubtree() throws Exception {
        UserManager uMgr = ((JackrabbitSession) U.adminSession).getUserManager();
        String userHomePath = uMgr.getAuthorizable(U.username).getPath() + "/profiles/private";
        assertFalse(U.adminSession.nodeExists(userHomePath));
        assertThrows(PathNotFoundException.class, () -> AccessControlUtils.getAccessControlList(U.adminSession, userHomePath));

        Group gr = uMgr.createGroup("groupId");
        String groupHomePath = U.adminSession.getNode(gr.getPath()).getPath() + "/profiles/private";
        assertFalse(U.adminSession.nodeExists(groupHomePath));

        assertThrows(PathNotFoundException.class, () -> AccessControlUtils.getAccessControlList(U.adminSession, groupHomePath));
    }

    /**
     * @param groupId the id of the group to create
     */
    protected void assertSetAclWithHomePath(String groupId) throws AccessDeniedException, UnsupportedRepositoryOperationException,
            RepositoryException, AuthorizableExistsException {
        UserManager uMgr = ((JackrabbitSession) U.adminSession).getUserManager();

        Group gr = uMgr.createGroup(groupId);
        String groupHomePath = gr.getPath();
        assertIsNotContained(AccessControlUtils.getAccessControlList(U.adminSession, groupHomePath), U.username, new String[] {Privilege.JCR_READ}, true);

        List<String> paths = Collections.singletonList(":home:"+gr.getID()+","+U.username+"#");
        AclUtil.setAcl(U.adminSession, Collections.singletonList(U.username), paths, Collections.singletonList(Privilege.JCR_READ), true);

        assertIsContained(AccessControlUtils.getAccessControlList(U.adminSession, groupHomePath), U.username, new String[] {Privilege.JCR_READ}, true);
    }

    @Test
    public void testSetAclWithHomePathIdWithHash() throws Exception {
        assertSetAclWithHomePath("g#roupId#");
    }

    @Test
    public void testSetAclWithHomePathIdWithColon() throws Exception {
        assertSetAclWithHomePath(":group:Id");
    }

    @Ignore("TODO: user/groupId containing , will fail ac setup")
    @Test
    public void testSetAclWithHomePathIdWithComma() throws Exception {
        assertSetAclWithHomePath(",group,Id,");
    }

    @Test(expected = IllegalStateException.class)
    public void testSetAclWithHomePathMissingTrailingHash() throws Exception {
        List<String> paths = Collections.singletonList(":home:"+U.username);
        AclUtil.setAcl(U.adminSession, Collections.singletonList(U.username), paths, Collections.singletonList(Privilege.JCR_READ), true);
    }

    @Test(expected = PathNotFoundException.class)
    public void testSetAclWithHomePathUnknownUser() throws Exception {
        List<String> paths = Collections.singletonList(":home:alice#");
        AclUtil.setAcl(U.adminSession, Collections.singletonList(U.username), paths, Collections.singletonList(Privilege.JCR_READ), true);
    }

    private void assertIsContained(JackrabbitAccessControlList acl, String username, String[] privilegeNames, boolean isAllow) throws RepositoryException {
        assertIsContained0(acl, username, privilegeNames, isAllow, true);
    }
    
    private void assertIsNotContained(JackrabbitAccessControlList acl, String username, String[] privilegeNames, boolean isAllow) throws RepositoryException {
        assertIsContained0(acl, username, privilegeNames, isAllow, false);
    }
    
    private void assertIsContained0(JackrabbitAccessControlList acl, String username, String[] privilegeNames, boolean isAllow, boolean contained) throws RepositoryException {
        AclUtil.LocalAccessControlEntry localAce = new AclUtil.LocalAccessControlEntry(principal(username), privileges(privilegeNames), isAllow);
        
        if ( contained ) {
            assertTrue("ACL does not contain an entry for " + localAce, AclUtil.contains(acl.getAccessControlEntries(), localAce));    
        } else {
            assertFalse("ACL contains an entry for " + localAce, AclUtil.contains(acl.getAccessControlEntries(), localAce));
        }
        
    }

    private Principal principal(String principalName) throws RepositoryException {
        return AccessControlUtils.getPrincipal(U.adminSession, principalName);
    }

    private Privilege[] privileges(String... privilegeNames) throws RepositoryException {
        return AccessControlUtils.privilegesFromNames(U.adminSession, privilegeNames);
    }
}