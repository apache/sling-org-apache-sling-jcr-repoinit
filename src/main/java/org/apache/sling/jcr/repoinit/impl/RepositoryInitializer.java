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
package org.apache.sling.jcr.repoinit.impl;

import javax.jcr.RepositoryException;
import javax.jcr.Session;

import java.io.IOException;
import java.io.StringReader;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import org.apache.sling.jcr.api.SlingRepository;
import org.apache.sling.jcr.api.SlingRepositoryInitializer;
import org.apache.sling.jcr.repoinit.JcrRepoInitOpsProcessor;
import org.apache.sling.repoinit.parser.RepoInitParser;
import org.apache.sling.repoinit.parser.RepoInitParsingException;
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

/** SlingRepositoryInitializer that executes repoinit statements read
 *  from a configurable URL.
 */
@Designate(ocd = RepositoryInitializer.Config.class)
@Component(
        service = SlingRepositoryInitializer.class,
        configurationPolicy = ConfigurationPolicy.REQUIRE,
        property = {
            Constants.SERVICE_VENDOR + "=The Apache Software Foundation",
            // SlingRepositoryInitializers are executed in ascending
            // order of their service ranking
            Constants.SERVICE_RANKING + ":Integer=100"
        })
public class RepositoryInitializer implements SlingRepositoryInitializer {

    @ObjectClassDefinition(
            name = "Apache Sling Repository Initializer",
            description = "Initializes the JCR content repository using repoinit statements")
    public @interface Config {

        @AttributeDefinition(
                name = "Repoinit references",
                description = "References to the source text that provides repoinit statements."
                        + " format is either model@repoinit:<provisioning model URL> or raw:<raw URL>")
        String[] references() default {};
    }

    private final Logger log = LoggerFactory.getLogger(getClass());

    @Reference
    private RepoInitParser parser;

    @Reference
    private JcrRepoInitOpsProcessor processor;

    private Config config;

    @Activate
    public void activate(Config config) {
        this.config = config;
        log.debug("Activated: {}", this);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + ", references=" + Arrays.toString(config.references());
    }

    @Override
    public void processRepository(SlingRepository repo) throws RepositoryException {
        if (config.references() != null && config.references().length > 0) {
            // loginAdministrative is ok here, definitely an admin operation
            @SuppressWarnings("deprecation")
            final Session s = repo.loginAdministrative(null);
            try {
                Instant start = Instant.now();
                final RepoinitTextProvider p = new RepoinitTextProvider();
                for (String reference : config.references()) {
                    try {
                        final String repoinitText = p.getRepoinitText(reference);
                        final List<Operation> ops;
                        try (StringReader sr = new StringReader(repoinitText)) {
                            ops = parser.parse(sr);
                        }
                        log.info("Executing {} repoinit operations from {}", ops.size(), reference);
                        processor.apply(s, ops);
                        if (s.hasPendingChanges()) {
                            s.save();
                        }
                    } catch (IOException | RuntimeException | RepositoryException | RepoInitParsingException e) {
                        throw new RepoInitException("Error executing repoinit from " + reference, e);
                    }
                }
                Duration duration = Duration.between(start, Instant.now());
                log.info("Total time for successful repoinit execution: {} miliseconds", duration.toMillis());
            } finally {
                s.logout();
            }
        }
    }
}
