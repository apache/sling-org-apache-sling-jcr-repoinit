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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import java.util.Properties;

import javax.jcr.RepositoryException;
import javax.jcr.Session;

import org.apache.sling.jcr.api.SlingRepository;
import org.apache.sling.jcr.repoinit.JcrRepoInitOpsProcessor;
import org.apache.sling.repoinit.parser.RepoInitParser;
import org.apache.sling.testing.mock.sling.ResourceResolverType;
import org.apache.sling.testing.mock.sling.junit.SlingContext;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

public class RepositoryInitializerFactoryTest {

    @Rule
    public SlingContext context = new SlingContext(ResourceResolverType.JCR_OAK);

    RepositoryInitializerFactory sut;
    JcrRepoInitOpsProcessor processor;

    @Before
    public void setup() {
        RepoInitParser parser = mock(RepoInitParser.class);
        context.registerService(RepoInitParser.class, parser);
        processor = mock(JcrRepoInitOpsProcessor.class);
        context.registerService(JcrRepoInitOpsProcessor.class, processor);
        
        sut = new RepositoryInitializerFactory();
        context.registerInjectActivateService(sut);
    }

    @Test(expected = RepositoryException.class)
    public void handleUncheckedErrorsInOperations() throws RepositoryException {
        doThrow(new RepoInitException("some op failed", new Exception("root cause")))
            .when(processor).apply(ArgumentMatchers.any(), ArgumentMatchers.any());
        sut.applyOperations(mock(Session.class), null, null); 
    }
    
    @Test
    public void validateDeveloperMode() throws Exception {
        Properties oldProps = System.getProperties();
        try {
            System.setProperty(RepositoryInitializerFactory.PROPERTY_DEVELOPER_MODE, "true");
            RepositoryInitializerFactory spy = Mockito.spy(sut);
            doThrow(new RepositoryException("reason")).when(spy).executeScripts(any(Session.class),any(RepositoryInitializerFactory.Config.class));
            spy.processRepository(context.getService(SlingRepository.class));
        } finally {
            System.clearProperty(RepositoryInitializerFactory.PROPERTY_DEVELOPER_MODE);
        }
    }

}
