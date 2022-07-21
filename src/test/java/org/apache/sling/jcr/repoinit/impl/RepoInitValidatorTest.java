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



import org.apache.sling.jcr.api.SlingRepository;
import org.apache.sling.repoinit.parser.impl.RepoInitParserService;
import org.apache.sling.testing.mock.osgi.MockOsgi;
import org.apache.sling.testing.mock.sling.ResourceResolverType;
import org.apache.sling.testing.mock.sling.junit.SlingContext;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import org.assertj.core.api.Assertions;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;
import java.util.UUID;

public class RepoInitValidatorTest {

    @Rule
    public SlingContext context = new SlingContext(ResourceResolverType.JCR_OAK);
    
    

    RepositoryInitializer initializer;
    RepositoryInitializerFactory factoryInitializer; 
    SlingRepository rep;
    
    private ListAppender<ILoggingEvent> listAppender;

    @Before 
    public void setup() throws IOException {
        
        context.registerInjectActivateService(new RepoInitParserService());
        context.registerInjectActivateService(new JcrRepoInitOpsProcessorImpl());
                
        initializer = new RepositoryInitializer();
        
        String serviceUser = getClass().getSimpleName() + "-" + UUID.randomUUID();
        String feature = "[feature name=bar]\n[:repoinit]\n" + " create service user " + serviceUser + "\n";
        String url = getTestUrl(feature);
        Map<String, Object> initializerConfig = new HashMap<String, Object>();
        initializerConfig.put("references", new String[] {"model:" + url});
        
        context.registerInjectActivateService(initializer, initializerConfig);
        
        factoryInitializer = new RepositoryInitializerFactory();
        
        String serviceUser2 = getClass().getSimpleName() + "-" + UUID.randomUUID();
        String txt2 = "create service user " + serviceUser2 + "\n";
        Map<String, Object> initializer2Config = new HashMap<String, Object>();
        initializer2Config.put("scripts", new String[] {txt2});

       
        context.registerInjectActivateService(factoryInitializer, initializer2Config);

        rep = context.getService(SlingRepository.class);
        
        final Logger logger = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        listAppender = new ListAppender<>();
        listAppender.start();
        logger.addAppender(listAppender);
    }
    
    @After
    public void teardown() {
        final Logger logger = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        logger.detachAppender(listAppender);
    }
    
    @Test (expected=RepoInitException.class)
    public void testStrictWithChanges() throws Exception {
        Dictionary<String, Object> props = new Hashtable<String, Object>();
        props.put("strict", true);
        MockOsgi.setConfigForPid(context.bundleContext(), RepoInitValidator.class.getName(), props);
        
        RepoInitValidator sut = new RepoInitValidator();
        context.registerInjectActivateService(sut, props);
        // apply validator w/o executing repoinit at first forces the precondition not to be met
        sut.processRepository(rep);
        MockOsgi.deactivate(sut, context.bundleContext());
    }
    
    
    @Test (expected=RepoInitException.class)
    public void testStrictWithInitializerOnlyChanges() throws Exception {
        Dictionary<String, Object> props = new Hashtable<String, Object>();
        props.put("strict", true);
        MockOsgi.setConfigForPid(context.bundleContext(), RepoInitValidator.class.getName(), props);
        
        RepoInitValidator sut = new RepoInitValidator();
        context.registerInjectActivateService(sut, props);
        // no initializer.processRepository(rep) - forcing difference
        factoryInitializer.processRepository(rep);
        sut.processRepository(rep);
        MockOsgi.deactivate(sut, context.bundleContext());
    }
    
    
    @Test (expected=RepoInitException.class)
    public void testStrictWithFactoryOnlyChanges() throws Exception {
        Dictionary<String, Object> props = new Hashtable<String, Object>();
        props.put("strict", true);
        MockOsgi.setConfigForPid(context.bundleContext(), RepoInitValidator.class.getName(), props);
        
        RepoInitValidator sut = new RepoInitValidator();
        context.registerInjectActivateService(sut, props);
        initializer.processRepository(rep);
        // no factoryInitializer.processRepository(rep) - forcing difference
        sut.processRepository(rep);
        MockOsgi.deactivate(sut, context.bundleContext());
    }
    
    
    @Test
    public void testWarnWithChanges() throws Exception {
        Dictionary<String, Object> props = new Hashtable<String, Object>();
        props.put("strict", false);
        MockOsgi.setConfigForPid(context.bundleContext(), RepoInitValidator.class.getName(), props);
        
        RepoInitValidator sut = new RepoInitValidator();
        context.registerInjectActivateService(sut, props);
        // apply validator w/o executing repoinit at first forces the precondition not to be met
        // warn should only lead to warn but not cause an exception.
        
        sut.processRepository(rep);

        Assertions.assertThat(listAppender.list)
            .extracting(ILoggingEvent::getFormattedMessage)
            .contains(RepositoryInitializer.WARNMESSAGE);
     
        MockOsgi.deactivate(sut, context.bundleContext());
    }
    
    
    @Test
    public void testStrictWithOutChanges() throws Exception {
        Dictionary<String, Object> props = new Hashtable<String, Object>();
        props.put("strict", true);
        MockOsgi.setConfigForPid(context.bundleContext(), RepoInitValidator.class.getName(), props);
        
        RepoInitValidator sut = new RepoInitValidator();
        context.registerInjectActivateService(sut, props);
        initializer.processRepository(rep);
        factoryInitializer.processRepository(rep);
        sut.processRepository(rep);
        
        Assertions.assertThat(listAppender.list)
        .extracting(ILoggingEvent::getFormattedMessage)
        .doesNotContain(RepositoryInitializer.WARNMESSAGE);

        
        MockOsgi.deactivate(sut, context.bundleContext());
    }
    
    
    /** Return the URL of a temporary file that contains repoInitText */
    private String getTestUrl(String repoInitText) throws IOException {
        final File tmpFile = File.createTempFile(getClass().getSimpleName(), "txt");
        tmpFile.deleteOnExit();
        final FileWriter w = new FileWriter(tmpFile);
        w.write(repoInitText);
        w.flush();
        w.close();
        return tmpFile.toURI().toURL().toString();
    }

}
