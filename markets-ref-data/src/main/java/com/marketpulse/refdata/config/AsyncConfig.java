package com.marketpulse.refdata.config;

import java.util.concurrent.Executor;
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
}
