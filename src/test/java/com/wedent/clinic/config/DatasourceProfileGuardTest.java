package com.wedent.clinic.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DatasourceProfileGuardTest {

    @Test
    void railwayProfile_requiresDbUrl() {
        MockEnvironment environment = railwayEnvironment()
                .withProperty("spring.datasource.username", "postgres")
                .withProperty("spring.datasource.password", "secret");

        assertThatThrownBy(() -> DatasourceProfileGuard.validate(environment))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("spring.datasource.url is required");
    }

    @Test
    void railwayProfile_requiresDbUsername() {
        MockEnvironment environment = railwayEnvironment()
                .withProperty("spring.datasource.url", "jdbc:postgresql://db.example.internal:5432/railway")
                .withProperty("spring.datasource.password", "secret");

        assertThatThrownBy(() -> DatasourceProfileGuard.validate(environment))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("spring.datasource.username is required");
    }

    @Test
    void railwayProfile_requiresDbPassword() {
        MockEnvironment environment = railwayEnvironment()
                .withProperty("spring.datasource.url", "jdbc:postgresql://db.example.internal:5432/railway")
                .withProperty("spring.datasource.username", "postgres");

        assertThatThrownBy(() -> DatasourceProfileGuard.validate(environment))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("spring.datasource.password is required");
    }

    @Test
    void railwayProfile_rejectsPlaceholderDbUrl() {
        MockEnvironment environment = railwayEnvironment()
                .withProperty("spring.datasource.url", "${DB_URL}")
                .withProperty("spring.datasource.username", "postgres")
                .withProperty("spring.datasource.password", "secret");

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
                .withProperty("spring.datasource.url", "jdbc:postgresql://db.example.internal:5432/railway")
                .withProperty("spring.datasource.username", "postgres")
                .withProperty("spring.datasource.password", "secret");

        assertThatCode(() -> DatasourceProfileGuard.validate(environment))
                .doesNotThrowAnyException();
    }

    private static MockEnvironment railwayEnvironment() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("railway");
        return environment;
    }
}
