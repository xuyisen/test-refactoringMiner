

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
package org.apache.commons.lang3.concurrent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.commons.lang3.AbstractLangTest;
import org.apache.commons.lang3.ThreadUtils;
import org.junit.jupiter.api.Test;

public class BackgroundInitializerTest extends AbstractLangTest {
    // ... other methods remain unchanged ...

    /**
     * Tests the execution of the background task if a temporary executor has to
     * be created.
     */
    @Test
    public void testInitializeTempExecutor() throws ConcurrentException {
        final BackgroundInitializerTestImpl init = new BackgroundInitializerTestImpl();
        assertTrue(init.start(), "Wrong result of start()");
        assertInitializationAndShutdown(init);
    }

    private void assertInitializationAndShutdown(BackgroundInitializerTestImpl init) throws ConcurrentException {
        checkInitialize(init);
        assertTrue(init.getActiveExecutor().isShutdown(), "Executor not shutdown");
    }

    // ... other methods remain unchanged ...

    private static final class BackgroundInitializerTestImpl extends
            BackgroundInitializer<Integer> {
        /** An exception to be thrown by initialize(). */
        Exception ex;

        /** A flag whether the background task should sleep a while. */
        boolean shouldSleep;

        /** The number of invocations of initialize(). */
        volatile int initializeCalls;

        /** A latch tests can use to control when initialize completes. */
        final CountDownLatch latch = new CountDownLatch(1);
        boolean waitForLatch = false;

        BackgroundInitializerTestImpl() {
        }

        BackgroundInitializerTestImpl(final ExecutorService exec) {
            super(exec);
        }

        public void enableLatch() {
            waitForLatch = true;
        }

        public void releaseLatch() {
            latch.countDown();
        }

        /**
         * Records this invocation. Optionally throws an exception or sleeps a
         * while.
         *
         * @throws Exception in case of an error
         */
        @Override
        protected Integer initialize() throws Exception {
            if (ex != null) {
                throw ex;
            }
            handleDelay();
            return Integer.valueOf(++initializeCalls);
        }

        private void handleDelay() throws InterruptedException {
            if (shouldSleep) {
                ThreadUtils.sleep(Duration.ofMinutes(1));
            }
            if (waitForLatch) {
                latch.await();
            }
        }
    }
}