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

import org.apache.jackrabbit.api.security.JackrabbitAccessControlEntry;
import org.apache.jackrabbit.api.security.JackrabbitAccessControlList;
import org.apache.jackrabbit.api.security.JackrabbitAccessControlManager;
import org.apache.jackrabbit.api.security.JackrabbitAccessControlPolicy;
import org.apache.jackrabbit.api.security.authorization.PrincipalAccessControlList;
import org.apache.jackrabbit.api.security.principal.ItemBasedPrincipal;
import org.apache.jackrabbit.api.security.user.Authorizable;
import org.apache.jackrabbit.commons.jackrabbit.authorization.AccessControlUtils;
import org.apache.jackrabbit.oak.spi.security.principal.PrincipalImpl;
import org.apache.jackrabbit.util.Text;
import org.apache.sling.repoinit.parser.operations.AclLine;
import org.apache.sling.repoinit.parser.operations.RestrictionClause;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.PathNotFoundException;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.Value;
import javax.jcr.ValueFactory;
import javax.jcr.security.AccessControlEntry;
import javax.jcr.security.AccessControlException;
import javax.jcr.security.AccessControlManager;
import javax.jcr.security.AccessControlPolicy;
import javax.jcr.security.Privilege;
import java.security.Principal;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import static org.apache.sling.repoinit.parser.operations.AclLine.ID_DELIMINATOR;
import static org.apache.sling.repoinit.parser.operations.AclLine.PATH_HOME;
import static org.apache.sling.repoinit.parser.operations.AclLine.PATH_REPOSITORY;
import static org.apache.sling.repoinit.parser.operations.AclLine.PROP_PATHS;
import static org.apache.sling.repoinit.parser.operations.AclLine.PROP_PRIVILEGES;
import static org.apache.sling.repoinit.parser.operations.AclLine.SUBTREE_DELIMINATOR;

/** Utilities for ACL management */
public class AclUtil {

    private static final String PRINCIPAL_NOT_FOUND_PATTERN = "Principal not found: {0}";

    private AclUtil() {
        // private constructor to hide the implicit public one
    }

    private static final Logger LOG = LoggerFactory.getLogger(AclUtil.class);
    public static JackrabbitAccessControlManager getJACM(Session s) throws RepositoryException {
        final AccessControlManager acm = s.getAccessControlManager();
        checkState((acm instanceof JackrabbitAccessControlManager), "AccessControlManager is not a JackrabbitAccessControlManager: {0}", acm.getClass().getName());
        return (JackrabbitAccessControlManager) acm;
    }

    /**
     * Converts RestrictionClauses to structure consumable by
     * jackrabbit
     * @param list
     * @param jacl
     * @param s
     * @return
     * @throws RepositoryException
     */
    private static LocalRestrictions createLocalRestrictions(List<RestrictionClause> list, JackrabbitAccessControlList jacl, Session s) throws RepositoryException {
        Map<String,Value> restrictions = new HashMap<>();
        Map<String,Value[]> mvrestrictions = new HashMap<>();

        if(list != null && !list.isEmpty()){
            ValueFactory vf = s.getValueFactory();

           for(RestrictionClause rc : list){
               String restrictionName = rc.getName();
               int type = jacl.getRestrictionType(restrictionName);
               boolean isMvRestriction = jacl.isMultiValueRestriction(restrictionName);
               Value[] values = new Value[rc.getValues().size()];
               for(int i=0;i<values.length;i++) {
                   values[i] = vf.createValue(rc.getValues().get(i),type);
               }

               if("rep:glob".equals(restrictionName) && values.length == 0) {
                   // SLING-7280 - special case for rep:glob which supports an empty string
                   // to mean "no values"
                   restrictions.put(restrictionName, vf.createValue(""));
               } else if (isMvRestriction) {
                   mvrestrictions.put(restrictionName, values);
               } else {
                   checkState(values.length == 1, "Expected just one value for single valued restriction with name {0}", restrictionName);
                   restrictions.put(restrictionName, values[0]);
               }
           }
        }
        return new LocalRestrictions(restrictions,mvrestrictions);
    }


    public static void setAcl(Session session, List<String> principals, List<String> paths, List<String> privileges, boolean isAllow)
            throws RepositoryException {
        setAcl(session, principals, paths, privileges, isAllow, Collections.emptyList());
    }

    public static void setAcl(Session session, List<String> principals, List<String> paths, List<String> privileges, boolean isAllow, List<RestrictionClause> restrictionClauses)
            throws RepositoryException {
        for (String jcrPath : getJcrPaths(session, paths)) {
            if (jcrPath != null && !session.nodeExists(jcrPath)) {
                throw new PathNotFoundException("Cannot set ACL on non-existent path " + jcrPath);
            }
            setAcl(session, principals, jcrPath, privileges, isAllow, restrictionClauses);
        }
    }

    private static void setAcl(Session session, List<String> principals, String jcrPath, List<String> privileges, boolean isAllow, List<RestrictionClause> restrictionClauses)
            throws RepositoryException {

        AccessControlManager acMgr = session.getAccessControlManager();

        final String [] privArray = privileges.toArray(new String[privileges.size()]);
        final Privilege[] jcrPriv = AccessControlUtils.privilegesFromNames(acMgr, privArray);
        
        JackrabbitAccessControlList acl = getAccessControlList(acMgr, jcrPath, true);
        checkState(acl != null, "No JackrabbitAccessControlList available for path {0}", jcrPath);

        LocalRestrictions localRestrictions = createLocalRestrictions(restrictionClauses, acl, session);

        AccessControlEntry[] existingAces = acl.getAccessControlEntries();

        boolean changed = false;
        for (String name : principals) {
            Principal principal = AccessControlUtils.getPrincipal(session, name);
            if (principal == null) {
                // backwards compatibility: fallback to original code treating principal name as authorizable ID (see SLING-8604)
                final Authorizable authorizable = UserUtil.getAuthorizable(session, name);
                checkState(authorizable != null, "Authorizable not found: {0}", name);
                principal = authorizable.getPrincipal();
            }
            checkState(principal != null, PRINCIPAL_NOT_FOUND_PATTERN, name);
            LocalAccessControlEntry newAce = new LocalAccessControlEntry(principal, jcrPriv, isAllow, localRestrictions);
            if (contains(existingAces, newAce)) {
                LOG.info("Not adding {} to path {} since an equivalent access control entry already exists", newAce, jcrPath);
                continue;
            }
            acl.addEntry(newAce.principal, newAce.privileges, newAce.isAllow,
                    newAce.restrictions.getRestrictions(), newAce.restrictions.getMVRestrictions());
            changed = true;
        }
        if ( changed ) {
            acMgr.setPolicy(jcrPath, acl);
        }
    }

    public static void setRepositoryAcl(Session session, List<String> principals, List<String> privileges, boolean isAllow, List<RestrictionClause> restrictionClauses)
           throws RepositoryException {
        setAcl(session, principals, (String)null, privileges, isAllow, restrictionClauses);
    }

    /**
     * Remove resource-based access control setup for the principal with the given name.
     * 
     * @param session
     * @param principalName
     * @throws RepositoryException
     */
    public static void removePolicy(@NotNull Session session, @NotNull final String principalName) throws RepositoryException {
        Principal principal = AccessControlUtils.getPrincipal(session, principalName);
        if (principal == null) {
            LOG.info("Principal {} does not exist.", principalName);
            // using PrincipalImpl will prevent 'removePolicy' from failing with AccessControlException
            // in case import-behavior is configured to be ABORT.
            principal = new PrincipalImpl(principalName);
        }
        
        JackrabbitAccessControlManager acMgr = getJACM(session);
        for (JackrabbitAccessControlPolicy policy : acMgr.getPolicies(principal)) {
            // make sure not to remove the principal-based access control list but instead only drop
            // resource-based access control content for the given principal
            if (policy instanceof JackrabbitAccessControlList && !(policy instanceof PrincipalAccessControlList)) {
                acMgr.removePolicy(policy.getPath(), policy);
            }
        }
    }

    /**
     * Remove resource-based access control setup defined for the specified paths.
     * 
     * @param session
     * @param paths
     * @throws RepositoryException
     */
    public static void removePolicies(@NotNull Session session, @NotNull List<String> paths) throws RepositoryException {
        AccessControlManager acMgr = session.getAccessControlManager();
        for (String jcrPath : getJcrPaths(session, paths)) {
            if (!isValidPath(session, jcrPath)) {
                LOG.info("Cannot remove ACL; no node at {} ", jcrPath);
                continue;
            }
            LOG.info("Removing access control policy at {}", jcrPath);
            JackrabbitAccessControlList acl = getAccessControlList(acMgr, jcrPath, false);
            if (acl == null) {
                LOG.info("No ACL to remove at path {}", jcrPath);
            } else {
                acMgr.removePolicy(jcrPath, acl);
            }
        }
    }
    
    public static void removeEntries(@NotNull Session session, @NotNull List<String> principals, @NotNull List<String> paths) throws RepositoryException {
        Set<String> principalNames = new HashSet<>(principals);
        AccessControlManager acMgr = session.getAccessControlManager();
        for (String jcrPath : getJcrPaths(session, paths)) {
            if (!isValidPath(session, jcrPath)) {
                LOG.info("Cannot remove access control entries on non-existent path {}", jcrPath);
            } else {
                JackrabbitAccessControlList acl = getAccessControlList(acMgr, jcrPath, false);
                if (acl != null) {
                    boolean modified = false;
                    for (AccessControlEntry ace : acl.getAccessControlEntries()) {
                        if (principalNames.contains(ace.getPrincipal().getName())) {
                            acl.removeAccessControlEntry(ace);
                            modified = true;
                        }
                    }
                    if (modified) {
                        acMgr.setPolicy(jcrPath, acl);
                    }
                } else {
                    LOG.info("Cannot remove access control entries for principal(s) {}. No ACL at {}", principalNames, jcrPath);
                }
            }
        }
    }

    public static void removeEntries(@NotNull Session session, @NotNull List<String> principals, @NotNull List<String> paths, List<String> privileges, boolean isAllow, List<RestrictionClause> restrictionClauses) throws RepositoryException {
        Set<String> principalNames = new HashSet<>(principals);
        AccessControlManager acMgr = session.getAccessControlManager();
        for (String jcrPath : getJcrPaths(session, paths)) {
            if (!isValidPath(session, jcrPath)) {
                LOG.info("Cannot remove access control entries on non-existent path {}", jcrPath);
            } else {
                JackrabbitAccessControlList acl = getAccessControlList(acMgr, jcrPath, false);
                if (acl != null) {
                    boolean modified = false;

                    LocalRestrictions restr = createLocalRestrictions(restrictionClauses, acl, session);
                    Privilege[] privs = AccessControlUtils.privilegesFromNames(acMgr, privileges.toArray(new String[0]));
                    
                    for (AccessControlEntry ace : acl.getAccessControlEntries()) {
                        Principal principal = ace.getPrincipal();
                        if (!principalNames.contains(principal.getName())) {
                            continue;
                        }
                        LocalAccessControlEntry entry = new LocalAccessControlEntry(ace.getPrincipal(), privs, isAllow, restr);
                        if (entry.isEqual(ace)) {
                            acl.removeAccessControlEntry(ace);
                            modified = true;
                        }
                    }
                    if (modified) {
                        acMgr.setPolicy(jcrPath, acl);
                    } else {
                        LOG.info("No matching access control entry found to remove for principals {} at {}. Expected entry with isAllow={}, privileges={}, restrictions={}", principalNames, jcrPath, isAllow, privileges, restrictionClauses);
                    }
                } else {
                    LOG.info("Cannot remove access control entries for principal(s) {}. No ACL at {}", principalNames, jcrPath);
                }
            }
        }
    }

    public static void setPrincipalAcl(Session session, String principalName, Collection<AclLine> lines) throws RepositoryException {
        final JackrabbitAccessControlManager acMgr = getJACM(session);
        Principal principal = AccessControlUtils.getPrincipal(session, principalName);
        if (principal == null) {
            // due to transient nature of the repo-init the principal lookup may not succeed if completed through query
            // -> save transient changes and retry principal lookup
            session.save();
            principal = AccessControlUtils.getPrincipal(session, principalName);
            checkState(principal != null, PRINCIPAL_NOT_FOUND_PATTERN, principalName);
        }

        final PrincipalAccessControlList acl = getPrincipalAccessControlList(acMgr, principal, true);
        if (acl == null) {
            String principalDescription = principal.getName();
            // try to get path of principal in case it is backed by a JCR user/group
            if (principal instanceof ItemBasedPrincipal) {
                principalDescription += " (" + ((ItemBasedPrincipal) principal).getPath() + ")";
            }
            throw new IllegalStateException("No PrincipalAccessControlList available for principal '" + principalDescription + "'.");
        }
        boolean modified = false;
        for (AclLine line : lines) {
            AclLine.Action action = line.getAction();
            List<String> jcrPaths = getJcrPaths(session, line.getProperty(PROP_PATHS));
            if (action == AclLine.Action.DENY) {
                throw new AccessControlException("PrincipalAccessControlList doesn't support 'deny' entries.");
            } else if (action == AclLine.Action.REMOVE) {
                throw new IllegalArgumentException(AclLine.Action.REMOVE + " is not supported. Use 'remove principal acl' instead.");
            } else if (action == AclLine.Action.REMOVE_ALL) {
                if (removePrincipalEntries(acl, principalName, entry -> jcrPaths.contains(entry.getEffectivePath()))) {
                    modified = true;
                }
            } else if (action == AclLine.Action.ALLOW) {
                final Privilege[] privileges = AccessControlUtils.privilegesFromNames(acMgr, line.getProperty(PROP_PRIVILEGES).toArray(new String[0]));
                for (String effectivePath : jcrPaths) {
                    final LocalRestrictions restrictions = createLocalRestrictions(line.getRestrictions(), acl, session);
                    final boolean added = acl.addEntry(effectivePath, privileges, restrictions.getRestrictions(), restrictions.getMVRestrictions());
                    if (!added) {
                        LOG.info("Equivalent principal-based entry already exists for principal {} and effective path {} ", principalName, effectivePath);
                    } else {
                        modified = true;
                    }
                }
            } else {
                throw new IllegalArgumentException("Unknown action " + action);
            }
        }
        if (modified) {
            acMgr.setPolicy(acl.getPath(), acl);
        }
    }

    public static void removePrincipalEntries(Session session, String principalName, Collection<AclLine> lines) throws RepositoryException {
        final JackrabbitAccessControlManager acMgr = getJACM(session);
        Principal principal = AccessControlUtils.getPrincipal(session, principalName);
        if (principal == null) {
            // due to transient nature of the repo-init the principal lookup may not succeed if completed through query
            // -> save transient changes and retry principal lookup
            session.save();
            principal = AccessControlUtils.getPrincipal(session, principalName);
            checkState(principal != null, PRINCIPAL_NOT_FOUND_PATTERN, principalName);
        }

        final PrincipalAccessControlList acl = getPrincipalAccessControlList(acMgr, principal, true);
        boolean modified = false;
        for (AclLine line : lines) {
            List<String> jcrPaths = getJcrPaths(session, line.getProperty(PROP_PATHS));
            LocalRestrictions restr = createLocalRestrictions(line.getRestrictions(), acl, session);
            List<String> privNames = line.getProperty(PROP_PRIVILEGES);
            Privilege[] privs = AccessControlUtils.privilegesFromNames(acMgr, privNames.toArray(new String[0]));
            Predicate<PrincipalAccessControlList.Entry> predicate = entry -> {
                if (!jcrPaths.contains(entry.getEffectivePath())) {
                    return false;
                }
                LocalAccessControlEntry lace = new LocalAccessControlEntry(entry.getPrincipal(), privs, line.getAction()== AclLine.Action.ALLOW, restr);
                return lace.isEqual(entry);
            };
            if (removePrincipalEntries(acl, principalName, predicate)) {
                modified = true;
            } else {
                LOG.info("No matching access control entry found to remove for principal {} at {}. Expected entry with isAllow={}, privileges={}, restrictions={}", principalName, jcrPaths, line.getAction(), privNames, line.getRestrictions());
            }
        }
        if (modified) {
            acMgr.setPolicy(acl.getPath(), acl);
        }
    }

    /**
     * Remove principal-based access control setup for the principal with the given name. 
     *
     * @param session
     * @param principalName
     * @throws RepositoryException
     */
    public static void removePrincipalPolicy(@NotNull Session session, @NotNull String principalName) throws RepositoryException {
        Principal principal = AccessControlUtils.getPrincipal(session, principalName);
        if (principal == null) {
            LOG.info("Cannot remove principal-based ACL. Principal {} does not exist.", principalName);
            return;
        }

        JackrabbitAccessControlManager acMgr = getJACM(session);
        PrincipalAccessControlList acl = getPrincipalAccessControlList(acMgr, principal, false);
        if (acl == null) {
            LOG.info("Cannot remove principal-based ACL for principal {}. No such policy exists.", principalName);
        } else {
            acMgr.removePolicy(acl.getPath(), acl);
        }
    }
    
    private static boolean isValidPath(@NotNull Session session, @Nullable String jcrPath) throws RepositoryException {
        return jcrPath == null || session.nodeExists(jcrPath);
    }

    /**
     * 
     * @param acMgr the access control manager
     * @param principal the principal
     * @return the first available {@link PrincipalAccessControlList} bound to the given principal or {@code null} of <a href="https://jackrabbit.apache.org/oak/docs/security/authorization/principalbased.html">principal-based authorization</a> is not enabled for the given principal
     * @throws RepositoryException
     */
    @Nullable
    private static JackrabbitAccessControlList getAccessControlList(@NotNull AccessControlManager acMgr,
                                                                    @Nullable String path, boolean includeApplicable) throws RepositoryException {
        if (includeApplicable) {
            return AccessControlUtils.getAccessControlList(acMgr, path);
        } else {
            for (AccessControlPolicy policy : acMgr.getPolicies(path)) {
                if (policy instanceof JackrabbitAccessControlList) {
                    return (JackrabbitAccessControlList) policy;
                }
            }
            return null;
        }
    }

    @Nullable
    private static PrincipalAccessControlList getPrincipalAccessControlList(@NotNull JackrabbitAccessControlManager acMgr, 
                                                                            @NotNull Principal principal, boolean includeApplicable) throws RepositoryException {
        PrincipalAccessControlList acl = null;
        for (JackrabbitAccessControlPolicy policy : acMgr.getPolicies(principal)) {
            if (policy instanceof PrincipalAccessControlList) {
                acl = (PrincipalAccessControlList) policy;
                break;
            }
        }
        if (acl == null && includeApplicable) {
            for (JackrabbitAccessControlPolicy policy : acMgr.getApplicablePolicies(principal)) {
                if (policy instanceof PrincipalAccessControlList) {
                    acl = (PrincipalAccessControlList) policy;
                    break;
                }
            }
        }
        return acl;
    }

    private static boolean removePrincipalEntries(@Nullable PrincipalAccessControlList acl, @NotNull String principalName, @NotNull Predicate<PrincipalAccessControlList.Entry> predicate) throws RepositoryException {
        boolean modified = false;
        if (acl == null) {
            LOG.info("Cannot remove entries. No principal-based ACL for {}", principalName);
        } else {
            for (AccessControlEntry ace : acl.getAccessControlEntries()) {
                if (ace instanceof PrincipalAccessControlList.Entry) {
                    PrincipalAccessControlList.Entry entry = (PrincipalAccessControlList.Entry) ace;
                    if (predicate.test(entry)) {
                        acl.removeAccessControlEntry(ace);
                        modified = true;
                    }
                }
            }
        }
        return modified;
    }

    @NotNull
    private static List<String> getJcrPaths(@NotNull Session session, @NotNull List<String> paths) throws RepositoryException {
        List<String> jcrPaths = new ArrayList<>(paths.size());
        for (String path : paths) {
            if (PATH_REPOSITORY.equals(path) || path == null || path.isEmpty()) {
                jcrPaths.add(null);
            } else if (path.startsWith(PATH_HOME)) {
                int lastHashIndex = path.lastIndexOf(SUBTREE_DELIMINATOR);
                checkState(lastHashIndex > -1, "Invalid format of home path: # deliminator expected.");
                String subTreePath = path.substring(lastHashIndex+1);
                for (String aPath : getAuthorizablePaths(session, path.substring(PATH_HOME.length(), lastHashIndex))) {
                    jcrPaths.add(aPath + subTreePath);
                }
            } else {
                jcrPaths.add(path);
            }
        }
        return jcrPaths;
    }

    @NotNull
    private static Iterable<String> getAuthorizablePaths(@NotNull Session session, @NotNull String ids) throws RepositoryException {
        List<String> paths = new ArrayList<>();
        for (String id : Text.explode(ids, ID_DELIMINATOR)) {
            Authorizable a = UserUtil.getAuthorizable(session, id);
            if (a == null) {
                throw new PathNotFoundException("Cannot resolve path of user/group with id '" + id + "'.");
            }
            paths.add(a.getPath());
        }
        return paths;
    }

    // visible for testing
    static boolean contains(AccessControlEntry[] existingAces, LocalAccessControlEntry newAce) throws RepositoryException {
        for (int i = 0 ; i < existingAces.length; i++) {
            JackrabbitAccessControlEntry existingEntry = (JackrabbitAccessControlEntry) existingAces[i];
            if (LOG.isDebugEnabled()) {
                LOG.debug("Comparing {} with {}", newAce, toString(existingEntry));
            }
            if (newAce.isContainedIn(existingEntry)) {
                return true;
            }
        }
        return false;
    }

    private static String toString(JackrabbitAccessControlEntry entry) throws RepositoryException {
        return "[" + entry.getClass().getSimpleName() + "# principal: "
                + "" + entry.getPrincipal() + ", privileges: " + Arrays.toString(entry.getPrivileges()) +
                ", isAllow: " + entry.isAllow() + ", restrictionNames: " + entry.getRestrictionNames()  + "]";
    }

    private static void checkState(boolean expression, String msgPattern, Object... args) {
        if (!expression) {
            if (args != null) {
                throw new IllegalStateException(msgPattern);
            } else {
                throw new IllegalStateException(MessageFormat.format(msgPattern, args));
            }
        }
    }

    /** Compare value arrays a and b, by set comparison.
     *  @param a first value array
     *  @param b second value array
     *  @return true if both value arrays contain the same elements,
     *      in whatever order.
     */
    static boolean compareValues(Value[] a, Value[] b) {
        if (a == null || b == null) {
            return false;
        }
        if(a.length != b.length){
            return false;
        }
        Set<Value> first = new HashSet<>(Arrays.asList(a));
        Set<Value> second = new HashSet<>(Arrays.asList(b));
        return first.equals(second);
    }

    /**
     * Helper class which allows easy comparison of a local (proposed) access control entry with an existing one
     */
    static class LocalAccessControlEntry {

        private final Principal principal;
        private final Privilege[] privileges;
        private final boolean isAllow;
        private final LocalRestrictions restrictions;
        LocalAccessControlEntry(Principal principal, Privilege[] privileges, boolean isAllow) {
            this(principal, privileges, isAllow, null);
        }

        LocalAccessControlEntry(Principal principal, Privilege[] privileges,
                                boolean isAllow, LocalRestrictions restrictions) {
            this.principal = principal;
            this.privileges = privileges;
            this.isAllow = isAllow;
            this.restrictions = restrictions != null ? restrictions : new LocalRestrictions();
        }

        public boolean isContainedIn(JackrabbitAccessControlEntry other) throws RepositoryException {
            return other.getPrincipal().equals(principal) &&
                    contains(other.getPrivileges(), privileges) &&
                    other.isAllow() == isAllow &&
                    sameRestrictions(other);
        }

        public boolean isEqual(AccessControlEntry other) {
            if (!(other instanceof JackrabbitAccessControlEntry)) {
                return false;
            }
            try {
                JackrabbitAccessControlEntry otherAce = (JackrabbitAccessControlEntry) other;
                return other.getPrincipal().equals(principal) &&
                        equalPrivileges(other.getPrivileges(), privileges) &&
                        otherAce.isAllow() == isAllow &&
                        sameRestrictions(otherAce);
            } catch (RepositoryException e) {
                throw new IllegalStateException("Cannot verify equivalence of access control entries", e);
            }
        }
        
        private Set<Privilege> expandPrivileges(Privilege[] privileges){
            Set<Privilege> expandedSet = new HashSet<>();

            if(privileges != null){
                for(Privilege privilege : privileges){
                    if(privilege.isAggregate()){
                        expandedSet.addAll(Arrays.asList(privilege.getAggregatePrivileges()));
                    } else {
                        expandedSet.add(privilege);
                    }
                }
            }

            return expandedSet;
        }

        /**
         * compares if restrictions present in jackrabbit access control entry
         * is same as specified restrictions in repo init
         * @param jace
         * @return
         * @throws RepositoryException
         */
        private boolean sameRestrictions(JackrabbitAccessControlEntry jace) throws RepositoryException {
            // total (multivalue and simple)  number of restrictions should be same
            if(jace.getRestrictionNames().length == (restrictions.size())){
                for(String rn : jace.getRestrictionNames()){
                    Value[] oldValues = jace.getRestrictions(rn);
                    Value[] newValues = restrictions.getRestrictions().get(rn) != null
                                        ? new Value[]{restrictions.getRestrictions().get(rn)}
                                        : restrictions.getMVRestrictions().get(rn);
                    if((newValues == null || newValues.length == 0) && (oldValues == null || oldValues.length == 0)){
                        continue;
                    }

                    if(!compareValues(newValues, oldValues)) {
                        return false;
                    }
                }
                return true;
            }
            return false;
        }

        private boolean contains(Privilege[] first, Privilege[] second) {
            // we need to ensure that the privilege order is not taken into account, so we use sets
            Set<Privilege> set1 = expandPrivileges(first);

            Set<Privilege> set2 = expandPrivileges(second);

            return set1.containsAll(set2);
        }

        private boolean equalPrivileges(Privilege[] first, Privilege[] second) {
            Set<Privilege> set1 = expandPrivileges(first);
            Set<Privilege> set2 = expandPrivileges(second);
            return set1.equals(set2);
        }

        @Override
        public String toString() {
            return "[" + getClass().getSimpleName() + "# principal " + principal+ ", privileges: " + Arrays.toString(privileges) + ", isAllow : " + isAllow + "]";
        }
    }

    /**
     * Helper class to store both restrictions and multi value restrictions
     * in ready to consume structure expected by jackrabbit
     */
    private static class LocalRestrictions {
        private Map<String,Value> restrictions;
        private Map<String,Value[]> mvRestrictions;
        public LocalRestrictions(){
            restrictions = new HashMap<>();
            mvRestrictions = new HashMap<>();
        }
        public LocalRestrictions(Map<String,Value> restrictions,Map<String,Value[]> mvRestrictions){
            this.restrictions = restrictions != null ? restrictions : new HashMap<>();
            this.mvRestrictions = mvRestrictions != null ? mvRestrictions : new HashMap<>();
        }

        public Map<String,Value> getRestrictions(){
            return this.restrictions;
        }

        public Map<String,Value[]> getMVRestrictions(){
            return this.mvRestrictions;
        }

        public int size(){
            return this.restrictions.size() + this.mvRestrictions.size();
        }
    }
}
