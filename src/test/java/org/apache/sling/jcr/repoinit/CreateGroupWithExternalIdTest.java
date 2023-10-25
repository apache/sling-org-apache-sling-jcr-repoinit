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
package org.apache.sling.jcr.repoinit;

import org.apache.jackrabbit.api.security.user.Authorizable;
import org.apache.jackrabbit.oak.jcr.Jcr;
import org.apache.jackrabbit.oak.security.internal.SecurityProviderBuilder;
import org.apache.jackrabbit.oak.spi.security.ConfigurationParameters;
import org.apache.jackrabbit.oak.spi.security.SecurityProvider;
import org.apache.jackrabbit.oak.spi.security.authentication.external.impl.principal.ExternalPrincipalConfiguration;
import org.apache.sling.jcr.repoinit.impl.TestUtil;
import org.apache.sling.jcr.repoinit.impl.UserUtil;
import org.apache.sling.repoinit.parser.RepoInitParsingException;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;

import javax.jcr.Repository;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.SimpleCredentials;
import javax.jcr.Value;
import javax.jcr.nodetype.ConstraintViolationException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;

public class CreateGroupWithExternalIdTest {

    private static final String GROUP_NAME = "testGroup";

    @Test
    public void setRepExternalIdPropertyOnExistingGroupFails() throws RepositoryException, RepoInitParsingException {
        // validates the test setup more than anything else
        TestUtil U = createTestUtil();

        U.assertGroup(GROUP_NAME + " should not yet exist", GROUP_NAME, false);

        U.parseAndExecute("create group " + GROUP_NAME);
        U.assertGroup(GROUP_NAME + " should exist", GROUP_NAME, true);

        assertThrows(ConstraintViolationException.class, () -> U.parseAndExecute(
                "set properties on authorizable(" + GROUP_NAME + ")",
                "  set rep:externalId to \"" + GROUP_NAME + ";ldap\"",
                "end"
        ));
        // The ConstraintViolationException does not refresh the session automatically,
        // so the invalid changes are still transiently present and need to be explicitly dropped.
        U.getAdminSession().refresh(false);

        final Authorizable group = UserUtil.getAuthorizable(U.getAdminSession(), GROUP_NAME);
        assertNull(GROUP_NAME + " should not have a rep:externalId property", group.getProperty("rep:externalId"));
    }

    @Test
    public void setRepExternalIdPropertyOnNewGroupSucceeds() throws RepositoryException, RepoInitParsingException {
        TestUtil U = createTestUtil();

        U.assertGroup(GROUP_NAME + " should not yet exist", GROUP_NAME, false);

        // Do this twice in order to test initial creation and update scenarios.
        for (int i = 0; i < 2; i++) {
            U.parseAndExecute(
                    "create group " + GROUP_NAME,
                    "set properties on authorizable(" + GROUP_NAME + ")",
                    "  set rep:externalId to \"" + GROUP_NAME + ";ldap\"",
                    "end"
            );
            U.assertGroup(GROUP_NAME + " should have been created", GROUP_NAME, true);

            final Authorizable group = UserUtil.getAuthorizable(U.getAdminSession(), GROUP_NAME);
            final Value[] externalIdValues = group.getProperty("rep:externalId");
            assertEquals(1, externalIdValues.length);
            assertEquals(GROUP_NAME + ";ldap", externalIdValues[0].getString());
        }
    }

    @NotNull
    private static TestUtil createTestUtil() throws RepositoryException {
        final SecurityProvider securityProvider = createSecurityProvider();

        final Repository repository = new Jcr()
                .with(securityProvider)
                .createRepository();
        final Session adminSession = repository.login(new SimpleCredentials("admin", "admin".toCharArray()));
        return new TestUtil(adminSession);
    }

    @NotNull
    private static SecurityProvider createSecurityProvider() {
        return SecurityProviderBuilder.newBuilder()
                .with(
                        null, ConfigurationParameters.EMPTY,
                        null, ConfigurationParameters.EMPTY,
                        null, ConfigurationParameters.EMPTY,
                        null, ConfigurationParameters.EMPTY,
                        new ExternalPrincipalConfiguration(), ConfigurationParameters.EMPTY,
                        null, ConfigurationParameters.EMPTY)
                .build();
    }
}