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

import org.apache.jackrabbit.api.JackrabbitSession;
import org.apache.jackrabbit.api.security.JackrabbitAccessControlManager;

/**
 * A simple wrapper around a session, which can cache the privilege resolution
 */
public class PrivilegeCachingSessionWrapper {

    JackrabbitSession session;
    JackrabbitAccessControlManager acMgr;
    
    public PrivilegeCachingSessionWrapper (Session session) {
        AclUtil.checkState(session instanceof JackrabbitSession,"A Jackrabbit Session is required");
        this.session = (JackrabbitSession) session;
        try {
            AclUtil.checkState(session.getAccessControlManager() instanceof JackrabbitAccessControlManager, 
                    "A Jachrabbit AccessControlManager is required");
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

}
