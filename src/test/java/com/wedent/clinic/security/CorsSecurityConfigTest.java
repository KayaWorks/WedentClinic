package com.wedent.clinic.security;

import com.wedent.clinic.auth.controller.AuthController;
import com.wedent.clinic.auth.dto.LoginRequest;
import com.wedent.clinic.auth.dto.LoginResponse;
import com.wedent.clinic.auth.service.AuthService;
import com.wedent.clinic.common.audit.AuditEventPublisher;
import com.wedent.clinic.common.exception.GlobalExceptionHandler;
import com.wedent.clinic.security.blacklist.AccessTokenBlacklist;
import com.wedent.clinic.security.ratelimit.LoginRateLimiter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Set;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS;
import static org.springframework.http.HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS;
import static org.springframework.http.HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN;
import static org.springframework.http.HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS;
import static org.springframework.http.HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD;
import static org.springframework.http.HttpHeaders.ORIGIN;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@ActiveProfiles("test")
@Import({
        SecurityConfig.class,
        JwtAuthenticationFilter.class,
        RestAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class,
        GlobalExceptionHandler.class
})
@TestPropertySource(properties = {
        "app.security.jwt.secret=test-secret-test-secret-test-secret-test-secret-1234567890",
        "app.security.jwt.access-token-expiration-minutes=60",
        "app.security.jwt.issuer=wedent-clinic-test",
        "app.security.public-paths[0]=/api/auth/**",
        "app.cors.allowed-origins=https://clinicflow-dashboard-production.up.railway.app,http://localhost:5173,http://127.0.0.1:5173"
})
class CorsSecurityConfigTest {

    private static final String FRONTEND_ORIGIN = "https://clinicflow-dashboard-production.up.railway.app";

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
                        .header(ORIGIN, FRONTEND_ORIGIN)
                        .header(ACCESS_CONTROL_REQUEST_METHOD, "POST")
                        .header(ACCESS_CONTROL_REQUEST_HEADERS, "Authorization,Content-Type,X-Request-Id"))
                .andExpect(status().isOk())
                .andExpect(header().string(ACCESS_CONTROL_ALLOW_ORIGIN, FRONTEND_ORIGIN))
                .andExpect(header().string(ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"))
                .andExpect(header().string(ACCESS_CONTROL_ALLOW_HEADERS, "Authorization, Content-Type, X-Request-Id"));
    }

    @Test
    void postToLogin_fromConfiguredOriginReturnsJsonWithCorsHeaders() throws Exception {
        when(loginRateLimiter.isBlocked(anyString())).thenReturn(false);
        when(authService.login(any(LoginRequest.class), anyString(), any()))
                .thenReturn(new LoginResponse(
                        "access-token",
                        "Bearer",
                        3600000L,
                        "refresh-token",
                        1209600000L,
                        42L,
                        "owner@wedent.local",
                        "Wedent",
                        "Owner",
                        10L,
                        20L,
                        Set.of("CLINIC_OWNER")
                ));

        mvc.perform(post("/api/auth/login")
                        .header(ORIGIN, FRONTEND_ORIGIN)
                        .header("X-Request-Id", "cors-login-test")
                        .contentType(APPLICATION_JSON)
                        .accept(APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "owner@wedent.local",
                                  "password": "ChangeMe!123"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(header().string(ACCESS_CONTROL_ALLOW_ORIGIN, FRONTEND_ORIGIN))
                .andExpect(header().string(ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"))
                .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON))
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.accessToken", is("access-token")))
                .andExpect(jsonPath("$.data.refreshToken", is("refresh-token")));
    }

    @Test
    void preflightToLogin_allowsLocalhostIpOrigin() throws Exception {
        mvc.perform(options("/api/auth/login")
                        .header(ORIGIN, "http://127.0.0.1:5173")
                        .header(ACCESS_CONTROL_REQUEST_METHOD, "POST")
                        .header(ACCESS_CONTROL_REQUEST_HEADERS, "Content-Type,X-Request-Id"))
                .andExpect(status().isOk())
                .andExpect(header().string(ACCESS_CONTROL_ALLOW_ORIGIN, "http://127.0.0.1:5173"))
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
                        .header(ORIGIN, FRONTEND_ORIGIN))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void postToLogin_whenRedisUnavailableReturnsStructuredJsonError() throws Exception {
        when(loginRateLimiter.isBlocked(anyString())).thenReturn(false);
        when(authService.login(any(LoginRequest.class), anyString(), any()))
                .thenThrow(new RedisConnectionFailureException("Connection refused"));

        mvc.perform(post("/api/auth/login")
                        .header(ORIGIN, FRONTEND_ORIGIN)
                        .contentType(APPLICATION_JSON)
                        .accept(APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "owner@wedent.local",
                                  "password": "ChangeMe!123"
                                }
                                """))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string(ACCESS_CONTROL_ALLOW_ORIGIN, FRONTEND_ORIGIN))
                .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON))
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.code", is("SERVICE_UNAVAILABLE")))
                .andExpect(jsonPath("$.message", is("Session service temporarily unavailable")));
    }
}
