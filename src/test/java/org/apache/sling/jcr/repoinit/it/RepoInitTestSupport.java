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
package org.apache.sling.jcr.repoinit.it;

import javax.inject.Inject;
import javax.jcr.Session;
import javax.jcr.SimpleCredentials;

import org.apache.sling.jcr.api.SlingRepository;
import org.apache.sling.testing.paxexam.SlingOptions;
import org.apache.sling.testing.paxexam.TestSupport;
import org.junit.After;
import org.junit.Before;
import org.ops4j.pax.exam.Configuration;
import org.ops4j.pax.exam.Option;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.sling.testing.paxexam.SlingOptions.awaitility;
import static org.apache.sling.testing.paxexam.SlingOptions.slingQuickstartOakTar;
import static org.apache.sling.testing.paxexam.SlingOptions.versionResolver;
import static org.ops4j.pax.exam.CoreOptions.composite;
import static org.ops4j.pax.exam.CoreOptions.junitBundles;
import static org.ops4j.pax.exam.CoreOptions.mavenBundle;
import static org.ops4j.pax.exam.CoreOptions.options;
import static org.ops4j.pax.exam.CoreOptions.vmOption;
import static org.ops4j.pax.exam.cm.ConfigurationAdminOptions.newConfiguration;

public abstract class RepoInitTestSupport extends TestSupport {

    protected Session session;

    protected static final Logger log = LoggerFactory.getLogger(RepoInitTestSupport.class.getName());

    final String repositoriesURL = "https://repo1.maven.org/maven2@id=central";

    @Inject
    private SlingRepository repository;

    @Configuration
    public Option[] configuration() {
        SlingOptions.versionResolver.setVersionFromProject("org.apache.jackrabbit", "oak-jackrabbit-api");
        SlingOptions.versionResolver.setVersionFromProject("org.apache.sling", "org.apache.sling.repoinit.parser");
        SlingOptions.versionResolver.setVersionFromProject("org.apache.sling", "org.apache.sling.commons.metrics");
        SlingOptions.versionResolver.setVersionFromProject("org.apache.commons", "commons-lang3"); // for the metrics
        return options(composite(
                        super.baseConfiguration(),
                        vmOption(System.getProperty("pax.vm.options")),
                        slingQuickstart(),
                        testBundle("bundle.filename"),
                        mavenBundle()
                                .groupId("org.apache.sling")
                                .artifactId("org.apache.sling.repoinit.parser")
                                .versionAsInProject(),
                        mavenBundle()
                            .groupId("org.apache.jackrabbit")
                            .artifactId("oak-jackrabbit-api")
                            .versionAsInProject(),
                        junitBundles(),
                        awaitility(),
                        newConfiguration("org.apache.sling.jcr.base.internal.LoginAdminWhitelist")
                                .put("whitelist.bundles.regexp", "^PAXEXAM.*$")
                                .asOption())
                .add(additionalOptions())
                .remove(
                        // remove our bundle under test to avoid duplication
                        mavenBundle()
                                .groupId("org.apache.sling")
                                .artifactId("org.apache.sling.jcr.repoinit")
                                .version(versionResolver)));
    }

    protected Option[] additionalOptions() {
        return new Option[] {};
    }

    protected Option slingQuickstart() {
        final String workingDirectory = workingDirectory();
        final int httpPort = findFreePort();
        return composite(slingQuickstartOakTar(workingDirectory, httpPort));
    }

    public String getTestFileUrl(String path) {
        return getClass().getResource(path).toExternalForm();
    }

    @Before
    public void setupSession() throws Exception {
        if (session == null) {
            session = repository.login(new SimpleCredentials("admin", "admin".toCharArray()));
        }
    }

    @After
    public void cleanupSession() {
        if (session != null) {
            session.logout();
        }
    }
}
