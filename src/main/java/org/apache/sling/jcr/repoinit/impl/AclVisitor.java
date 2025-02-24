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
package org.apache.sling.jcr.repoinit.impl;

import javax.jcr.RepositoryException;
import javax.jcr.Session;

import java.util.Collections;
import java.util.List;

import org.apache.sling.repoinit.parser.operations.AclLine;
import org.apache.sling.repoinit.parser.operations.DeleteAclPaths;
import org.apache.sling.repoinit.parser.operations.DeleteAclPrincipalBased;
import org.apache.sling.repoinit.parser.operations.DeleteAclPrincipals;
import org.apache.sling.repoinit.parser.operations.EnsureAclPrincipalBased;
import org.apache.sling.repoinit.parser.operations.RemoveAcePaths;
import org.apache.sling.repoinit.parser.operations.RemoveAcePrincipalBased;
import org.apache.sling.repoinit.parser.operations.RemoveAcePrincipals;
import org.apache.sling.repoinit.parser.operations.SetAclPaths;
import org.apache.sling.repoinit.parser.operations.SetAclPrincipalBased;
import org.apache.sling.repoinit.parser.operations.SetAclPrincipals;

import static org.apache.sling.repoinit.parser.operations.AclLine.PROP_PATHS;
import static org.apache.sling.repoinit.parser.operations.AclLine.PROP_PRINCIPALS;
import static org.apache.sling.repoinit.parser.operations.AclLine.PROP_PRIVILEGES;

/**
 * OperationVisitor which processes only operations related to ACLs.
 * Having several such specialized visitors
 * makes it easy to control the execution order.
 */
class AclVisitor extends DoNothingVisitor {

    /**
     * ACLOptions value that allows the check for the principals' existence to be skipped,
     * relying on the JCR implementation's behaviour in this respect. With Jackrabbit Oak,
     * depending on its configuration, this allows setting ACLs even if the referenced
     * principal does not (yet) exist.
     */
    public static final String OPTION_IGNORE_MISSING_PRINCIPAL = "ignoreMissingPrincipal";

    private final SessionContext context;

    private enum Instruction {
        SET,
        REMOVE
    }

    /**
     * Create a visitor using the supplied JCR Session.
     *
     * @param s must have sufficient rights to create users
     *          and set ACLs.
     */
    public AclVisitor(Session s) {
        super(s);
        context = new SessionContext(s);
    }

    private void handleAclLine(
            AclLine line, Instruction instruction, List<String> principals, List<String> paths, List<String> options)
            throws RepositoryException {
        final AclLine.Action action = line.getAction();
        if (action == AclLine.Action.REMOVE) {
            report("remove not supported. use 'remove acl' instead.");
        } else if (action == AclLine.Action.REMOVE_ALL) {
            AclUtil.removeEntries(context, principals, paths);
        } else {
            final boolean isAllow = action == AclLine.Action.ALLOW;
            final String actionName = isAllow ? "allow" : "deny";
            final List<String> privileges = line.getProperty(PROP_PRIVILEGES);
            if (instruction == Instruction.SET) {
                log.info("Adding ACL '{}' entry '{}' for {} on {}", actionName, privileges, principals, paths);
                AclUtil.setAcl(context, principals, paths, privileges, isAllow, line.getRestrictions(), options);
            } else if (instruction == Instruction.REMOVE) {
                log.info("Removing ACL '{}' entry '{}' for {} on {}", actionName, privileges, principals, paths);
                AclUtil.removeEntries(context, principals, paths, privileges, isAllow, line.getRestrictions());
            }
        }
    }

    @Override
    public void visitSetAclPrincipal(SetAclPrincipals s) {
        final List<String> principals = s.getPrincipals();
        for (AclLine line : s.getLines()) {
            List<String> paths = line.getProperty(PROP_PATHS);
            // empty paths indicates a repository ACL ...
            final boolean isRepositoryAcl = paths.isEmpty();
            // ... which needs to be represented with the path ":repository"
            paths = isRepositoryAcl ? Collections.singletonList(AclLine.PATH_REPOSITORY) : paths;
            try {
                handleAclLine(line, Instruction.SET, principals, paths, s.getOptions());
            } catch (Exception e) {
                if (isRepositoryAcl) {
                    report(e, "Failed to set repository level ACL (" + e + ") " + line);
                } else {
                    report(e, "Failed to set ACL (" + e + ") " + line);
                }
            }
        }
    }

    @Override
    public void visitSetAclPaths(SetAclPaths s) {
        final List<String> paths = s.getPaths();
        for (AclLine line : s.getLines()) {
            try {
                handleAclLine(line, Instruction.SET, line.getProperty(PROP_PRINCIPALS), paths, s.getOptions());
            } catch (Exception e) {
                report(e, "Failed to set ACL (" + e + ") " + line);
            }
        }
    }

    @Override
    public void visitSetAclPrincipalBased(SetAclPrincipalBased s) {
        for (String principalName : s.getPrincipals()) {
            try {
                log.info("Adding principal-based access control entry for {}", principalName);
                AclUtil.setPrincipalAcl(context, principalName, s.getLines(), false);
            } catch (Exception e) {
                report(e, "Failed to set principal-based ACL (" + e.getMessage() + ")");
            }
        }
    }

    @Override
    public void visitEnsureAclPrincipalBased(EnsureAclPrincipalBased s) {
        for (String principalName : s.getPrincipals()) {
            try {
                log.info("Enforcing principal-based access control entry for {}", principalName);
                AclUtil.setPrincipalAcl(context, principalName, s.getLines(), true);
            } catch (Exception e) {
                report(e, "Failed to set principal-based ACL (" + e.getMessage() + ")");
            }
        }
    }

    @Override
    public void visitRemoveAcePrincipal(RemoveAcePrincipals s) {
        final List<String> principals = s.getPrincipals();
        for (AclLine line : s.getLines()) {
            try {
                handleAclLine(line, Instruction.REMOVE, principals, line.getProperty(PROP_PATHS), s.getOptions());
            } catch (Exception e) {
                report(e, "Failed to remove access control entries (" + e.toString() + ") " + line);
            }
        }
    }

    @Override
    public void visitRemoveAcePaths(RemoveAcePaths s) {
        final List<String> paths = s.getPaths();
        for (AclLine line : s.getLines()) {
            try {
                handleAclLine(line, Instruction.REMOVE, line.getProperty(PROP_PRINCIPALS), paths, s.getOptions());
            } catch (Exception e) {
                report(e, "Failed to remove access control entries (" + e.toString() + ") " + line);
            }
        }
    }

    @Override
    public void visitRemoveAcePrincipalBased(RemoveAcePrincipalBased s) {
        for (String principalName : s.getPrincipals()) {
            try {
                log.info("Removing principal-based access control entries for {}", principalName);
                AclUtil.removePrincipalEntries(context, principalName, s.getLines());
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
                AclUtil.removePolicy(context, principalName);
            } catch (RepositoryException e) {
                report(e, "Failed to remove ACL (" + e.getMessage() + ")");
            }
        }
    }

    @Override
    public void visitDeleteAclPaths(DeleteAclPaths s) {
        try {
            AclUtil.removePolicies(context, s.getPaths());
        } catch (RepositoryException e) {
            report(e, "Failed to remove ACL (" + e.getMessage() + ")");
        }
    }

    @Override
    public void visitDeleteAclPrincipalBased(DeleteAclPrincipalBased s) {
        for (String principalName : s.getPrincipals()) {
            try {
                log.info("Removing principal-based access control policy for {}", principalName);
                AclUtil.removePrincipalPolicy(context, principalName);
            } catch (RepositoryException e) {
                report(e, "Failed to remove principal-based ACL (" + e.getMessage() + ")");
            }
        }
    }
}
