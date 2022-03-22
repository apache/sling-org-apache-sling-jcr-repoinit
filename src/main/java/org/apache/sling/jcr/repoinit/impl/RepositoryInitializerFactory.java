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

import java.io.StringReader;
import java.util.Arrays;
import java.util.List;

import javax.jcr.InvalidItemStateException;
import javax.jcr.RepositoryException;
import javax.jcr.Session;

import org.apache.sling.jcr.api.SlingRepository;
import org.apache.sling.jcr.api.SlingRepositoryInitializer;
import org.apache.sling.jcr.repoinit.JcrRepoInitOpsProcessor;
import org.apache.sling.repoinit.parser.RepoInitParser;
import org.apache.sling.repoinit.parser.operations.Operation;
import org.osgi.framework.Constants;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * SlingRepositoryInitializer Factory that executes repoinit statements configured
 * through OSGi configurations. A configuration can contain URLs from which the
 * statements are read or inlined statements.
 */
@Designate(ocd = RepositoryInitializerFactory.Config.class, factory=true)
@Component(service = SlingRepositoryInitializer.class,
    configurationPolicy=ConfigurationPolicy.REQUIRE,
    configurationPid = "org.apache.sling.jcr.repoinit.RepositoryInitializer",
    property = {
            Constants.SERVICE_VENDOR + "=The Apache Software Foundation",
            // SlingRepositoryInitializers are executed in ascending
            // order of their service ranking
            Constants.SERVICE_RANKING + ":Integer=100"
    })
public class RepositoryInitializerFactory implements SlingRepositoryInitializer {

    @ObjectClassDefinition(name = "Apache Sling Repository Initializer Factory",
            description="Initializes the JCR content repository using repoinit statements.")
        public @interface Config {

            @AttributeDefinition(name="References",
                description=
                     "References to the source text that provides repoinit statements."
                    + " format is a raw URL.")
            String[] references() default {};

            @AttributeDefinition(name="Scripts",
                    description=
                         "Contents of a repo init script.")
            String[] scripts() default {};
    }

    protected static final String PROPERTY_DEVELOPER_MODE = "org.apache.sling.jcr.repoinit.developermode";
    
    private final Logger log = LoggerFactory.getLogger(getClass());


    @Reference
    private RepoInitParser parser;

    @Reference
    private JcrRepoInitOpsProcessor processor;

    private RepositoryInitializerFactory.Config config;

    @Activate
    public void activate(final RepositoryInitializerFactory.Config config) {
        this.config = config;
        if (isDeveloperModeEnabled()) {
            log.info("Activated: {} (developer mode active)", this.toString());
        } else {
            log.debug("Activated: {}", this.toString());
        }
    }

    @Override
    public String toString() {
        return getClass().getSimpleName()
                + ", references=" + Arrays.toString(config.references())
                + ", scripts=" + (config.scripts() != null ? config.scripts().length : 0);
    }

    @Override
    public void processRepository(final SlingRepository repo) throws Exception {
        // loginAdministrative is ok here, definitely an admin operation
        final Session s = repo.loginAdministrative(null);
        try {
            executeScripts(s, config);
        } catch (Exception e) {
            if (isDeveloperModeEnabled()) {
                log.error("Repoinit error, won't stop execution because {} is set. Without that "
                        + "developer option the startup would fail.",PROPERTY_DEVELOPER_MODE,e);
            } else {
                throw (e);
            }
        } finally {
            s.logout();
        }
    }


    protected void executeScripts (Session session, RepositoryInitializerFactory.Config config) throws Exception {
        if ( (config.references() != null && config.references().length > 0)
                || (config.scripts() != null && config.scripts().length > 0 )) {
            if ( config.references() != null ) {
                final RepoinitTextProvider p = new RepoinitTextProvider();
                for(final String reference : config.references()) {
                    if(reference == null || reference.trim().length() == 0) {
                        continue;
                    }
                    final String repoinitText = p.getRepoinitText("raw:" + reference);
                    final List<Operation> ops = parser.parse(new StringReader(repoinitText));
                    String msg = String.format("Executing %s repoinit operations", ops.size());
                    log.info(msg);
                    applyOperations(session,ops,msg);
                }
            }
            if ( config.scripts() != null ) {
                for(final String script : config.scripts()) {
                    if(script == null || script.trim().length() == 0) {
                        continue;
                    }
                    final List<Operation> ops = parser.parse(new StringReader(script));
                    String msg = String.format("Executing %s repoinit operations", ops.size());
                    log.info(msg);
                    applyOperations(session,ops,msg);
                }
            }
        }
    }

    /**
     * Apply the operations within a session, support retries
     * @param session the JCR session to use
     * @param ops the list of operations
     * @param logMessage the messages to print when retry
     * @throws Exception if the application fails despite the retry
     */
    protected void applyOperations(Session session, List<Operation> ops, String logMessage) throws RepositoryException {

        RetryableOperation retry = new RetryableOperation.Builder().withBackoffBaseMsec(1000).withMaxRetries(3).build();
        RetryableOperation.RetryableOperationResult result = retry.apply(() -> {
            try {
                processor.apply(session, ops);
                session.save();
                return new RetryableOperation.RetryableOperationResult(true,false,null);
            } catch (InvalidItemStateException ise) {
                // a retry makes sense, because this exception might be caused by an concurrent operation
                log.debug("(temporarily) failed to apply repoinit operations",ise);
                try {
                    session.refresh(false); // discard all pending changes
                } catch (RepositoryException e1) {
                    // ignore
                }
                return new RetryableOperation.RetryableOperationResult(false,true,ise);
            } catch (RepositoryException|RepoInitException ex) {
                // a permanent error, retry is not useful
                try {
                    session.refresh(false); // discard all pending changes
                } catch (RepositoryException e1) {
                    // ignore
                }
                return new RetryableOperation.RetryableOperationResult(false,false,ex);
            }
        }, logMessage);
        if (!result.isSuccessful()) {
            String msg = String.format("Applying repoinit operation failed despite retry; set loglevel to DEBUG to see all exceptions. "
                    + "Last exception message was: %s", result.getFailureTrace().getMessage());
            throw new RepositoryException(msg, result.getFailureTrace());
        }
    }

    
    protected boolean isDeveloperModeEnabled() {
        String dm = System.getProperty(PROPERTY_DEVELOPER_MODE,"");
        return dm.toLowerCase().equals("true");
    }

}
