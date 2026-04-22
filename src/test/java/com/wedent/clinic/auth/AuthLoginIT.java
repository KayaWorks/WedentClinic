package com.wedent.clinic.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wedent.clinic.auth.dto.LoginRequest;
import com.wedent.clinic.clinic.entity.Clinic;
import com.wedent.clinic.clinic.repository.ClinicRepository;
import com.wedent.clinic.company.entity.Company;
import com.wedent.clinic.company.repository.CompanyRepository;
import com.wedent.clinic.support.AbstractPostgresIntegrationTest;
import com.wedent.clinic.user.entity.Role;
import com.wedent.clinic.user.entity.RoleType;
import com.wedent.clinic.user.entity.User;
import com.wedent.clinic.user.entity.UserStatus;
import com.wedent.clinic.user.repository.RoleRepository;
import com.wedent.clinic.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.HashSet;
import java.util.Set;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Black-box auth flow against a real Postgres: seed a company + clinic + owner,
 * hit {@code POST /api/auth/login} over MockMvc, verify the response shape and
 * that the happy path returns a JWT.
 *
 * <p>Covers what the pure unit tests can't: password encoder + user repository
 * + security filter chain + MVC controller + Jackson all wired together.
 */
@AutoConfigureMockMvc
class AuthLoginIT extends AbstractPostgresIntegrationTest {

    private static final String EMAIL = "it-owner@wedent.local";
    private static final String PASSWORD = "S3cret!Password";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private CompanyRepository companyRepository;
    @Autowired private ClinicRepository clinicRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    @BeforeEach
    void seed() {
        if (userRepository.existsByEmailIgnoreCase(EMAIL)) {
            return;
        }
        Company company = companyRepository.save(Company.builder()
                .name("Auth IT Co " + System.nanoTime())
                .build());
        Clinic clinic = clinicRepository.save(Clinic.builder()
                .company(company).name("Main").build());
        Role ownerRole = roleRepository.findByCode(RoleType.CLINIC_OWNER)
                .orElseThrow(() -> new IllegalStateException("CLINIC_OWNER role missing — V2 migration failed"));

        userRepository.save(User.builder()
                .email(EMAIL)
                .passwordHash(passwordEncoder.encode(PASSWORD))
                .firstName("IT").lastName("Owner")
                .status(UserStatus.ACTIVE)
                .company(company).clinic(clinic)
                .roles(new HashSet<>(Set.of(ownerRole)))
                .build());
    }

    @Test
    void login_withCorrectCredentials_returns200AndJwt() throws Exception {
        LoginRequest request = new LoginRequest(EMAIL, PASSWORD);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.email").value(EMAIL))
                .andExpect(jsonPath("$.data.roles", org.hamcrest.Matchers.hasItem("CLINIC_OWNER")));
    }

    @Test
    void login_withWrongPassword_returns401() throws Exception {
        LoginRequest request = new LoginRequest(EMAIL, "wrong-password");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void login_withUnknownEmail_returns401() throws Exception {
        LoginRequest request = new LoginRequest("nobody@nowhere.local", PASSWORD);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void login_withInvalidPayload_returns400() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"not-an-email\",\"password\":\"\"}"))
                .andExpect(status().isBadRequest());
    }
}
