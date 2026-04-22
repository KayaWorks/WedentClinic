package com.wedent.clinic.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Shared base class for Spring Boot integration tests that need a real PostgreSQL
 * database. A single container is started once per JVM (static initializer +
 * withReuse) and shared across every integration test to keep the suite fast.
 *
 * <p>Flyway runs on startup against the container just like in dev/prod, which
 * is exactly the point: tests exercise the real DDL + constraints, catch
 * migration drift, and prove pessimistic-lock behaviour end-to-end.
 */
@Testcontainers
@SpringBootTest
@ActiveProfiles("it")
public abstract class AbstractPostgresIntegrationTest {

    @SuppressWarnings("resource")
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("wedent_test")
            .withUsername("wedent")
            .withPassword("wedent")
            .withReuse(true);

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void registerDatasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }
}
