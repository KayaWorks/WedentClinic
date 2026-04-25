package com.wedent.clinic.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DatasourceProfileGuardTest {

    @Test
    void railwayProfile_requiresDbUrl() {
        MockEnvironment environment = railwayEnvironment()
                .withProperty("DB_USERNAME", "postgres")
                .withProperty("DB_PASSWORD", "secret");

        assertThatThrownBy(() -> DatasourceProfileGuard.validate(environment))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DB_URL is required");
    }

    @Test
    void railwayProfile_requiresDbUsername() {
        MockEnvironment environment = railwayEnvironment()
                .withProperty("DB_URL", "jdbc:postgresql://db.example.internal:5432/railway")
                .withProperty("DB_PASSWORD", "secret");

        assertThatThrownBy(() -> DatasourceProfileGuard.validate(environment))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DB_USERNAME is required");
    }

    @Test
    void railwayProfile_requiresDbPassword() {
        MockEnvironment environment = railwayEnvironment()
                .withProperty("DB_URL", "jdbc:postgresql://db.example.internal:5432/railway")
                .withProperty("DB_USERNAME", "postgres");

        assertThatThrownBy(() -> DatasourceProfileGuard.validate(environment))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DB_PASSWORD is required");
    }

    @Test
    void railwayProfile_rejectsPlaceholderDbUrl() {
        MockEnvironment environment = railwayEnvironment()
                .withProperty("DB_URL", "${DB_URL}")
                .withProperty("DB_USERNAME", "postgres")
                .withProperty("DB_PASSWORD", "secret");

        assertThatThrownBy(() -> DatasourceProfileGuard.validate(environment))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not a placeholder");
    }

    @Test
    void railwayEnvironment_requiresRailwayProfile() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("RAILWAY_SERVICE_ID", "service-id");
        environment.setActiveProfiles("dev");

        assertThatThrownBy(() -> DatasourceProfileGuard.validate(environment))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SPRING_PROFILES_ACTIVE=railway");
    }

    @Test
    void testProfile_allowsRailwayBuildEnvironmentMarkers() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("RAILWAY_SERVICE_ID", "service-id");
        environment.setActiveProfiles("test");

        assertThatCode(() -> DatasourceProfileGuard.validate(environment))
                .doesNotThrowAnyException();
    }

    @Test
    void railwayProfile_allowsProvidedDatasourceVariables() {
        MockEnvironment environment = railwayEnvironment()
                .withProperty("DB_URL", "jdbc:postgresql://db.example.internal:5432/railway")
                .withProperty("DB_USERNAME", "postgres")
                .withProperty("DB_PASSWORD", "secret");

        assertThatCode(() -> DatasourceProfileGuard.validate(environment))
                .doesNotThrowAnyException();
    }

    private static MockEnvironment railwayEnvironment() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("railway");
        return environment;
    }
}
