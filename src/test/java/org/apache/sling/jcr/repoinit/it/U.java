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
package org.apache.sling.jcr.repoinit.it;

import java.util.UUID;
import java.util.Arrays;

import javax.jcr.AccessDeniedException;
import javax.jcr.LoginException;
import javax.jcr.Node;
import javax.jcr.Value;
import javax.jcr.PathNotFoundException;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.SimpleCredentials;
import javax.jcr.UnsupportedRepositoryOperationException;

import org.apache.jackrabbit.api.JackrabbitSession;
import org.apache.jackrabbit.api.security.user.Authorizable;
import org.apache.jackrabbit.api.security.user.Group;
import org.apache.jackrabbit.api.security.user.User;

/** Test utilities */
public class U {
    public static boolean userExists(Session session, String id) throws LoginException, RepositoryException, InterruptedException {
        final Authorizable a = ((JackrabbitSession)session).getUserManager().getAuthorizable(id);
        return a != null;
    }

    public static boolean userIsDisabled(Session session, String id) throws RepositoryException {
        final Authorizable a = ((JackrabbitSession)session).getUserManager().getAuthorizable(id);
        if (a == null) {
            throw new IllegalStateException("Authorizable not found:" + id);
        }
        if (a.isGroup()) {
            throw new IllegalStateException("Authorizable is a group:" + id);
        }
        return ((User)a).isDisabled();
    }

    public static Session getServiceSession(Session session, String serviceId) throws LoginException, RepositoryException {
        return session.impersonate(new SimpleCredentials(serviceId, new char[0]));
    }

    /** True if user can write to specified path.
     *  @throws PathNotFoundException if the path doesn't exist */
    public static boolean canWrite(Session session, String userId, String path) throws PathNotFoundException,RepositoryException {
        if(!session.itemExists(path)) {
            throw new PathNotFoundException(path);
        }

        final Session serviceSession = getServiceSession(session, userId);
        final String testNodeName = "test_" + UUID.randomUUID().toString();
        try {
            ((Node)serviceSession.getItem(path)).addNode(testNodeName);
            serviceSession.save();
        } catch(AccessDeniedException ade) {
            return false;
        } catch(PathNotFoundException pnf) {
            // Thrown when access is denied to a user's home node
            return false;
        } finally {
            serviceSession.logout();
        }
        return true;
    }

    /** True if user can read specified path.
     *  @throws PathNotFoundException if the path doesn't exist */
    public static boolean canRead(Session session, String userId, String path) throws PathNotFoundException,RepositoryException {
        if(!session.itemExists(path)) {
            throw new PathNotFoundException(path);
        }

        final Session serviceSession = getServiceSession(session, userId);
        try {
            serviceSession.getItem(path);
        } catch(AccessDeniedException ade) {
            return false;
        } catch(PathNotFoundException pnf) {
            // Thrown when access is denied to a user's home node
            return false;
        } finally {
            serviceSession.logout();
        }
        return true;
    }

    public static String getHomePath(Session session, String userId)
    throws UnsupportedRepositoryOperationException, RepositoryException {
        final Authorizable a = ((JackrabbitSession)session).getUserManager().getAuthorizable(userId);
        return a.getPath();
    }

    public static boolean isMember(Session session, String userId, String groupId) throws  RepositoryException {
        final Authorizable a = ((JackrabbitSession)session).getUserManager().getAuthorizable(groupId);
        final Authorizable member = ((JackrabbitSession)session).getUserManager().getAuthorizable(userId);
        return ((Group) a).isMember(member);
    }

    public static boolean hasProperty(Session session, String nodePath, String propertyName, Value propertyValue) throws  RepositoryException {
        final Node n = session.getNode(nodePath);
        if (n != null) {
            boolean isPropertyPresent = n.hasProperty(propertyName);
            if (isPropertyPresent) {
                Value v = n.getProperty(propertyName).getValue();
                return isPropertyPresent && (v.equals(propertyValue));
            }
        }
        return false;
    }

    public static boolean hasProperty(Session session, String nodePath, String propertyName, Value[] propertyValues) throws  RepositoryException {
        final Node n = session.getNode(nodePath);
        if (n != null) {
            boolean isPropertyPresent = n.hasProperty(propertyName);
            if (isPropertyPresent) {
                Value[] v = n.getProperty(propertyName).getValues();
                return isPropertyPresent && Arrays.equals(v, propertyValues);
            }
        }
        return false;
    }

    public static boolean hasProperty(Authorizable a, String propertyName, Value propertyValue) throws  RepositoryException {
        if (a != null) {
            boolean isPropertyPresent = a.hasProperty(propertyName);
            if (isPropertyPresent) {
                Value[] values = a.getProperty(propertyName);
                if (values != null && values.length == 1) {
                    Value v = values[0];
                    return v.equals(propertyValue);
                }
            }
        }
        return false;
    }

    public static boolean hasProperty(Authorizable a, String propertyName, Value[] propertyValues) throws  RepositoryException {
        if (a != null) {
            boolean isPropertyPresent = a.hasProperty(propertyName);
            if (isPropertyPresent) {
                Value[] v = a.getProperty(propertyName);
                return Arrays.equals(v, propertyValues);
            }
        }
        return false;
    }
}