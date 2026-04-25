package com.wedent.clinic.config;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class DatasourceProfileGuard implements BeanFactoryPostProcessor, EnvironmentAware {

    private static final String DATASOURCE_URL = "spring.datasource.url";
    private static final String DATASOURCE_USERNAME = "spring.datasource.username";
    private static final String DATASOURCE_PASSWORD = "spring.datasource.password";
    private static final Set<String> RAILWAY_ENV_MARKERS = Set.of(
            "RAILWAY_ENVIRONMENT",
            "RAILWAY_ENVIRONMENT_NAME",
            "RAILWAY_PROJECT_ID",
            "RAILWAY_SERVICE_ID",
            "RAILWAY_DEPLOYMENT_ID",
            "RAILWAY_PUBLIC_DOMAIN",
            "RAILWAY_STATIC_URL");

    private Environment environment;

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
        validate(environment);
    }

    static void validate(Environment environment) {
        Set<String> activeProfiles = Arrays.stream(environment.getActiveProfiles())
                .collect(Collectors.toUnmodifiableSet());
        boolean testProfile = activeProfiles.contains("test") || activeProfiles.contains("it");

        if (!testProfile && isRailwayEnvironment(environment) && !activeProfiles.contains("railway")) {
            throw new IllegalStateException("SPRING_PROFILES_ACTIVE=railway is required for Railway deployments");
        }

        if (activeProfiles.contains("railway") || activeProfiles.contains("prod")) {
            requireRealValue(environment, DATASOURCE_URL);
            requireRealValue(environment, DATASOURCE_USERNAME);
            requireRealValue(environment, DATASOURCE_PASSWORD);
        }
    }

    private static boolean isRailwayEnvironment(Environment environment) {
        return RAILWAY_ENV_MARKERS.stream()
                .map(name -> property(environment, name))
                .anyMatch(StringUtils::hasText);
    }

    private static void requireRealValue(Environment environment, String name) {
        String value = property(environment, name);
        if (!StringUtils.hasText(value)) {
            throw new IllegalStateException(name + " is required for railway/prod profiles");
        }
        if (looksUnresolved(value)) {
            throw new IllegalStateException(name + " must be set to a real value, not a placeholder");
        }
    }

    private static String property(Environment environment, String name) {
        try {
            return environment.getProperty(name);
        } catch (IllegalArgumentException ex) {
            return "${" + name + "}";
        }
    }

    private static boolean looksUnresolved(String value) {
        return value != null && value.trim().startsWith("${");
    }
}
