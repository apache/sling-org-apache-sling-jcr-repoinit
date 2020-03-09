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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.UUID;

import javax.inject.Inject;

import org.apache.sling.jcr.repoinit.JcrRepoInitOpsProcessor;
import org.apache.sling.repoinit.parser.RepoInitParser;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerClass;

/** Basic integration test of the repoinit parser and execution
 *  services, reading statements from a text file.
 */
@RunWith(PaxExam.class)
@ExamReactorStrategy(PerClass.class)
public class RepoInitTextIT extends RepoInitTestSupport {

    private static final String FRED_WILMA = "fredWilmaService";
    private static final String ANOTHER = "anotherService";
    private static final String ALICE = "alice";
    private static final String BOB = "bob";
    private static final String GROUP_A = "grpA";
    private static final String GROUP_B = "grpB";

    public static final String REPO_INIT_FILE = "/repoinit.txt";

    @Inject
    private RepoInitParser parser;

    @Inject
    private JcrRepoInitOpsProcessor processor;

    @Before
    public void setup() throws Exception {
        setupSession();

        // Execute some repoinit statements
        final InputStream is = getClass().getResourceAsStream(REPO_INIT_FILE);
        assertNotNull("Expecting " + REPO_INIT_FILE, is);
        try {
            processor.apply(session, parser.parse(new InputStreamReader(is, "UTF-8")));
            session.save();
        } finally {
            is.close();
        }

        // The repoinit file causes those nodes to be created
        assertTrue("Expecting test nodes to be created", session.itemExists("/acltest/A/B"));
    }

    @Test
    public void serviceUserCreatedWithHomePath() throws Exception {
        new Retry() {
            @Override
            public Void call() throws Exception {
                assertTrue("Expecting user " + FRED_WILMA, U.userExists(session, FRED_WILMA));
                final String path = U.getHomePath(session, FRED_WILMA);
                assertTrue("Expecting path " + path, session.itemExists(path));
                return null;
            }
        };
    }

    @Test
    public void fredWilmaAcl() throws Exception {
        new Retry() {
            @Override
            public Void call() throws Exception {
                assertFalse("Expecting no write access to A", U.canWrite(session, FRED_WILMA, "/acltest/A"));
                assertTrue("Expecting write access to A/B", U.canWrite(session, FRED_WILMA, "/acltest/A/B"));
                return null;
            }
        };
    }

    @Test
    public void anotherUserAcl() throws Exception {
        new Retry() {
            @Override
            public Void call() throws Exception {
                assertTrue("Expecting write access to A", U.canWrite(session, ANOTHER, "/acltest/A"));
                assertFalse("Expecting no write access to B", U.canWrite(session, ANOTHER, "/acltest/A/B"));
                return null;
            }
        };
    }

    @Test
    public void namespaceAndCndRegistered() throws Exception {
        final String nodeName = "ns-" + UUID.randomUUID();
        session.getRootNode().addNode(nodeName, "slingtest:unstructured");
        session.save();
    }

    @Test
    public void fredWilmaHomeAcl() throws Exception {
        new Retry() {
            @Override
            public Void call() throws Exception {
                assertTrue("Expecting user " + FRED_WILMA, U.userExists(session, FRED_WILMA));
                final String path = U.getHomePath(session, FRED_WILMA);
                assertTrue("Expecting path " + path, session.itemExists(path));
                assertTrue("Expecting write access for alice", U.canWrite(session, ALICE, path));
                assertFalse("Expecting no access for bob", U.canRead(session, BOB, path));
                return null;
            }
        };
    }

    @Test
    public void groupMembership() throws Exception {
        new Retry() {
            @Override
            public Void call() throws Exception {
                assertTrue("Expecting user " + FRED_WILMA + "to be member of " + GROUP_A, U.isMember(session, FRED_WILMA, GROUP_A));
                assertTrue("Expecting user " + ALICE + "to be member of " + GROUP_A,U.isMember(session, ALICE, GROUP_A));
                assertTrue("Expecting user " + ANOTHER + "to be member of " + GROUP_B,U.isMember(session, ANOTHER, GROUP_B));
                assertFalse("Expecting user " + BOB + "not to be member of " + GROUP_B,U.isMember(session, BOB, GROUP_B));
                assertFalse("Expecting group " + GROUP_A + "not to be member of " + GROUP_B,U.isMember(session, GROUP_A, GROUP_B));
                return null;
            }
        };
    }
}