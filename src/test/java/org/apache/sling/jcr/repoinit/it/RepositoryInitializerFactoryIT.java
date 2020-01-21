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
package org.apache.sling.jcr.repoinit.it;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.ops4j.pax.exam.cm.ConfigurationAdminOptions.factoryConfiguration;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerClass;
import org.osgi.service.cm.Configuration;
import org.osgi.service.cm.ConfigurationAdmin;

/** Verify the RepositoryInitializerFactory functionality, to execute
 *  repoinit statements coming from OSGi configurations
 */
@RunWith(PaxExam.class)
@ExamReactorStrategy(PerClass.class)
public class RepositoryInitializerFactoryIT extends RepoInitTestSupport {

    @Inject
    private ConfigurationAdmin configAdmin;

    private static final String TEST_MARKER = "TEST_MARKER";
    private static final String TEST_MARKER_VALUE = "TEST_VALUE";

    @Override
    protected Option[] additionalOptions() {
        return new Option[] {
            factoryConfiguration("org.apache.sling.jcr.repoinit.RepositoryInitializer")
            .put(TEST_MARKER, TEST_MARKER_VALUE)
            .put("scripts", "create path /repoinit-test/scripts/A")
            .asOption(),
            factoryConfiguration("org.apache.sling.jcr.repoinit.RepositoryInitializer")
            .put(TEST_MARKER, TEST_MARKER_VALUE)
            .put("scripts", "create path /repoinit-test/scripts/B")
            .put("references", "")
            .asOption(),
            factoryConfiguration("org.apache.sling.jcr.repoinit.RepositoryInitializer")
            .put(TEST_MARKER, TEST_MARKER_VALUE)
            .put("scripts", "create path /repoinit-test/scripts/C")
            .put("references", "file://" + getRepoinitFilesPath() + "/repoinit-path-3.txt")
            .asOption(),
            factoryConfiguration("org.apache.sling.jcr.repoinit.RepositoryInitializer")
            .put(TEST_MARKER, TEST_MARKER_VALUE)
            .put("references", "file://" + getRepoinitFilesPath() + "/repoinit-path-4.txt")
            .asOption(),
            factoryConfiguration("org.apache.sling.jcr.repoinit.RepositoryInitializer")
            .put(TEST_MARKER, TEST_MARKER_VALUE)
            .put("references", "file://" + getRepoinitFilesPath() + "/repoinit-path-5.txt")
            .put("scripts", "")
            .asOption(),
            factoryConfiguration("org.apache.sling.jcr.repoinit.RepositoryInitializer")
            .put(TEST_MARKER, TEST_MARKER_VALUE)
            .put("references", "file://" + getRepoinitFilesPath() + "/repoinit-path-6.txt")
            .put("scripts", "create path /repoinit-test/scripts/D")
            .asOption(),
        };
    }

    private List<String> getMissingPaths() throws Exception {
        final String [] paths = {
            "/repoinit-test/scripts/A",
            // TODO fails due to SLING-9015  ?? "/repoinit-test/scripts/B", 
            "/repoinit-test/scripts/C",
            "/repoinit-test/scripts/D",
            "/repoinit-test/path-3",
            "/repoinit-test/path-4",
            "/repoinit-test/path-5",
            "/repoinit-test/path-6",
        };
        final List<String> missing = new ArrayList<>();
        for(String path : paths) {
            if(!session.itemExists(path)) {
                missing.add(path);
            }
        }
        return missing;
    }

    @Test
    public void allConfigsRegistered() throws Exception {
        int markerCount = 0;
        for(Configuration cfg : configAdmin.listConfigurations(null)) {
            if(cfg.getProperties().get(TEST_MARKER) != null) {
                markerCount++;
            }
        }

        // allPathsCreated fails semi-randomly, trying to find out what's happening
        final int expectedMarkers = 6;
        assertEquals("Expecting the correct amount of registered configs", expectedMarkers, markerCount);
    }

    @Test
    public void allPathsCreated() throws Exception {
        final long endTime = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(15);
        List<String> missing = null;

        // Configs are processed asynchronously, give them some time
        while(System.currentTimeMillis() < endTime) {
            missing = getMissingPaths();
            if(missing.isEmpty()) {
                break;
            }
            Thread.sleep(250);
        }
        assertTrue("Expecting all paths to be created, missing: " + missing, missing.isEmpty());
    }

}