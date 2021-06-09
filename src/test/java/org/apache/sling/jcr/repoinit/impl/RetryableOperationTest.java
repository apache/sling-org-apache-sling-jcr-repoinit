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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import org.junit.Test;


public class RetryableOperationTest {


    @Test
    public void testWithoutRetry() {

        RetryableOperation ro = new RetryableOperation.Builder().build();
        Supplier<RetryableOperation.RetryableOperationResult> op = () -> {
            return new RetryableOperation.RetryableOperationResult(true,false,null);
        };
        RetryableOperation.RetryableOperationResult result = ro.apply(op, "log");
        assertEquals(0,ro.retryCount);
        assertTrue(result.isSuccessful());
    }

    @Test
    public void testWithRetrySuccesful() {

        // bypass the effective final feature
        AtomicInteger retries = new AtomicInteger(0);
        RetryableOperation ro = new RetryableOperation.Builder()
                .withBackoffBaseMsec(10)
                .withMaxRetries(3)
                .build();
        Supplier<RetryableOperation.RetryableOperationResult> op = () -> {
            // 1 regular execution + 2 retries
            if (retries.getAndAdd(1) == 2) {
                return new RetryableOperation.RetryableOperationResult(true,false,null);
            } else {
                return new RetryableOperation.RetryableOperationResult(false,true,new RuntimeException());
            }
        };
        RetryableOperation.RetryableOperationResult result = ro.apply(op, "log");
        assertEquals(2,ro.retryCount);
        assertTrue(result.isSuccessful());
    }

    @Test
    public void testWithRetryFail() {

        AtomicInteger retries = new AtomicInteger(0);
        RetryableOperation ro = new RetryableOperation.Builder()
                .withBackoffBaseMsec(10)
                .withMaxRetries(3)
                .build();
        Supplier<RetryableOperation.RetryableOperationResult> op = () -> {
            // 1 regular execution + 4 retries
            if (retries.getAndAdd(1) == 4) {
                return new RetryableOperation.RetryableOperationResult(true,false,null);
            } else {
                return new RetryableOperation.RetryableOperationResult(false,true, new RuntimeException());
            }
        };
        RetryableOperation.RetryableOperationResult result = ro.apply(op, "log");
        assertEquals(3,ro.retryCount); //only 3 retries and then stopped
        assertFalse(result.isSuccessful());
    }

    @Test
    public void testWithPermanentFailure() {

        RetryableOperation ro = new RetryableOperation.Builder()
                .withBackoffBaseMsec(10)
                .withMaxRetries(3)
                .build();
        Supplier<RetryableOperation.RetryableOperationResult> op = () -> {
            // indicate a permanent failure, where it doesn't make sense to retry
            return new RetryableOperation.RetryableOperationResult(false,false, new RuntimeException());
        };
        RetryableOperation.RetryableOperationResult result = ro.apply(op, "log");
        assertEquals(0,ro.retryCount); // no retry
        assertFalse(result.isSuccessful());
    }

}
