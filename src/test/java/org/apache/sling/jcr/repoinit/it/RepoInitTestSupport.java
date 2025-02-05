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
import org.ops4j.pax.exam.util.PathUtils;
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
import static org.ops4j.pax.exam.CoreOptions.systemProperty;
import static org.ops4j.pax.exam.cm.ConfigurationAdminOptions.newConfiguration;

import java.util.Collection;
import java.util.HashSet;

public abstract class RepoInitTestSupport extends TestSupport {

    protected Session session;

    protected static final Logger log = LoggerFactory.getLogger(RepoInitTestSupport.class.getName());

    final String repositoriesURL = "https://repo1.maven.org/maven2@id=central";
    
    // artifact ID of all oak bundles
    private static final String[] OAK_BUNDLES = new String[] {"oak-api","oak-blob",
            "oak-blob-plugins","oak-commons","oak-core","oak-core-spi","oak-jcr",
            "oak-lucene","oak-query-spi","oak-security-spi","oak-segment-tar","oak-store-composite",
            "oak-store-document","oak-store-spi", "oak-jackrabbit-api"};
    
    private static final String[] JACKRABBIT_2x_BUNDLES = new String[] {"jackrabbit-data","jackrabbit-jcr-commons",
            "jackrabbit-jcr-rmi","jackrabbit-spi-commons","jackrabbit-spi","jackrabbit-webdav"};
  
    @Inject
    private SlingRepository repository;

    @Configuration
    public Option[] configuration() {
        
        updateOakVersion("1.44.0");
        Collection<Option> nonExistingJackrabbitBundles = updateJackrabbit("2.22.0");
        SlingOptions.versionResolver.setVersion("org.apache.sling", "org.apache.sling.jcr.oak.server", "1.2.10");
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
                        mavenBundle()
                            .groupId("org.apache.jackrabbit")
                            .artifactId("oak-store-spi")
                            .versionAsInProject(),
                        mavenBundle()
                            .groupId("commons-codec")
                            .artifactId("commons-codec")
                            .version("1.14"),
                        mavenBundle()
                            .groupId("commons-io")
                            .artifactId("commons-io")
                            .version("2.11.0"),
                        mavenBundle()
                            .groupId("org.apache.tika")
                            .artifactId("tika-core")
                            .version("1.24.1"),
                        SlingOptions.logback(),
                        systemProperty("logback.configurationFile").value("file:" + PathUtils.getBaseDir() + "/src/test/resources/logback-it.xml"),
                        junitBundles(),
                        awaitility(),
                        newConfiguration("org.apache.sling.jcr.base.internal.LoginAdminWhitelist")
                                .put("whitelist.bundles.regexp", "^PAXEXAM.*$")
                                .asOption())
                .add(additionalOptions())
                .remove(nonExistingJackrabbitBundles)
                .remove(mavenBundle().groupId("org.apache.jackrabbit").artifactId("jackrabbit-api").version(versionResolver))
                .remove(
                        // remove our bundle under test to avoid duplication
                        mavenBundle()
                                .groupId("org.apache.sling")
                                .artifactId("org.apache.sling.jcr.repoinit")
                                .version(versionResolver)));
    }

    protected void updateOakVersion(String defaultVersion) {
        for (int i=0;i< OAK_BUNDLES.length;i++) {
            try {
                SlingOptions.versionResolver.setVersionFromProject("org.apache.jackrabbit", OAK_BUNDLES[i]);
            } catch (IllegalArgumentException e) {
                // the version is not explicitly listed in the pom, so use the provided default version
                SlingOptions.versionResolver.setVersion("org.apache.jackrabbit", OAK_BUNDLES[i], defaultVersion);
            }
        }
    }
    
    protected Collection<Option> updateJackrabbit(String version) {
        Collection<Option> nonExistingBundles = new HashSet<>();
        for (int i=0;i< JACKRABBIT_2x_BUNDLES.length;i++) {
            Option existingBundle = null;
            try {
                String artifactId=JACKRABBIT_2x_BUNDLES[i];
                existingBundle = mavenBundle().groupId("org.apache.jackrabbit").artifactId(artifactId).versionAsInProject();
                SlingOptions.versionResolver.setVersion("org.apache.jackrabbit", artifactId, version);
            } catch (IllegalArgumentException e) {
                nonExistingBundles.add(existingBundle);
                
            }
        }
        return nonExistingBundles;
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
