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

import java.util.Dictionary;
import java.util.Hashtable;

import org.apache.sling.jcr.api.SlingRepository;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
*
 */
@Component(immediate = true,  property = {
        Constants.SERVICE_VENDOR + "=The Apache Software Foundation"})
public class RepoInitValidatorService {

    private final static Logger logger = LoggerFactory.getLogger(RepoInitValidatorService.class);

    
    private ServiceRegistration<?> mbeanRegistration;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    private SlingRepository repo;

    @Activate
    public void activate(BundleContext context) {
        registerMBean(context);
    }
    

    /**
     * Deactivate this service
     */
    @Deactivate
    protected void deactivate() {
        try{
            if ( this.mbeanRegistration != null ) {
                this.mbeanRegistration.unregister();
                this.mbeanRegistration = null;
            }
        } catch(Exception e) {
            logger.error("deactivate: Error on unregister: "+e, e);
        }
    }
    
    
    protected void registerMBean(BundleContext bundleContext) {
        if (this.mbeanRegistration!=null) {
            try{
                if ( this.mbeanRegistration != null ) {
                    this.mbeanRegistration.unregister();
                    this.mbeanRegistration = null;
                }
            } catch(Exception e) {
                logger.error("registerMBean: Error on unregister: "+e, e);
            }
        }
        try {
            final Dictionary<String, String> mbeanProps = new Hashtable<String, String>();
            mbeanProps.put("jmx.objectname", "org.apache.sling:type=repoinit,name=RepoInitValidatorService");

            final RepoInitValidatorMBeanImpl mbean = new RepoInitValidatorMBeanImpl(repo, bundleContext);
            this.mbeanRegistration = bundleContext.registerService(RepoInitValidatorMBeanImpl.class.getName(), mbean, mbeanProps);
        } catch (Throwable t) {
            logger.warn("registerMBean: Unable to register RepoInitValidatorMBeanImpl MBean", t);
        }
    }

}
