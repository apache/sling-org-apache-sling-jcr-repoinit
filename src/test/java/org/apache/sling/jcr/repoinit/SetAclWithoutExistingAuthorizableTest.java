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
package org.apache.sling.jcr.repoinit;

import javax.jcr.Repository;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.SimpleCredentials;

import java.util.stream.Stream;

import org.apache.jackrabbit.oak.jcr.Jcr;
import org.apache.jackrabbit.oak.security.internal.SecurityProviderBuilder;
import org.apache.jackrabbit.oak.spi.security.ConfigurationParameters;
import org.apache.jackrabbit.oak.spi.security.SecurityProvider;
import org.apache.jackrabbit.oak.spi.security.authorization.AuthorizationConfiguration;
import org.apache.jackrabbit.oak.spi.xml.ImportBehavior;
import org.apache.jackrabbit.oak.spi.xml.ProtectedItemImporter;
import org.apache.sling.jcr.repoinit.impl.RepoInitException;
import org.apache.sling.jcr.repoinit.impl.TestUtil;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.Assert.assertThrows;
import static org.junit.jupiter.params.provider.Arguments.arguments;

class SetAclWithoutExistingAuthorizableTest {

    static Stream<Arguments> nonExistingGroupStatements() {
        return Stream.of(
                arguments(String.join(
                        "\n",
                        "set ACL on / (ACLOptions=ignoreMissingPrincipal)",
                        "  allow jcr:read for nonExistingGroup",
                        "end")),
                arguments(String.join(
                        "\n",
                        "set ACL for nonExistingGroup (ACLOptions=ignoreMissingPrincipal)",
                        "  allow jcr:read on /",
                        "end")));
    }

    static Stream<Arguments> existingGroupStatements() {
        return Stream.of(ImportBehavior.NAME_BESTEFFORT, ImportBehavior.NAME_IGNORE, ImportBehavior.NAME_ABORT)
                .flatMap(importBehaviour -> Stream.of(
                        arguments(
                                importBehaviour,
                                String.join(
                                        "\n",
                                        "create group existingGroup",
                                        "set ACL on / (ACLOptions=ignoreMissingPrincipal)",
                                        "  allow jcr:read for existingGroup",
                                        "end")),
                        arguments(
                                importBehaviour,
                                String.join(
                                        "\n",
                                        "create group existingGroup",
                                        "set ACL for existingGroup (ACLOptions=ignoreMissingPrincipal)",
                                        "  allow jcr:read on /",
                                        "end"))));
    }

    @ParameterizedTest
    @MethodSource("nonExistingGroupStatements")
    void withImportBehaviourBestEffort(String repoinit) throws Exception {
        TestUtil U = createTestUtil(ImportBehavior.NAME_BESTEFFORT);
        assertReadOnRootPath(U, "nonExistingGroup", false);
        U.parseAndExecute(repoinit);
        assertReadOnRootPath(U, "nonExistingGroup", true);
    }

    @ParameterizedTest
    @MethodSource("nonExistingGroupStatements")
    void withImportBehaviourIgnore(String repoinit) throws Exception {
        TestUtil U = createTestUtil(ImportBehavior.NAME_IGNORE);
        assertReadOnRootPath(U, "nonExistingGroup", false);
        U.parseAndExecute(repoinit);
        assertReadOnRootPath(U, "nonExistingGroup", false);
    }

    @ParameterizedTest
    @MethodSource("nonExistingGroupStatements")
    void withImportBehaviourAbort(String repoinit) throws Exception {
        TestUtil U = createTestUtil(ImportBehavior.NAME_ABORT);
        assertReadOnRootPath(U, "nonExistingGroup", false);
        assertThrows(RepoInitException.class, () -> U.parseAndExecute(repoinit));
        assertReadOnRootPath(U, "nonExistingGroup", false);
    }

    @ParameterizedTest
    @MethodSource("existingGroupStatements")
    void withExistingGroup(String importBehavior, String repoinit) throws Exception {
        TestUtil U = createTestUtil(importBehavior);
        assertReadOnRootPath(U, "existingGroup", false);
        U.parseAndExecute(repoinit);
        assertReadOnRootPath(U, "existingGroup", true);
    }

    private static void assertReadOnRootPath(TestUtil U, String principalName, boolean allowed)
            throws RepositoryException {
        U.assertPrivileges(principalName, "/", allowed, "jcr:read");
    }

    @NotNull
    private static TestUtil createTestUtil(String importBehaviour) throws RepositoryException {
        final Repository repository =
                new Jcr().with(createSecurityProvider(importBehaviour)).createRepository();
        final Session adminSession = repository.login(new SimpleCredentials("admin", "admin".toCharArray()));
        return new TestUtil(adminSession);
    }

    @NotNull
    private static SecurityProvider createSecurityProvider(String importBehaviorName) {
        return SecurityProviderBuilder.newBuilder()
                .with(ConfigurationParameters.of(
                        AuthorizationConfiguration.NAME,
                        ConfigurationParameters.of(ProtectedItemImporter.PARAM_IMPORT_BEHAVIOR, importBehaviorName)))
                .build();
    }
}
