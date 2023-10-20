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

import static org.apache.sling.repoinit.parser.operations.AclLine.Action.ALLOW;
import static org.apache.sling.repoinit.parser.operations.AclLine.PROP_PATHS;
import static org.apache.sling.repoinit.parser.operations.AclLine.PROP_PRINCIPALS;
import static org.apache.sling.repoinit.parser.operations.AclLine.PROP_PRIVILEGES;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

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

    private static final Logger LOG = LoggerFactory.getLogger(AclVisitor.class);
    
    /**
     * Create a visitor using the supplied JCR Session.
     *
     * @param s must have sufficient rights to create users
     *          and set ACLs.
     */
    public AclVisitor(Session s) {
        super(s);
    }

    @Override
    public void visitSetAclPrincipal(SetAclPrincipals s) {
        final List<String> principals = s.getPrincipals();
        for (AclLine line : s.getLines()) {
            handleAcLine(line, Instruction.SET, principals, line.getProperty(PROP_PATHS), s.getOptions());
        }
    }

    @Override
    public void visitSetAclPaths(SetAclPaths s) {
        final List<String> paths = s.getPaths();
        for (AclLine line : s.getLines()) {
            handleAcLine(line, Instruction.SET, line.getProperty(PROP_PRINCIPALS), paths, s.getOptions());
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

    @Override
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
            handleAcLine(line, Instruction.REMOVE, principals, line.getProperty(PROP_PATHS), s.getOptions());
        }
    }

    @Override
    public void visitRemoveAcePaths(RemoveAcePaths s) {
        final List<String> paths = s.getPaths();
        for (AclLine line : s.getLines()) {
            handleAcLine(line, Instruction.REMOVE, line.getProperty(PROP_PRINCIPALS), paths, s.getOptions());
        }
    }

    private void handleAcLine(AclLine line, Instruction instruction, List<String> principals, List<String> paths, List<String> options) {
        final AclLine.Action action = line.getAction();
        final List<String> privileges = line.getProperty(PROP_PRIVILEGES);
        switch (action) {
            case REMOVE:
                report("remove not supported. use 'remove acl' instead.");
                break;
            case REMOVE_ALL:
                try {
                    AclUtil.removeEntries(session, principals, paths);
                } catch (Exception e) {
                    report(e,"Failed to remove access control entries (" + e + ") " + line);
                }
                break;
            case ALLOW:
            case DENY:
                // empty paths indicates a repository ACL, which is also encoded with the path ":repository"
                final List<String> fixedPaths = paths.isEmpty() ? Collections.singletonList(AclLine.PATH_REPOSITORY) : paths;
                instruction.execute(session, action, principals, fixedPaths, privileges, line.getRestrictions(), options, line::toString);
                break;
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

    enum Instruction {
        SET {
            @Override
            public void execute(Session session, AclLine.Action action, List<String> principals, List<String> paths, List<String> privileges,
                                List<RestrictionClause> restrictions, List<String> options, Supplier<String> lineString) {
                try {
                    LOG.info("Adding ACL '{}' entry '{}' for {} on {}",
                            action.name().toLowerCase(Locale.ROOT), privileges, principals, paths);
                    AclUtil.setAcl(session, principals, paths, privileges, action == ALLOW, restrictions, options);
                } catch (Exception e) {
                    report(e,"Failed to set ACL (" + e + ") " + lineString.get());
                }
            }
        },
        REMOVE {
            @Override
            public void execute(Session session, AclLine.Action action, List<String> principals, List<String> paths, List<String> privileges,
                                List<RestrictionClause> restrictions, List<String> options, Supplier<String> lineString) {
                try {
                    LOG.info("Removing ACL '{}' entry '{}' for {} on {}",
                            action.name().toLowerCase(Locale.ROOT), privileges, principals, paths);
                    AclUtil.removeEntries(session, principals, paths, privileges, action == ALLOW, restrictions);
                } catch (Exception e) {
                    report(e,"Failed to remove access control entries (" + e + ") " + lineString.get());
                }
            }
        };

        public abstract void execute(Session session, AclLine.Action action, List<String> principals, List<String> paths, List<String> privileges,
                                     List<RestrictionClause> restrictions, List<String> options, Supplier<String> lineString);
    }
}
