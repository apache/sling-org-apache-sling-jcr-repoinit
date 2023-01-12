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

import javax.jcr.Session;

import org.apache.sling.repoinit.parser.operations.AddGroupMembers;
import org.apache.sling.repoinit.parser.operations.AddMixins;
import org.apache.sling.repoinit.parser.operations.CreateGroup;
import org.apache.sling.repoinit.parser.operations.CreatePath;
import org.apache.sling.repoinit.parser.operations.CreateServiceUser;
import org.apache.sling.repoinit.parser.operations.CreateUser;
import org.apache.sling.repoinit.parser.operations.DeleteAclPrincipals;
import org.apache.sling.repoinit.parser.operations.DeleteGroup;
import org.apache.sling.repoinit.parser.operations.DeleteServiceUser;
import org.apache.sling.repoinit.parser.operations.DeleteUser;
import org.apache.sling.repoinit.parser.operations.DisableServiceUser;
import org.apache.sling.repoinit.parser.operations.EnsureNodes;
import org.apache.sling.repoinit.parser.operations.OperationVisitor;
import org.apache.sling.repoinit.parser.operations.RegisterNamespace;
import org.apache.sling.repoinit.parser.operations.RegisterNodetypes;
import org.apache.sling.repoinit.parser.operations.RegisterPrivilege;
import org.apache.sling.repoinit.parser.operations.DeleteAclPaths;
import org.apache.sling.repoinit.parser.operations.DeleteAclPrincipalBased;
import org.apache.sling.repoinit.parser.operations.RemoveAcePaths;
import org.apache.sling.repoinit.parser.operations.RemoveAcePrincipalBased;
import org.apache.sling.repoinit.parser.operations.RemoveAcePrincipals;
import org.apache.sling.repoinit.parser.operations.RemoveGroupMembers;
import org.apache.sling.repoinit.parser.operations.RemoveMixins;
import org.apache.sling.repoinit.parser.operations.SetAclPaths;
import org.apache.sling.repoinit.parser.operations.SetAclPrincipalBased;
import org.apache.sling.repoinit.parser.operations.SetAclPrincipals;
import org.apache.sling.repoinit.parser.operations.SetProperties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Base class for specialized OperationVisitors.
 */
class DoNothingVisitor implements OperationVisitor {

    protected final Logger log = LoggerFactory.getLogger(getClass());

    protected final Session session;

    /** Create a visitor using the supplied JCR Session.
     * @param s must have sufficient rights to create users
     *      and set ACLs.
     */
    protected DoNothingVisitor(Session s) {
        session = s;
    }

    protected void report(Exception e, String message) {
        throw new RepoInitException(message, e);
    }

    protected void report(String message) {
        throw new RepoInitException(message);
    }

    protected static String excerpt(String s, int maxLength) {
        if(s.length() < maxLength) {
            return s;
        } else {
            return s.substring(0, maxLength -1) + "...";
        }
    }

    @Override
    public void visitCreateServiceUser(CreateServiceUser s) {
        // no-op
    }

    @Override
    public void visitDeleteServiceUser(DeleteServiceUser s) {
        // no-op
    }

    @Override
    public void visitCreateUser(CreateUser cu) {
        // no-op
    }

    @Override
    public void visitDeleteUser(DeleteUser u) {
        // no-op
    }

    @Override
    public void visitSetAclPrincipal(SetAclPrincipals s) {
        // no-op
    }

    @Override
    public void visitSetAclPaths(SetAclPaths s) {
        // no-op
    }

    @Override
    public void visitSetAclPrincipalBased(SetAclPrincipalBased operation) {
        // no-op
    }

    @Override
    public void visitRemoveAcePrincipal(RemoveAcePrincipals s) {
        // no-op
    }

    @Override
    public void visitRemoveAcePaths(RemoveAcePaths s) {
        // no-op
    }

    @Override
    public void visitRemoveAcePrincipalBased(RemoveAcePrincipalBased s) {
        // no-op
    }

    @Override
    public void visitDeleteAclPrincipals(DeleteAclPrincipals s) {
        // no-op
    }

    @Override
    public void visitDeleteAclPaths(DeleteAclPaths s) {
        // no-op
    }

    @Override
    public void visitDeleteAclPrincipalBased(DeleteAclPrincipalBased s) {
        // no-op
    }

    @Override
    public void visitCreatePath(CreatePath cp) {
        // no-op
    }

    @Override
    public void visitEnsureNodes(EnsureNodes en) {
        // no-op
    }

    @Override
    public void visitRegisterNamespace(RegisterNamespace rn) {
        // no-op
    }

    @Override
    public void visitRegisterNodetypes(RegisterNodetypes rn) {
        // no-op
    }

    @Override
    public void visitRegisterPrivilege(RegisterPrivilege rp) {
        // no-op
    }

    @Override
    public void visitDisableServiceUser(DisableServiceUser dsu) {
        // no-op
    }

    @Override
    public void visitCreateGroup(CreateGroup g) {
        // no-op
    }

    @Override
    public void visitDeleteGroup(DeleteGroup g) {
        // no-op
    }

    @Override
    public void visitAddGroupMembers(AddGroupMembers am) {
        // no-op
    }

    @Override
    public void visitRemoveGroupMembers(RemoveGroupMembers rm) {
        // no-op
    }

    @Override
    public void visitSetProperties(SetProperties sp) {
        // no-op
    }

    @Override
    public void visitAddMixins(AddMixins s) {
        // no-op
    }

    @Override
    public void visitRemoveMixins(RemoveMixins s) {
        // no-op
    }

}
