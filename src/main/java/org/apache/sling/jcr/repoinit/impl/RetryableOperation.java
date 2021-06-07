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

import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A simple implementation of retryable operations.
 * Use the builder class to create an instance of it.
 *
 */
public class RetryableOperation {

    private static final Logger LOG = LoggerFactory.getLogger(RetryableOperation.class);

    int backoffBase;
    int maxRetries;
    int jitter;

    int retryCount = 0;

    RetryableOperation(int backoff, int maxRetries, int jitter) {
        this.backoffBase = backoff;
        this.maxRetries = maxRetries;
        this.jitter = jitter;
    }
    /**
     * Execute the operation with the defined retry until it returns true or
     * the retry aborts; in case the operation is retried, a log message is logged on INFO with the
     * provided logMessage and the current number of retries
     * @param operation
     * @param logMessage the log message
     * @return true if the supplier was eventually successful, false if it failed despite all retries
     */
    public boolean apply(BooleanSupplier operation, String logMessage) {

        boolean successful = false;
        successful = operation.getAsBoolean();
        while (! successful && retryCount < maxRetries) {
            retryCount++;
            LOG.info("{} (retry {}/{})", logMessage, retryCount, maxRetries);
            delay(retryCount);
            successful = operation.getAsBoolean();
        }
        return successful;
    }

    private void delay(int retryCount) {

        Random r = new Random();
        int j = r.nextInt(jitter);
        int delayInMilis = (backoffBase * retryCount) + j;
        try {
            TimeUnit.MILLISECONDS.sleep(delayInMilis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }



    public static class Builder {

        int exponentialBackoff = 1000; // default
        int maxRetries = 3; // default
        int jitter = 200;

        /**
         * The backoff time
         * @param msec backoff time in miliseconds
         * @return the builder
         */
        Builder withBackoffBaseMsec(int msec) {
            exponentialBackoff = msec;
            return this;
        }

        /**
         * Configures the number of retries;
         * @param retries number of retries
         * @return the builder
         */
        Builder withMaxRetries(int retries) {
            this.maxRetries= retries;
            return this;
        }

        /**
         * configures the jitter
         * @param msec the jitter in miliseconds
         * @return the builder
         */
        Builder withJitterMsec(int msec) {
            this.jitter = msec;
            return this;
        }

        RetryableOperation build() {
            return new RetryableOperation(exponentialBackoff,maxRetries, jitter);
        }
    }

}
