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

import java.io.StringReader;
import java.util.List;
import java.util.UUID;

import org.apache.sling.jcr.repoinit.JcrRepoInitOpsProcessor;
import org.apache.sling.repoinit.parser.RepoInitParser;
import org.apache.sling.repoinit.parser.operations.Operation;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerClass;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Minimal integration test that demonstrates how to use
 *  the parser and operations executor to run repoinit
 *  statements at any time.
 */
@RunWith(PaxExam.class)
@ExamReactorStrategy(PerClass.class)
public class MinimalRepoInitIT extends RepoInitTestSupport {

    @Inject
    private RepoInitParser parser;

    @Inject
    private JcrRepoInitOpsProcessor processor;

    @Test
    public void executeRepoinitStatements() throws Exception {

        // Prepare the test
        final String path = "/" + getClass().getSimpleName() + "/" + UUID.randomUUID();
        setupSession();
        assertFalse("Not expecting test path before test runs", session.itemExists(path));

        // The RepoInitParser returns a List of Operation
        final String statement = String.format("create path %s\n", path);
        final List<Operation> ops = parser.parse(new StringReader(statement));

        // That the JcrRepoInitOpsProcessor applies to a JCR repository Session
        processor.apply(session, ops);

        // Save and validate
        session.save();
        assertTrue("Expecting test nodes to be created", session.itemExists(path));
    }
}
