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

import java.util.List;
import java.util.stream.Stream;

import javax.jcr.Session;

import org.apache.sling.jcr.repoinit.JcrRepoInitOpsProcessor;
import org.apache.sling.repoinit.parser.operations.Operation;
import org.osgi.framework.Constants;
import org.osgi.service.component.annotations.Component;

import static java.util.Arrays.asList;
import static java.util.Collections.singleton;

/**
 * Apply Operations produced by the repoinit parser to a JCR Repository
 */
@Component(service = JcrRepoInitOpsProcessor.class,
        property = {
                Constants.SERVICE_VENDOR + "=The Apache Software Foundation"
        })
public class JcrRepoInitOpsProcessorImpl implements JcrRepoInitOpsProcessor {

    /**
     * Apply the supplied operations: first the namespaces and nodetypes
     * registrations, then the service users, paths and ACLs.
     */
    @Override
    public void apply(Session session, List<Operation> ops) {
        Stream.of(
                // register namespaces first
                singleton(new NamespacesVisitor(session)),
                // then create node types and privileges, both use namespaces
                asList(
                        new NodetypesVisitor(session),
                        new PrivilegeVisitor(session)),
                // finally apply everything else
                asList(
                        new UserVisitor(session),
                        new NodeVisitor(session),
                        new AclVisitor(session),
                        new GroupMembershipVisitor(session),
                        new NodePropertiesVisitor(session))
        ).forEach(visitorGroup -> {
            ops.forEach(op -> visitorGroup.forEach(op::accept));
        });
    }
}
