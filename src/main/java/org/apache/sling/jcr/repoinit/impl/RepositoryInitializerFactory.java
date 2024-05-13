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
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.jcr.InvalidItemStateException;
import javax.jcr.RepositoryException;
import javax.jcr.Session;

import org.apache.sling.commons.metrics.MetricsService;
import org.apache.sling.jcr.api.SlingRepository;
import org.apache.sling.jcr.api.SlingRepositoryInitializer;
import org.apache.sling.jcr.repoinit.JcrRepoInitOpsProcessor;
import org.apache.sling.jcr.repoinit.impl.RetryableOperation.RetryableOperationResult;
import org.apache.sling.repoinit.parser.RepoInitParser;
import org.apache.sling.repoinit.parser.operations.Operation;
import org.osgi.framework.Constants;
import org.osgi.service.component.ComponentConstants;
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

    private final Logger log = LoggerFactory.getLogger(getClass());

    private static final String METRIC_REPOINIT_FAILED = RepositoryInitializerFactory.class.getName() + ".failed";

    @Reference
    private RepoInitParser parser;

    @Reference
    private JcrRepoInitOpsProcessor processor;
    
    @Reference
    MetricsService metrics;

    private RepositoryInitializerFactory.Config config;
    
    // assume that repoinit succeeds ... and just this to true if it fails
    private AtomicBoolean aRepoInitStatementFailed = new AtomicBoolean(false);
    

    private String componentId;

    @Activate
    public void activate(final RepositoryInitializerFactory.Config config, Map<String, Object> properties) {
        this.config = config;
        this.componentId = properties.getOrDefault(ComponentConstants.COMPONENT_ID, "").toString();
        log.debug("Activated: {}", this);
        metrics.gauge(METRIC_REPOINIT_FAILED, this::failureStateAsMetric);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName()
                + ", references=" + Arrays.toString(config.references())
                + ", scripts=" + (config.scripts() != null ? config.scripts().length : 0);
    }

    @Override
    public void processRepository(final SlingRepository repo) throws Exception {
        if ( (config.references() != null && config.references().length > 0)
           || (config.scripts() != null && config.scripts().length > 0 )) {

            // loginAdministrative is ok here, definitely an admin operation
            @SuppressWarnings("deprecation")
            final Session s = repo.loginAdministrative(null);
            try {
                if ( config.references() != null ) {
                    final RepoinitTextProvider p = new RepoinitTextProvider();
                    for(final String reference : config.references()) {
                        if(reference == null || reference.trim().length() == 0) {
                            continue;
                        }
                        final String repoinitText = p.getRepoinitText("raw:" + reference);
                        final List<Operation> ops;
                        try (StringReader sr = new StringReader(repoinitText)) {
                            ops = parser.parse(sr);
                        }
                        String sourceReference = String.format("Configuration PID %s, reference URL %s", componentId, reference);
                        String msg = String.format("Executing %s repoinit operations from \"%s\"", ops.size(), sourceReference);
                        log.info(msg);
                        applyOperations(s,ops,msg, sourceReference);
                    }
                }
                if ( config.scripts() != null ) {
                    int scriptIndex = 0;
                    for(final String script : config.scripts()) {
                        if(script == null || script.trim().length() == 0) {
                            continue;
                        }
                        final List<Operation> ops;
                        try (StringReader sr = new StringReader(script)) {
                            ops = parser.parse(sr);
                        }
                        String sourceReference = String.format("Configuration PID %s, script[%d]", componentId, scriptIndex);
                        String msg = String.format("Executing %s repoinit operations from \"%s\"", ops.size(), sourceReference);
                        log.info(msg);
                        applyOperations(s,ops,msg, sourceReference);
                        scriptIndex++;
                    }
                }
            } finally {
                s.logout();
            }
        }
    }

    /**
     * Apply the operations within a session, support retries
     * @param session the JCR session to use
     * @param ops the list of operations
     * @param logMessage the messages to print when retry
     * @param reference the reference identifying the source of the repoinit script
     * @throws Exception if the application fails despite the retry
     */
    protected void applyOperations(Session session, List<Operation> ops, String logMessage, String reference) throws RepositoryException {

        RetryableOperation retry = new RetryableOperation.Builder().withBackoffBaseMsec(1000).withMaxRetries(3).build();
        RetryableOperation.RetryableOperationResult result = applyOperationInternal(session, ops, logMessage, reference, retry);
        if (!result.isSuccessful()) {
            String msg = String.format("Applying repoinit operation failed despite retry; set loglevel to DEBUG to see all exceptions. "
                    + "Last exception message from \"%s\" was: %s", result.getReference(), result.getFailureTrace().getMessage());
            aRepoInitStatementFailed.set(true);
            throw new RepositoryException(msg, result.getFailureTrace());
        }
    }

    /**
     * Perform the operations
     * @param session the session to use
     * @param ops the operations
     * @param logMessage logmessage which should be printed
     * @param reference the reference identifying the source of the repoinit script
     * @param retry the retry object
     * @return
     */
    protected RetryableOperationResult applyOperationInternal(Session session, List<Operation> ops, String logMessage,
            String reference, RetryableOperation retry) {
        return retry.apply(() -> {
            try {
                processor.apply(session, ops);
                session.save();
                return new RetryableOperation.RetryableOperationResult(true,false,reference,null);
            } catch (InvalidItemStateException|RepoInitException ex) {
                // a retry makes sense, because this exception might be caused by an concurrent operation
                log.debug("(temporarily) failed to apply repoinit operations",ex);
                try {
                    session.refresh(false); // discard all pending changes
                } catch (RepositoryException e1) {
                    // ignore
                }
                return new RetryableOperation.RetryableOperationResult(false,true,reference,ex);
            } catch (RepositoryException ex) {
                // a permanent error, retry is not useful
                try {
                    session.refresh(false); // discard all pending changes
                } catch (RepositoryException e1) {
                    // ignore
                }
                return new RetryableOperation.RetryableOperationResult(false,false,reference,ex);
            }
        }, logMessage);
    }
    
    /**
     * return the state of the repoinit execution usable for a simple gauge metric
     * @return 1 if repoinit did not finish successfully, 0 otherwise
     */
    protected int failureStateAsMetric() {
        return aRepoInitStatementFailed.get() ? 1 : 0;
    }
    


}
