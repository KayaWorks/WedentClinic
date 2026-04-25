package com.wedent.clinic.security;

import com.wedent.clinic.auth.controller.AuthController;
import com.wedent.clinic.auth.service.AuthService;
import com.wedent.clinic.common.audit.AuditEventPublisher;
import com.wedent.clinic.security.blacklist.AccessTokenBlacklist;
import com.wedent.clinic.security.ratelimit.LoginRateLimiter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.http.HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS;
import static org.springframework.http.HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN;
import static org.springframework.http.HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS;
import static org.springframework.http.HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD;
import static org.springframework.http.HttpHeaders.ORIGIN;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@ActiveProfiles("test")
@Import({
        SecurityConfig.class,
        JwtAuthenticationFilter.class,
        RestAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class
})
@TestPropertySource(properties = {
        "app.security.jwt.secret=test-secret-test-secret-test-secret-test-secret-1234567890",
        "app.security.jwt.access-token-expiration-minutes=60",
        "app.security.jwt.issuer=wedent-clinic-test",
        "app.security.public-paths[0]=/api/auth/**",
        "app.cors.allowed-origins=http://localhost:5173,https://frontend.example"
})
class CorsSecurityConfigTest {

    @Autowired
    private MockMvc mvc;

    @MockBean
    private AuthService authService;

    @MockBean
    private LoginRateLimiter loginRateLimiter;

    @MockBean
    private AuditEventPublisher auditEventPublisher;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private AccessTokenBlacklist accessTokenBlacklist;

    @MockBean
    private JpaMetamodelMappingContext jpaMetamodelMappingContext;

    @MockBean(name = "auditorAware")
    private AuditorAware<Long> auditorAware;

    @Test
    void preflightToLogin_allowsConfiguredOriginWithCredentials() throws Exception {
        mvc.perform(options("/api/auth/login")
                        .header(ORIGIN, "http://localhost:5173")
                        .header(ACCESS_CONTROL_REQUEST_METHOD, "POST")
                        .header(ACCESS_CONTROL_REQUEST_HEADERS, "Authorization,Content-Type"))
                .andExpect(status().isOk())
                .andExpect(header().string(ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5173"))
                .andExpect(header().string(ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"));
    }

    @Test
    void preflightToLogin_rejectsUnlistedOrigin() throws Exception {
        mvc.perform(options("/api/auth/login")
                        .header(ORIGIN, "https://unlisted.example")
                        .header(ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    @Test
    void protectedEndpointWithoutToken_remainsUnauthorized() throws Exception {
        mvc.perform(get("/api/clinics")
                        .header(ORIGIN, "http://localhost:5173"))
                .andExpect(status().isUnauthorized());
    }
}
