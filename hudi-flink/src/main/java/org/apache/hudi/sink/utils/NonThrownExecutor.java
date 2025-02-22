/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.hudi.sink.utils;

import org.apache.flink.util.ExceptionUtils;
import org.apache.flink.util.function.ThrowingRunnable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * An executor service that catches all the throwable with logging.
 */
public class NonThrownExecutor implements AutoCloseable {
  private static final Logger LOG = LoggerFactory.getLogger(NonThrownExecutor.class);

  /**
   * A single-thread executor to handle all the asynchronous jobs.
   */
  private final ExecutorService executor;

  public NonThrownExecutor() {
    this.executor = Executors.newSingleThreadExecutor();
  }

  /**
   * Run the action in a loop.
   */
  public void execute(
      final ThrowingRunnable<Throwable> action,
      final String actionName,
      final String instant) {

    executor.execute(
        () -> {
          try {
            action.run();
            LOG.info("Executor executes action [{}] for instant [{}] success!", actionName, instant);
          } catch (Throwable t) {
            // if we have a JVM critical error, promote it immediately, there is a good
            // chance the
            // logging or job failing will not succeed any more
            ExceptionUtils.rethrowIfFatalErrorOrOOM(t);
            final String errMsg = String.format("Executor executes action [%s] error", actionName);
            LOG.error(errMsg, t);
          }
        });
  }

  @Override
  public void close() throws Exception {
    if (executor != null) {
      executor.shutdownNow();
      // We do not expect this to actually block for long. At this point, there should
      // be very few task running in the executor, if any.
      executor.awaitTermination(Long.MAX_VALUE, TimeUnit.SECONDS);
    }
  }
}
