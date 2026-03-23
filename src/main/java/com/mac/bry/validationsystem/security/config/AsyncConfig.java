package com.mac.bry.validationsystem.security.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * FIX #3: Asynchronous Operations Execution
 * Removes I/O overhead from the primary threads (e.g. for Audit logs)
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean(name = "auditTaskExecutor")
    public Executor auditTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2); // Keep 2 threads ready at all times
        executor.setMaxPoolSize(10); // Scale up to 10 if there's a heavy load
        executor.setQueueCapacity(500); // Buffer size before reaching max pool size limits
        executor.setThreadNamePrefix("audit-");
        executor.initialize();
        return executor;
    }
}
