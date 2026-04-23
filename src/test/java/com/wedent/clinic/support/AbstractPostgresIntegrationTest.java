package com.wedent.clinic.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Shared base class for Spring Boot integration tests that need real
 * PostgreSQL + Redis instances. A single container per dependency is started
 * once per JVM (static initializer + withReuse) and shared across every
 * integration test to keep the suite fast.
 *
 * <p>Flyway runs on startup against the container just like in dev/prod, which
 * is exactly the point: tests exercise the real DDL + constraints, catch
 * migration drift, and prove pessimistic-lock behaviour end-to-end.</p>
 *
 * <p>Redis is required because refresh tokens and the access-token blacklist
 * live there — booting the context without a redis connection would fail
 * fast, so the container is wired up at this layer for every IT.</p>
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

    @SuppressWarnings("resource")
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379)
            .withReuse(true);

    static {
        POSTGRES.start();
        REDIS.start();
    }

    @DynamicPropertySource
    static void registerDynamicProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");

        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }
}
