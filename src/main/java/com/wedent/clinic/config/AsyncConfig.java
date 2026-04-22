package com.wedent.clinic.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Async infrastructure.
 *
 * <p>We keep a <b>named</b>, <b>dedicated</b> executor for the audit writer so
 * that:
 * <ul>
 *   <li>Audit bursts (e.g. a password-spray attack generating thousands of
 *       failed-login events per second) can't starve the default Tomcat
 *       request pool or the default {@code @Async} executor used by other
 *       concerns.</li>
 *   <li>Operators can size it independently and see its metrics in
 *       Micrometer under the {@code executor.*} meter names tagged with
 *       {@code name=auditExecutor}.</li>
 *   <li>On overload we fall back to {@link ThreadPoolExecutor.CallerRunsPolicy}
 *       — the caller (Tomcat worker) absorbs the audit write synchronously
 *       rather than dropping the event. This prefers correctness (no lost
 *       audit rows) over tail latency during a flood.</li>
 * </ul>
 */
@Slf4j
@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean(name = "auditExecutor")
    public Executor auditExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(500);
        executor.setKeepAliveSeconds(60);
        executor.setThreadNamePrefix("audit-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        // Let in-flight audit writes finish before shutdown completes, paired
        // with the graceful-shutdown window in application.yml.
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(20);
        executor.initialize();
        return executor;
    }
}
