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

import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

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

    @SuppressWarnings("java:S2245") // we don't do crypto stuff here
    Random random = new Random();

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
    public RetryableOperationResult apply(Supplier<RetryableOperationResult> operation, String logMessage) {

        RetryableOperationResult result = operation.get();
        while (!result.isSuccessful() && result.shouldRetry() && retryCount < maxRetries) {
            retryCount++;
            LOG.info("{} (retry {}/{})", logMessage, retryCount, maxRetries);
            delay(retryCount);
            result = operation.get();
        }
        return result;
    }

    private void delay(int retryCount) {

        int j = random.nextInt(jitter);
        int delayInMilis = (backoffBase * retryCount) + j;
        try {
            TimeUnit.MILLISECONDS.sleep(delayInMilis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * A simple wrapper for the results
     */
    public static class RetryableOperationResult {

        final boolean successful;
        final boolean shouldRetry;
        final String reference;
        final Exception failureTrace;

        /**
         * simple constructor. If <code>succesful</code> is set to to true, all other
         * values are ignored and the operation is considered to be successful.
         * @param successful true if the operation was successful
         * @param shouldRetry true if it makes sense to retry the operation
         * @param reference the reference identifying the source of the repoinit script
         * @param trace the exception trace (if any)
         */
        RetryableOperationResult(boolean successful, boolean shouldRetry, String reference, Exception trace) {
            this.successful = successful;
            this.shouldRetry = shouldRetry;
            this.reference = reference;
            this.failureTrace = trace;
        }

        public boolean isSuccessful() {
            return successful;
        }

        public boolean shouldRetry() {
            return shouldRetry;
        }

        public String getReference() {
            return reference;
        }

        public Exception getFailureTrace() {
            return failureTrace;
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
            this.maxRetries = retries;
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
            return new RetryableOperation(exponentialBackoff, maxRetries, jitter);
        }
    }
}
