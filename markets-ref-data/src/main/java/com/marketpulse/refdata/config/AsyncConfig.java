package com.marketpulse.refdata.config;

import java.util.concurrent.Executor;
import org.springframework.boot.task.ThreadPoolTaskExecutorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/** Async plumbing for long-running backfill jobs. */
@Configuration
@EnableAsync
public class AsyncConfig {

    /**
     * Single-threaded on purpose: two backfills must never hit NSE concurrently, which
     * would double our request rate and risk being blocked mid-run.
     */
    @Bean("backfillExecutor")
    public Executor backfillExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(10);
        executor.setThreadNamePrefix("backfill-");
        executor.initialize();
        return executor;
    }

    /**
     * Boot's TaskExecutorConfiguration is @ConditionalOnMissingBean(Executor.class), so declaring
     * backfillExecutor above would otherwise suppress the application's default executor - leaving
     * any future unqualified @Async on an unbounded SimpleAsyncTaskExecutor. Re-declare it.
     *
     * <p>Both names are required. Boot registers its default under "applicationTaskExecutor" AND
     * "taskExecutor", and when several TaskExecutor beans exist Spring's async resolution falls back
     * only to one literally named "taskExecutor" - so registering the first name alone still leaves
     * an unqualified @Async on SimpleAsyncTaskExecutor.
     */
    @Bean(name = {"applicationTaskExecutor", "taskExecutor"})
    public ThreadPoolTaskExecutor applicationTaskExecutor(ThreadPoolTaskExecutorBuilder builder) {
        return builder.build();
    }
}
