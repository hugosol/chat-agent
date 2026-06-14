package com.hugosol.chatagent.service;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Provides a synchronous {@link ExecutorService} for integration tests,
 * so {@code @Async("optimizerExecutor")} methods complete immediately
 * on the calling thread.
 */
@TestConfiguration
class TestOptimizerExecutorConfig {

    @Bean
    @Qualifier("optimizerExecutor")
    public ExecutorService optimizerExecutor() {
        return new AbstractExecutorService() {
            private volatile boolean shutdown;

            @Override
            public void shutdown() { shutdown = true; }

            @Override
            public List<Runnable> shutdownNow() {
                shutdown = true;
                return Collections.emptyList();
            }

            @Override
            public boolean isShutdown() { return shutdown; }

            @Override
            public boolean isTerminated() { return shutdown; }

            @Override
            public boolean awaitTermination(long timeout, TimeUnit unit) { return true; }

            @Override
            public void execute(Runnable command) {
                command.run();
            }
        };
    }
}
