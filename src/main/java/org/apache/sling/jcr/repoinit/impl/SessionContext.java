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

import java.security.Principal;
import java.util.HashMap;
import java.util.Map;

import org.apache.jackrabbit.api.JackrabbitSession;
import org.apache.jackrabbit.api.security.JackrabbitAccessControlManager;
import org.apache.jackrabbit.api.security.authorization.PrivilegeCollection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A simple wrapper around a session, which can cache the principal lookup and resolves privilege names to
 * {@code PrivilegeCollection} objects.
 */
public class SessionContext {

    JackrabbitSession session;
    JackrabbitAccessControlManager acMgr;
    Map<String, Principal> nameToPrincipal = new HashMap<>();

    public SessionContext(@NotNull Session session) {
        AclUtil.checkState(session instanceof JackrabbitSession, "A Jackrabbit Session is required");
        this.session = (JackrabbitSession) session;
        try {
            AclUtil.checkState(
                    session.getAccessControlManager() instanceof JackrabbitAccessControlManager,
                    "A Jackrabbit AccessControlManager is required");
            this.acMgr = (JackrabbitAccessControlManager) session.getAccessControlManager();
        } catch (RepositoryException e) {
            throw new IllegalStateException("Cannot retrieve the AccessControlManager");
        }
    }

    public JackrabbitSession getSession() {
        return session;
    }

    public JackrabbitAccessControlManager getAccessControlManager() {
        return acMgr;
    }

    public @NotNull PrivilegeCollection privilegeCollectionFromNames(@NotNull String... privilegeNames)
            throws RepositoryException {
        return acMgr.privilegeCollectionFromNames(privilegeNames);
    }

    public @Nullable Principal getPrincipal(@NotNull String principalName) throws RepositoryException {
        if (nameToPrincipal.containsKey(principalName)) {
            return nameToPrincipal.get(principalName);
        }
        Principal p = session.getPrincipalManager().getPrincipal(principalName);
        // Do not cache null principals
        if (p != null) {
            nameToPrincipal.put(principalName, p);
        }
        return p;
    }

    public @Nullable Principal getPrincipalWithSave(@NotNull String principalName) throws RepositoryException {
        Principal principal = getPrincipal(principalName);
        if (principal == null) {
            // due to transient nature of the repo-init the principal lookup may not succeed if completed through query
            // -> save transient changes and retry principal lookup
            session.save();
            principal = getPrincipal(principalName);
        }
        return principal;
    }
}
