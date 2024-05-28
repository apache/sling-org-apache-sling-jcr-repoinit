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
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import javax.jcr.Session;

import org.apache.sling.jcr.repoinit.JcrRepoInitOpsProcessor;
import org.apache.sling.repoinit.parser.operations.Operation;
import org.apache.sling.repoinit.parser.operations.OperationVisitor;
import org.osgi.framework.Constants;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    private static final Logger log = LoggerFactory.getLogger(JcrRepoInitOpsProcessorImpl.class);

    /**
     * Apply the supplied operations: first the namespaces and nodetypes
     * registrations, then the service users, paths and ACLs.
     */
    @Override
    public void apply(Session session, List<Operation> ops) {
        AtomicReference<Operation> lastAttemptedOperation = new AtomicReference<>();
        try {
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
                ops.forEach(op -> {
                    lastAttemptedOperation.set(op);
                    visitorGroup.forEach(op::accept);
                });
            });
        } catch (RepoInitException originalFailure) {
            handleLegacyOrderingSupport(session, ops, originalFailure, lastAttemptedOperation);
        }
    }

    // support legacy statement reordering for backwards compatibility
    private static void handleLegacyOrderingSupport(Session session, List<Operation> ops, RepoInitException originalFailure, AtomicReference<Operation> lastAttemptedOperation) {
        try {
            session.refresh(false); // drop transient changes

            final OperationVisitor[] visitors = {
                    new NamespacesVisitor(session),
                    new NodetypesVisitor(session),
                    new PrivilegeVisitor(session),
                    new UserVisitor(session),
                    new NodeVisitor(session),
                    new AclVisitor(session),
                    new GroupMembershipVisitor(session),
                    new NodePropertiesVisitor(session)
            };

            for (OperationVisitor v : visitors) {
                for (Operation op : ops) {
                    op.accept(v);
                }
            }

            log.warn("DEPRECATION - The repoinit script being executed relies on a bug causing repoinit statements " +
                    "to be reordered (SLING-12107). For now your repoinit script was applied successfully in legacy " +
                    "mode. Please review and fix the ordering of your repoinit statements to avoid future issues. " +
                    "The code supporting the legacy order will be removed in a future release. The new code " +
                    "failed on the statement \"{}\". The original exception message was: {}",
                    Optional.ofNullable(lastAttemptedOperation.get()).map(Operation::asRepoInitString).orElse("unknown"),
                    originalFailure.getMessage());
        } catch (Exception legacyFailure) {
            // rethrow the originalFailure if the legacy code also failed
            throw originalFailure;
        }
    }
}
