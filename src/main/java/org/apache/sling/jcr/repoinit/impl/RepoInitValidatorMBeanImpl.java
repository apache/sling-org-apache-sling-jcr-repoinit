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

import javax.management.NotCompliantMBeanException;
import javax.management.StandardMBean;

import org.apache.sling.jcr.api.SlingRepository;
import org.apache.sling.jcr.repoinit.impl.RepoInitInitializer.Validationmode;
import org.osgi.framework.BundleContext;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * MBean of the RepoInitValidator
 */
public class RepoInitValidatorMBeanImpl extends StandardMBean implements RepoInitValidatorMBean {
    
    SlingRepository repo;
    private BundleContext context;
    
    private final static Logger log = LoggerFactory.getLogger(RepoInitValidatorMBeanImpl.class);


    protected RepoInitValidatorMBeanImpl(SlingRepository repo, BundleContext context) throws NotCompliantMBeanException {
        super(RepoInitValidatorMBean.class);
        this.repo = repo;
        this.context = context;
    }

    public boolean isRepoInitDivergingFromRepository() {

        boolean result = false;
        try {
            log.info("Starting RepoInitValidation");
            for (ServiceReference<RepoInitInitializer> initializerRef : context.getServiceReferences(
                RepoInitInitializer.class, null)) {
                RepoInitInitializer initializer = context.getService(
                    initializerRef);
                try {
                    initializer.processRepository(repo, Validationmode.STRICT);
                } catch (Exception e) {
                    if (RepositoryInitializer.EXCEPTION_MESSAGE_VALIDATION_FAILED.equals(
                        e.getMessage())) {
                        result = true;
                    } else {
                        log.error("RepoInitValidation failed", e);
                    }
                }
                if (result) {
                    break;
                }
            }
        } catch (InvalidSyntaxException e) {
            log.error(
                "Not able to determine Repoinit difference because of syntax exceptions",
                e);
        }
        log.info("Finished RepoInitValidation");
        return result;
    }

}