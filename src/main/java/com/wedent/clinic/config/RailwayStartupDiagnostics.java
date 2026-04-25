package com.wedent.clinic.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.util.Arrays;

@Slf4j
@Component
@Profile("railway")
public class RailwayStartupDiagnostics implements ApplicationRunner {

    private final Environment environment;

    public RailwayStartupDiagnostics(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void run(ApplicationArguments args) {
        DatabaseTarget databaseTarget = databaseTarget(environment.getProperty("spring.datasource.url"));
        log.info("Railway runtime profiles={}", String.join(",", environment.getActiveProfiles()));
        log.info("Railway database target host={} port={} database={}",
                databaseTarget.host(), databaseTarget.port(), databaseTarget.database());
        log.info("Railway Redis URL configured={}", StringUtils.hasText(environment.getProperty("REDIS_URL")));
        log.info("Railway CORS allowed origins count={}", corsOriginCount(environment.getProperty("app.cors.allowed-origins")));
    }

    private static DatabaseTarget databaseTarget(String jdbcUrl) {
        if (!StringUtils.hasText(jdbcUrl) || !jdbcUrl.startsWith("jdbc:")) {
            return DatabaseTarget.unknown();
        }
        try {
            URI uri = URI.create(jdbcUrl.substring("jdbc:".length()).trim());
            String database = uri.getPath();
            if (database != null && database.startsWith("/")) {
                database = database.substring(1);
            }
            return new DatabaseTarget(
                    valueOrUnknown(uri.getHost()),
                    uri.getPort() > 0 ? String.valueOf(uri.getPort()) : "default",
                    valueOrUnknown(database));
        } catch (IllegalArgumentException ex) {
            return DatabaseTarget.unknown();
        }
    }

    private static int corsOriginCount(String origins) {
        if (!StringUtils.hasText(origins)) {
            return 0;
        }
        return (int) Arrays.stream(origins.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .count();
    }

    private static String valueOrUnknown(String value) {
        return StringUtils.hasText(value) ? value : "unknown";
    }

    private record DatabaseTarget(String host, String port, String database) {
        static DatabaseTarget unknown() {
            return new DatabaseTarget("unknown", "unknown", "unknown");
        }
    }
}
