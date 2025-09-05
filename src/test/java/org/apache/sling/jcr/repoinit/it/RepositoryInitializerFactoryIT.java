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

import java.time.Duration;
import java.util.ArrayList;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.List;

import org.awaitility.Awaitility;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerClass;
import org.osgi.service.cm.Configuration;
import org.osgi.service.cm.ConfigurationAdmin;

import static org.junit.Assert.assertFalse;

/** Verify the RepositoryInitializerFactory functionality, to execute
 *  repoinit statements coming from OSGi configurations
 */
@RunWith(PaxExam.class)
@ExamReactorStrategy(PerClass.class)
public class RepositoryInitializerFactoryIT extends RepoInitTestSupport {

    @Inject
    private ConfigurationAdmin configAdmin;

    private void assertConfigAndPaths(String references, String scripts, String... expectedPaths) throws Exception {
        for (String path : expectedPaths) {
            assertFalse("Expecting path to be absent before test:" + path, session.itemExists(path));
        }

        final Dictionary<String, Object> props = new Hashtable<>();
        if (references != null) {
            props.put("references", references);
        }
        if (scripts != null) {
            props.put("scripts", scripts);
        }
        final Configuration cfg =
                configAdmin.createFactoryConfiguration("org.apache.sling.jcr.repoinit.RepositoryInitializer");
        cfg.setBundleLocation(null);
        cfg.update(props);

        // Configs are processed asynchronously, give them some time
        List<String> missing = new ArrayList<>();
        Awaitility.await("configAndPathsExist")
                .atMost(Duration.ofSeconds(5))
                .pollInterval(Duration.ofMillis(250))
                .until(() -> {
                    session.refresh(false);
                    missing.clear();
                    for (String path : expectedPaths) {
                        if (!session.itemExists(path)) {
                            missing.add(path);
                        }
                    }
                    return missing.isEmpty();
                });
    }

    @Test
    public void testReferencesAndScripts() throws Exception {
        assertConfigAndPaths(null, "create path /repoinit-test/scripts/A", "/repoinit-test/scripts/A");

        assertConfigAndPaths("", "create path /repoinit-test/scripts/B", "/repoinit-test/scripts/B");

        assertConfigAndPaths(
                getTestFileUrl("/repoinit-path-3.txt"),
                "create path /repoinit-test/scripts/C",
                "/repoinit-test/path-3",
                "/repoinit-test/scripts/C");

        assertConfigAndPaths(getTestFileUrl("/repoinit-path-4.txt"), null, "/repoinit-test/path-4");

        assertConfigAndPaths(getTestFileUrl("/repoinit-path-5.txt"), "", "/repoinit-test/path-5");
    }
}
