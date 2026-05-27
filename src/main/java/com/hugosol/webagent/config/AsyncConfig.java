package com.hugosol.webagent.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

@Configuration
public class AsyncConfig {

    private static final Logger log = LoggerFactory.getLogger(AsyncConfig.class);

    @Value("${app.memory.async:true}")
    private boolean asyncEnabled;

    @Bean
    @Qualifier("llmLogExecutor")
    public ExecutorService llmLogExecutor() {
        if (!asyncEnabled) {
            log.info("LLM log executor configured as synchronous (e2e/test mode)");
            return new DirectExecutorService();
        }

        ThreadFactory threadFactory = r -> {
            ThreadFactory defaultFactory = Executors.defaultThreadFactory();
            Thread t = defaultFactory.newThread(r);
            t.setName("llm-log-" + t.getName());
            t.setDaemon(true);
            return t;
        };

        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                2,
                4,
                60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(),
                threadFactory,
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
        executor.allowCoreThreadTimeOut(true);
        log.info("LLM log executor configured: core=2, max=4");
        return executor;
    }

    @Bean
    @Qualifier("memoryExecutor")
    public ExecutorService memoryExecutor() {
        if (!asyncEnabled) {
            log.info("Memory executor configured as synchronous (e2e/test mode)");
            return new DirectExecutorService();
        }

        ThreadFactory threadFactory = r -> {
            ThreadFactory defaultFactory = Executors.defaultThreadFactory();
            Thread t = defaultFactory.newThread(r);
            t.setName("memory-" + t.getName());
            t.setDaemon(true);
            return t;
        };

        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                4,
                8,
                60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(),
                threadFactory,
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
        executor.allowCoreThreadTimeOut(true);
        log.info("Memory executor configured: core=4, max=8, async mode");
        return executor;
    }

    @Bean
    @Qualifier("embeddingExecutor")
    public ExecutorService embeddingExecutor() {
        if (!asyncEnabled) {
            log.info("Embedding executor configured as synchronous (e2e/test mode)");
            return new DirectExecutorService();
        }

        ThreadFactory threadFactory = r -> {
            ThreadFactory defaultFactory = Executors.defaultThreadFactory();
            Thread t = defaultFactory.newThread(r);
            t.setName("embedding-" + t.getName());
            t.setDaemon(true);
            return t;
        };

        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                2,
                2,
                60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(),
                threadFactory,
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
        executor.allowCoreThreadTimeOut(true);
        log.info("Embedding executor configured: core=2, max=2");
        return executor;
    }

    private static class DirectExecutorService extends AbstractExecutorService {

        private volatile boolean shutdown;

        @Override
        public void execute(Runnable command) {
            command.run();
        }

        @Override
        public void shutdown() {
            shutdown = true;
        }

        @Override
        public java.util.List<Runnable> shutdownNow() {
            shutdown = true;
            return java.util.List.of();
        }

        @Override
        public boolean isShutdown() {
            return shutdown;
        }

        @Override
        public boolean isTerminated() {
            return shutdown;
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) {
            return true;
        }
    }
}
