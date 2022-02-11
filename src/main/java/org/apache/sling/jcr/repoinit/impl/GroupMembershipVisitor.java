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
import org.apache.sling.repoinit.parser.operations.AddGroupMembers;
import org.apache.sling.repoinit.parser.operations.RemoveGroupMembers;

import javax.jcr.RepositoryException;
import javax.jcr.Session;
import java.util.List;

/**
 * OperationVisitor which processes only operations related to group memberships. Having several such specialized visitors makes it easy to control the
 * execution order.
 */
class GroupMembershipVisitor extends DoNothingVisitor {

    /**
     * Create a visitor using the supplied JCR Session.
     *
     * @param s must have sufficient rights to add/remove members to/from a group.
     */
    public GroupMembershipVisitor(Session s) {
        super(s);
    }

    @Override
    public void visitAddGroupMembers(AddGroupMembers am) {
        List<String> members = am.getMembers();
        String groupname = am.getGroupname();
        Authorizable group = null;
        log.info("Adding members '{}' to group '{}'", members, groupname);
        try {
            group = UserUtil.getAuthorizable(session, groupname);
            if (group == null || !group.isGroup()) {
                report(groupname + " is not a group");
            }
            ((Group) group).addMembers(members.toArray(new String[0]));
        } catch (RepositoryException e) {
            report(e, "Unable to add members to group [" + groupname + "]:" + e);
        }
    }

    @Override
    public void visitRemoveGroupMembers(RemoveGroupMembers rm) {
        List<String> members = rm.getMembers();
        String groupname = rm.getGroupname();
        Authorizable group = null;
        log.info("Removing members '{}' from group '{}'", members, groupname);
        try {
            group = UserUtil.getAuthorizable(session, groupname);
            if (group == null || !group.isGroup()) {
                report(groupname + " is not a group");
            }
            ((Group) group).removeMembers(members.toArray(new String[0]));
        } catch (RepositoryException e) {
            report(e, "Unable to remove members from group [" + groupname + "]:" + e);
        }
    }

}
