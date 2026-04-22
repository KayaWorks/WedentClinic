package com.wedent.clinic.config;

import io.micrometer.core.aop.TimedAspect;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.config.MeterFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.autoconfigure.metrics.MeterRegistryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Micrometer wiring:
 *
 * <ul>
 *   <li>Default tags ({@code application}, {@code profile}) stamped on every metric
 *       so the Prometheus scrape can safely union multiple environments.</li>
 *   <li>{@link TimedAspect} so {@code @Timed} works on any Spring bean method
 *       without having to write instrumentation by hand.</li>
 *   <li>{@link MeterFilter#denyNameStartsWith} drops a few high-cardinality
 *       default meters we don't care about, keeping the scrape payload small.</li>
 * </ul>
 */
@Configuration
public class MetricsConfig {

    @Bean
    public MeterRegistryCustomizer<MeterRegistry> commonTags(
            @Value("${spring.application.name:wedent-clinic}") String app,
            @Value("${spring.profiles.active:default}") String profile) {
        return registry -> registry.config()
                .commonTags("application", app, "profile", profile)
                .meterFilter(MeterFilter.denyNameStartsWith("jvm.buffer"))
                .meterFilter(MeterFilter.denyNameStartsWith("logback.events"));
    }

    @Bean
    public TimedAspect timedAspect(MeterRegistry registry) {
        return new TimedAspect(registry);
    }
}
