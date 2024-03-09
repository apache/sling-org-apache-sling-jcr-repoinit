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

import org.apache.sling.commons.metrics.MetricsService;
import org.apache.sling.jcr.repoinit.JcrRepoInitOpsProcessor;
import org.apache.sling.jcr.repoinit.impl.RetryableOperation.RetryableOperationResult;
import org.apache.sling.repoinit.parser.RepoInitParser;
import org.apache.sling.testing.mock.sling.junit.SlingContext;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import org.mockito.ArgumentMatchers;

import javax.jcr.RepositoryException;
import javax.jcr.Session;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

public class RepositoryInitializerFactoryTest {

    @Rule
    public SlingContext context = new SlingContext();

    RepositoryInitializerFactory sut;
    JcrRepoInitOpsProcessor processor;

    @Before
    public void setup() {
        RepoInitParser parser = mock(RepoInitParser.class);
        context.registerService(RepoInitParser.class, parser);
        processor = mock(JcrRepoInitOpsProcessor.class);
        context.registerService(JcrRepoInitOpsProcessor.class, processor);
        MetricsService metrics = mock(MetricsService.class);
        context.registerService(MetricsService.class, metrics);
        
        sut = new RepositoryInitializerFactory();
        context.registerInjectActivateService(sut);
    }
    
    
    @Test
    public void successfulRun() throws RepositoryException {
        // doing nothing is also considered successful ...
        sut.applyOperations(mock(Session.class), null, null);
        assertEquals(0,sut.failureStateAsMetric());
    }

    @Test
    public void handleUncheckedErrorsInOperations() throws RepositoryException {
        doThrow(new RepoInitException("some op failed", new Exception("root cause")))
            .when(processor).apply(ArgumentMatchers.any(), ArgumentMatchers.any());
        try {
            sut.applyOperations(mock(Session.class), null, null);
        } catch (RepositoryException re) {
            // expected
        } catch (Exception e) {
            fail();
        }
        assertEquals(1,sut.failureStateAsMetric());
    }

    // https://issues.apache.org/jira/browse/SLING-11276
    @Test
    public void testRetriesWithExceptions() {
        doThrow(new RepoInitException("some op failed", new Exception("root cause")))
            .when(processor).apply(ArgumentMatchers.any(), ArgumentMatchers.any());
        RetryableOperation retry = new RetryableOperation.Builder().withBackoffBaseMsec(1).withMaxRetries(3).build();
        RetryableOperationResult result = sut.applyOperationInternal(mock(Session.class), null, null, retry);
        assertEquals(3, retry.retryCount);
        assertFalse(result.isSuccessful());
    }

}
