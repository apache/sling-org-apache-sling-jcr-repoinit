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

import static org.junit.Assert.assertTrue;
import static org.ops4j.pax.exam.cm.ConfigurationAdminOptions.newConfiguration;

import javax.inject.Inject;
import javax.jcr.Session;
import javax.jcr.SimpleCredentials;

import org.apache.sling.jcr.api.SlingRepository;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerClass;

/** Verify that statements provided as URLS to our RepositoryInitializer
 *  are executed.
 */
@RunWith(PaxExam.class)
@ExamReactorStrategy(PerClass.class)
public class RepositoryInitializerIT extends RepoInitTestSupport {

    static final String [] REPOINIT_SRC_URLS = {
        "raw:file://" + getRepoinitFilesPath() + "/repoinit-path-1.txt",
        "raw:file://" + getRepoinitFilesPath() + "/repoinit-path-2.txt",
    };

    private Session session;

    @Inject
    private SlingRepository repository;

    protected Option[] additionalOptions() {
        return new Option[] {
            newConfiguration("org.apache.sling.jcr.repoinit.impl.RepositoryInitializer")
            .put("references", RepositoryInitializerIT.REPOINIT_SRC_URLS)
            .asOption()
        };
    }

    @Before
    public void setup() throws Exception {
        session = repository.login(new SimpleCredentials("admin", "admin".toCharArray()));
    }

    @After
    public void cleanup() {
        if(session != null) {
            session.logout();
        }
    }

    @Test
    public void path1Created() throws Exception {
        assertTrue(session.itemExists("/repoinit-test/path-1"));
    }

    @Test
    public void path2Created() throws Exception {
        assertTrue(session.itemExists("/repoinit-test/path-2"));
    }

    static String getRepoinitFilesPath() {
        return System.getProperty("repoinit.test.files.path");
    }
}