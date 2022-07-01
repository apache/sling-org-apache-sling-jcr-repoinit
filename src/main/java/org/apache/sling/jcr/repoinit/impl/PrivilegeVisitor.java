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


import org.apache.jackrabbit.api.JackrabbitWorkspace;
import org.apache.sling.repoinit.parser.operations.RegisterPrivilege;

import javax.jcr.Session;
import javax.jcr.security.AccessControlException;
import javax.jcr.security.Privilege;

public class PrivilegeVisitor extends DoNothingVisitor {
    public PrivilegeVisitor(Session session) {
        super(session);
    }

    @Override
    public void visitRegisterPrivilege(RegisterPrivilege rp) {
        try {
            Privilege priv = ((JackrabbitWorkspace) session.getWorkspace()).getPrivilegeManager().getPrivilege(rp.getPrivilegeName());
            log.info("Privilege {} already exists: {}, no changes made.", rp.getPrivilegeName(), priv);
        } catch (AccessControlException ace) {
            try {
                ((JackrabbitWorkspace) session.getWorkspace()).getPrivilegeManager()
                    .registerPrivilege(rp.getPrivilegeName(), rp.isAbstract(), rp.getDeclaredAggregateNames().toArray(new String[0]));
            } catch (Exception ex) {
                report(ex, "Unable to register privilege from: " + rp);
            }
        } catch (Exception e) {
            report(e, "Unable to register privilege from: " + rp);
        }
    }
}
