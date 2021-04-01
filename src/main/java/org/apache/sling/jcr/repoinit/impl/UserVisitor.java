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

import org.apache.jackrabbit.api.security.user.Authorizable;
import org.apache.jackrabbit.api.security.user.Group;
import org.apache.jackrabbit.api.security.user.User;
import org.apache.jackrabbit.api.security.user.UserManager;
import org.apache.sling.repoinit.parser.operations.CreateGroup;
import org.apache.sling.repoinit.parser.operations.CreateServiceUser;
import org.apache.sling.repoinit.parser.operations.CreateUser;
import org.apache.sling.repoinit.parser.operations.DeleteGroup;
import org.apache.sling.repoinit.parser.operations.DeleteServiceUser;
import org.apache.sling.repoinit.parser.operations.DeleteUser;
import org.apache.sling.repoinit.parser.operations.DisableServiceUser;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.jcr.RepositoryException;
import javax.jcr.Session;

import static org.apache.sling.jcr.repoinit.impl.UserUtil.getPath;
import static org.apache.sling.jcr.repoinit.impl.UserUtil.getUserManager;

/**
 * OperationVisitor which processes only operations related to service users and
 * ACLs. Having several such specialized visitors makes it easy to control the
 * execution order.
 */
class UserVisitor extends DoNothingVisitor {

    /**
     * Create a visitor using the supplied JCR Session.
     * 
     * @param s must have sufficient rights to create users and set ACLs.
     */
    public UserVisitor(Session s) {
        super(s);
    }

    @Override
    public void visitCreateServiceUser(CreateServiceUser s) {
        final String username = s.getUsername();
        try {
            UserManager userManager = getUserManager(session);
            User user = userManager.getAuthorizable(username, User.class);
            checkUserType(username, user, true);
            if (user == null || (s.isForcedPath() && needsRecreate(username, user, s.getPath(), "Service user"))) {
                log.info("Creating service user {}", username);
                userManager.createSystemUser(username, s.getPath());
            }
        } catch (Exception e) {
            report(e, "Unable to create service user [" + username + "]:" + e);
        }
    }

    @Override
    public void visitDeleteServiceUser(DeleteServiceUser s) {
        final String username = s.getUsername();
        log.info("Deleting service user {}", username);
        try {
            UserUtil.deleteAuthorizable(session, username);
        } catch (Exception e) {
            report(e, "Unable to delete service user [" + username + "]:" + e);
        }
    }

    @Override
    public void visitCreateGroup(CreateGroup g) {
        final String groupname = g.getGroupname();
        try {
            UserManager userManager = getUserManager(session);
            Group group = userManager.getAuthorizable(groupname, Group.class);
            String intermediatePath = g.getPath();
            if (group == null || (g.isForcedPath() && needsRecreate(groupname, group, intermediatePath, "Group"))) {
                log.info("Creating group {}", groupname);
                if (intermediatePath == null) {
                    userManager.createGroup(groupname);
                } else {
                    userManager.createGroup(() -> groupname, intermediatePath);
                }
            }
        } catch (Exception e) {
            report(e, "Unable to create group [" + groupname + "]:" + e);
        }
    }

    @Override
    public void visitDeleteGroup(DeleteGroup g) {
        final String groupname = g.getGroupname();
        log.info("Deleting group {}", groupname);
        try {
            if (!UserUtil.deleteAuthorizable(session, groupname)) {
                log.debug("Group {} doesn't exist - assuming delete to be a noop.", groupname);
            }
        } catch (Exception e) {
            report(e, "Unable to delete group [" + groupname + "]:" + e);
        }
    }

    @Override
    public void visitCreateUser(CreateUser u) {
        final String username = u.getUsername();
        try {
            UserManager userManager = getUserManager(session);
            User user = userManager.getAuthorizable(username, User.class);
            checkUserType(username, user, false);
            if (user == null || (u.isForcedPath() && needsRecreate(username, user, u.getPath(), "User"))) {
                final String pwd = u.getPassword();
                if (pwd != null) {
                    // TODO we might revise this warning once we're able
                    // to create users by providing their encoded password
                    // using u.getPasswordEncoding - for now I think only cleartext works
                    log.warn("Creating user {} with cleartext password - should NOT be used on production systems", username);
                } else {
                    log.info("Creating user {}", username);
                }
                UserUtil.createUser(session, username, pwd, u.getPath());
            }
        } catch (Exception e) {
            report(e, "Unable to create user [" + username + "]:" + e);
        }
    }

    @Override
    public void visitDeleteUser(DeleteUser u) {
        final String username = u.getUsername();
        log.info("Deleting user {}", username);
        try {
            if (!UserUtil.deleteAuthorizable(session, username)) {
                log.debug("User {} doesn't exist - assuming delete to be a noop.", username);
            }
        } catch (Exception e) {
            report(e, "Unable to delete user [" + username + "]:" + e);
        }
    }

    @Override
    public void visitDisableServiceUser(DisableServiceUser dsu) {
        final String username = dsu.getUsername();
        final String reason = dsu.getReason();
        log.info("Disabling service user {} reason {}", username, reason );
        try {
            if (!UserUtil.disableUser(session, username, reason)) {
                log.debug("Service user {} doesn't exist - assuming disable to be a noop.", username);
            }
        } catch (Exception e) {
            report(e, "Unable to disable service user [" + username + "]:" + e);
        }
    }

    private static void checkUserType(@NotNull String id, @Nullable User user, boolean expectedSystemUser) {
        if (user != null && user.isSystemUser() != expectedSystemUser) {
            String msg = (expectedSystemUser) ? "Existing user %s is not a service user." : "Existing user %s is a service user.";
            throw new RuntimeException(String.format(msg, id));
        }
    }

    private boolean needsRecreate(@NotNull String id, @NotNull Authorizable authorizable, @NotNull String intermediatePath, @NotNull String type) throws RepositoryException {
        String path = getPath(authorizable);
        if (path != null) {
            String requiredIntermediate = intermediatePath + "/";
            if (!path.contains(requiredIntermediate)) {
                log.info("Recreating {} '{}' with path '{}' to match required intermediate path '{}'", type, id, path, intermediatePath);
                authorizable.remove();
                return true;
            } else {
                log.info("{} '{}' already exists with required intermediate path '{}', no changes made.", type, id, intermediatePath);
            }
        } else {
            log.error("{} '{}' already exists but path cannot be determined, no changes made.", type, id);
        }
        return false;
    }
}
