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

import java.security.Principal;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.security.Privilege;

import org.apache.jackrabbit.api.JackrabbitSession;
import org.apache.jackrabbit.api.security.JackrabbitAccessControlManager;
import org.apache.jackrabbit.commons.jackrabbit.authorization.AccessControlUtils;

import com.google.common.collect.Lists;

/**
 * A simple wrapper around a session, which can cache the privilege resolution
 */
public class PrivilegeCachingSessionWrapper {

    JackrabbitSession session;
    JackrabbitAccessControlManager acMgr;
    Map<String,Privilege> nameToPrivilegeMap = new HashMap<>();
    Map<Privilege,List<Privilege>> privilegeToAggreate = new HashMap<>();
    Map<String,Principal> idToPrincipal = new HashMap<>();
    
    public PrivilegeCachingSessionWrapper (Session session) {
        AclUtil.checkState(session instanceof JackrabbitSession,"A Jackrabbit Session is required");
        this.session = (JackrabbitSession) session;
        try {
            AclUtil.checkState(session.getAccessControlManager() instanceof JackrabbitAccessControlManager, 
                    "A Jachrabbit AccessControlManager is required");
            this.acMgr = (JackrabbitAccessControlManager) session.getAccessControlManager();
        } catch (RepositoryException e) {
            throw new IllegalStateException("Cannot retrieve the AcccessControlManager");
        }
    }

    public JackrabbitSession getSession() {
        return session;
    }

    public JackrabbitAccessControlManager getAccessControlManager() {
        return acMgr;
    }

    /**
     * Retrieve the matching privileges from the given privilege names; uses internally a cache. The retrieval
     * logic is identical to AccessControlUtils.privilegesFromName, but with caching
     * @param privilegeNames the name of the privileges
     * @return the matching privileges
     * @throws RepositoryException in case of errors
     */
    public Privilege[] privilegesFromNames(String... privilegeNames) throws RepositoryException {
        Set<Privilege> privileges = new HashSet<Privilege>(privilegeNames.length);
        for (String privName : privilegeNames) {
            if (nameToPrivilegeMap.containsKey(privName)) {
                privileges.add(nameToPrivilegeMap.get(privName));
            } else {
                Privilege p = acMgr.privilegeFromName(privName);
                nameToPrivilegeMap.put(privName, p);
                privileges.add(p);
            }
        }
        return privileges.toArray(new Privilege[privileges.size()]);
    }

    /**
     * If a privilege is an aggreated, return the privilges it contains, otherwise return the privilege itself
     * @param priv the privilege 
     * @return
     */
    public List<Privilege> expandPrivilege (Privilege priv) {
        return privilegeToAggreate.computeIfAbsent(priv, (p) -> {
            if (p.isAggregate()) {
                return Arrays.asList(p.getAggregatePrivileges());
            } else {
                return Lists.newArrayList(p);
            }
        });
    }

    public Principal getPrincipal (String principalName) throws RepositoryException {
        if (idToPrincipal.containsKey(principalName)) {
            return idToPrincipal.get(principalName);
        } else {
            Principal p = AccessControlUtils.getPrincipal(this.getSession(), principalName);
            idToPrincipal.put(principalName, p);
            return p;
        }
    }
}
