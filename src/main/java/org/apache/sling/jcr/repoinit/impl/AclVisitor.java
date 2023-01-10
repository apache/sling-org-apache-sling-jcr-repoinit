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

import static org.apache.sling.repoinit.parser.operations.AclLine.PROP_PATHS;
import static org.apache.sling.repoinit.parser.operations.AclLine.PROP_PRINCIPALS;
import static org.apache.sling.repoinit.parser.operations.AclLine.PROP_PRIVILEGES;

import java.util.Collections;
import java.util.List;

import javax.jcr.RepositoryException;
import javax.jcr.Session;

import org.apache.sling.repoinit.parser.operations.AclLine;
import org.apache.sling.repoinit.parser.operations.DeleteAclPrincipalBased;
import org.apache.sling.repoinit.parser.operations.DeleteAclPrincipals;
import org.apache.sling.repoinit.parser.operations.EnsureAclPrincipalBased;
import org.apache.sling.repoinit.parser.operations.DeleteAclPaths;
import org.apache.sling.repoinit.parser.operations.RemoveAcePaths;
import org.apache.sling.repoinit.parser.operations.RemoveAcePrincipalBased;
import org.apache.sling.repoinit.parser.operations.RemoveAcePrincipals;
import org.apache.sling.repoinit.parser.operations.RestrictionClause;
import org.apache.sling.repoinit.parser.operations.SetAclPaths;
import org.apache.sling.repoinit.parser.operations.SetAclPrincipalBased;
import org.apache.sling.repoinit.parser.operations.SetAclPrincipals;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * OperationVisitor which processes only operations related to ACLs.
 * Having several such specialized visitors
 * makes it easy to control the execution order.
 */
class AclVisitor extends DoNothingVisitor {

    private static final Logger slog = LoggerFactory.getLogger(AclVisitor.class);
    
    /**
     * Create a visitor using the supplied JCR Session.
     *
     * @param s must have sufficient rights to create users
     *          and set ACLs.
     */
    public AclVisitor(Session s) {
        super(s);
    }

    private List<String> require(AclLine line, String propertyName) {
        final List<String> result = line.getProperty(propertyName);
        if (result == null) {
            throw new IllegalStateException("Missing property " + propertyName + " on " + line);
        }
        return result;
    }

    private void setAcl(AclLine line, Session s, List<String> principals, List<String> paths, List<String> privileges, AclLine.Action action) {
        try {
            if (action == AclLine.Action.REMOVE) {
                report("remove not supported. use 'remove acl' instead.");
            } else if (action == AclLine.Action.REMOVE_ALL) {
                AclUtil.removeEntries(s, principals, paths);
            } else {
                final boolean isAllow = line.getAction().equals(AclLine.Action.ALLOW);
                log.info("Adding ACL '{}' entry '{}' for {} on {}", isAllow ? "allow" : "deny", privileges, principals, paths);
                List<RestrictionClause> restrictions = line.getRestrictions();
                AclUtil.setAcl(s, principals, paths, privileges, isAllow, restrictions);
            }
        } catch (Exception e) {
            report(e,"Failed to set ACL (" + e.toString() + ") " + line);
        }
    }

    private void setRepositoryAcl(AclLine line, Session s, List<String> principals, List<String> privileges, AclLine.Action action) {
        try {
            if (action == AclLine.Action.REMOVE) {
                report("remove not supported. use 'remove acl' instead.");
            } else if (action == AclLine.Action.REMOVE_ALL) {
                AclUtil.removeEntries(s, principals, Collections.singletonList(null));
            } else {
                final boolean isAllow = line.getAction().equals(AclLine.Action.ALLOW);
                log.info("Adding repository level ACL '{}' entry '{}' for {}", isAllow ? "allow" : "deny", privileges, principals);
                List<RestrictionClause> restrictions = line.getRestrictions();
                AclUtil.setRepositoryAcl(s, principals, privileges, isAllow, restrictions);
            }
        } catch (Exception e) {
            report(e, "Failed to set repository level ACL (" + e.toString() + ") " + line);
        }
    }

    
    @Override
    public void visitSetAclPrincipal(SetAclPrincipals s) {
        final List<String> principals = s.getPrincipals();
        for (AclLine line : s.getLines()) {
            final List<String> paths = line.getProperty(PROP_PATHS);
            if (paths != null && !paths.isEmpty()) {
                setAcl(line, session, principals, paths, require(line, PROP_PRIVILEGES), line.getAction());
            } else {
                setRepositoryAcl(line, session, principals, require(line, PROP_PRIVILEGES), line.getAction());
            }
        }
    }

    @Override
    public void visitSetAclPaths(SetAclPaths s) {
        final List<String> paths = s.getPaths();
        for (AclLine line : s.getLines()) {
            setAcl(line, session, require(line, PROP_PRINCIPALS), paths, require(line, PROP_PRIVILEGES), line.getAction());
        }
    }

    @Override
    public void visitSetAclPrincipalBased(SetAclPrincipalBased s) {
        for (String principalName : s.getPrincipals()) {
            try {
                log.info("Adding principal-based access control entry for {}", principalName);
                AclUtil.setPrincipalAcl(session, principalName, s.getLines(), false);
            } catch (Exception e) {
                report(e, "Failed to set principal-based ACL (" + e.getMessage() + ")");
            }
        }
    }

    public void visitEnsureAclPrincipalBased(EnsureAclPrincipalBased s) {
        for (String principalName : s.getPrincipals()) {
            try {
                log.info("Enforcing principal-based access control entry for {}", principalName);
                AclUtil.setPrincipalAcl(session, principalName, s.getLines(), true);
            } catch (Exception e) {
                report(e, "Failed to set principal-based ACL (" + e.getMessage() + ")");
            }
        }
    }

    @Override
    public void visitRemoveAcePrincipal(RemoveAcePrincipals s) {
        final List<String> principals = s.getPrincipals();
        for (AclLine line : s.getLines()) {
            final List<String> paths = line.getProperty(PROP_PATHS);
            try {
                AclUtil.removeEntries(session, principals, paths, require(line, PROP_PRIVILEGES), line.getAction() == AclLine.Action.ALLOW, line.getRestrictions());
            } catch (Exception e) {
                report(e,"Failed to remove access control entries (" + e.toString() + ") " + line);
            }
        }
    }

    @Override
    public void visitRemoveAcePaths(RemoveAcePaths s) {
        final List<String> paths = s.getPaths();
        for (AclLine line : s.getLines()) {
            try {
                AclUtil.removeEntries(session, require(line, PROP_PRINCIPALS), paths, require(line, PROP_PRIVILEGES), line.getAction() == AclLine.Action.ALLOW, line.getRestrictions());
            } catch (Exception e) {
                report(e,"Failed to remove access control entries (" + e.toString() + ") " + line);
            }
        }
    }

    @Override
    public void visitRemoveAcePrincipalBased(RemoveAcePrincipalBased s) {
        for (String principalName : s.getPrincipals()) {
            try {
                log.info("Removing principal-based access control entries for {}", principalName);
                AclUtil.removePrincipalEntries(session, principalName, s.getLines());
            } catch (Exception e) {
                report(e, "Failed to remove principal-based access control entries (" + e.getMessage() + ")");
            }
        }
    }

    @Override
    public void visitDeleteAclPrincipals(DeleteAclPrincipals s) {
        for (String principalName : s.getPrincipals()) {
            try {
                log.info("Removing access control policy for {}", principalName);
                AclUtil.removePolicy(session, principalName);
            } catch (RepositoryException e) {
                report(e, "Failed to remove ACL (" + e.getMessage() + ")");
            }
        }
    }

    @Override
    public void visitDeleteAclPaths(DeleteAclPaths s) {
        try {
            AclUtil.removePolicies(session, s.getPaths());
        } catch (RepositoryException e) {
            report(e, "Failed to remove ACL (" + e.getMessage() + ")");
        }
    }

    @Override
    public void visitDeleteAclPrincipalBased(DeleteAclPrincipalBased s) {
        for (String principalName : s.getPrincipals()) {
            try {
                log.info("Removing principal-based access control policy for {}", principalName);
                AclUtil.removePrincipalPolicy(session, principalName);
            } catch (RepositoryException e) {
                report(e, "Failed to remove principal-based ACL (" + e.getMessage() + ")");
            }
        }
    }
}
