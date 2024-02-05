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
import org.apache.sling.jcr.api.SlingRepositoryInitializer;
import org.apache.sling.jcr.repoinit.impl.RepoInitInitializer.Validationmode;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Designate(ocd = RepositoryInitializerFactory.Config.class, factory=true)
@Component(service = SlingRepositoryInitializer.class,
    configurationPolicy=ConfigurationPolicy.OPTIONAL,
    configurationPid = "org.apache.sling.jcr.repoinit.impl.RepoInitValidator",
    property = {
            Constants.SERVICE_VENDOR + "=The Apache Software Foundation",
            // SlingRepositoryInitializers are executed in ascending
            // order of their service ranking - default set higher than ExecutionPlanRepoInitializer 
            // to detect collisions caused by packageinitialization
            Constants.SERVICE_RANKING + ":Integer=300"
    })
public class RepoInitValidator implements SlingRepositoryInitializer {
    
    @ObjectClassDefinition(name = "Apache Sling Repository Initializer Validator",
            description="Reexutes Repoinit to detect colliding content changes.")
        public @interface Config {
 
        @AttributeDefinition(name="Strict",
                    description=
                         "Fails on validation when true, otherwise only logs warn.")
            boolean strict() default false;
            
    }

    private BundleContext ctx;
    private RepoInitValidator.Config config;
    
    private final Logger log = LoggerFactory.getLogger(getClass());

    @Activate
    public void activate(final BundleContext ctx, final RepoInitValidator.Config config) {
        this.ctx = ctx;
        this.config = config;
    }

    @Override
    public void processRepository(SlingRepository repo) throws Exception {
        log.info("Starting RepoInitValidation");
        for ( ServiceReference<RepoInitInitializer> initializerRef : ctx.getServiceReferences(RepoInitInitializer.class,null)) {
            RepoInitInitializer initializer = ctx.getService(initializerRef);
            initializer.processRepository(repo, config.strict() ? Validationmode.STRICT : Validationmode.WARN);
        }
        log.info("Finished RepoInitValidation");
    }

}
