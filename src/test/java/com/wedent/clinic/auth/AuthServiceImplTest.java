package com.wedent.clinic.auth;

import com.wedent.clinic.auth.dto.LoginRequest;
import com.wedent.clinic.auth.dto.LoginResponse;
import com.wedent.clinic.auth.service.impl.AuthServiceImpl;
import com.wedent.clinic.common.exception.InvalidCredentialsException;
import com.wedent.clinic.company.entity.Company;
import com.wedent.clinic.security.JwtProperties;
import com.wedent.clinic.security.JwtTokenProvider;
import com.wedent.clinic.user.entity.Role;
import com.wedent.clinic.user.entity.RoleType;
import com.wedent.clinic.user.entity.User;
import com.wedent.clinic.user.entity.UserStatus;
import com.wedent.clinic.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

class AuthServiceImplTest {

    private UserRepository userRepository;
    private PasswordEncoder passwordEncoder;
    private JwtTokenProvider tokenProvider;
    private AuthServiceImpl authService;

    @BeforeEach
    void setUp() {
        userRepository = Mockito.mock(UserRepository.class);
        passwordEncoder = new BCryptPasswordEncoder(4);
        JwtProperties props = new JwtProperties(
                new JwtProperties.Jwt(
                        "test-secret-test-secret-test-secret-test-secret-1234567890",
                        60,
                        "wedent-clinic-test"),
                java.util.List.of()
        );
        tokenProvider = new JwtTokenProvider(props);
        authService = new AuthServiceImpl(userRepository, passwordEncoder, tokenProvider);
    }

    @Test
    void login_withValidCredentials_returnsToken() {
        Company company = Company.builder().name("Acme").build();
        company.setId(10L);

        Role role = Role.builder().code(RoleType.MANAGER).build();
        role.setId(1L);

        User user = User.builder()
                .email("john@example.com")
                .firstName("John")
                .lastName("Doe")
                .passwordHash(passwordEncoder.encode("Secret123!"))
                .status(UserStatus.ACTIVE)
                .company(company)
                .roles(Set.of(role))
                .build();
        user.setId(99L);

        when(userRepository.findByEmailIgnoreCase(anyString()))
                .thenReturn(java.util.Optional.of(user));

        LoginResponse response = authService.login(new LoginRequest("john@example.com", "Secret123!"));

        assertThat(response.accessToken()).isNotBlank();
        assertThat(response.userId()).isEqualTo(99L);
        assertThat(response.companyId()).isEqualTo(10L);
        assertThat(response.roles()).contains(RoleType.MANAGER.name());
    }

    @Test
    void login_withWrongPassword_throws() {
        Company company = Company.builder().name("Acme").build();
        company.setId(10L);

        User user = User.builder()
                .email("john@example.com")
                .firstName("John")
                .lastName("Doe")
                .passwordHash(passwordEncoder.encode("Right!"))
                .status(UserStatus.ACTIVE)
                .company(company)
                .build();
        user.setId(99L);

        when(userRepository.findByEmailIgnoreCase(anyString()))
                .thenReturn(java.util.Optional.of(user));

        assertThatThrownBy(() -> authService.login(new LoginRequest("john@example.com", "Wrong!")))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void login_withDisabledUser_throws() {
        Company company = Company.builder().name("Acme").build();
        company.setId(10L);

        User user = User.builder()
                .email("john@example.com")
                .firstName("John")
                .lastName("Doe")
                .passwordHash(passwordEncoder.encode("Secret123!"))
                .status(UserStatus.DISABLED)
                .company(company)
                .build();
        user.setId(99L);

        when(userRepository.findByEmailIgnoreCase(anyString()))
                .thenReturn(java.util.Optional.of(user));

        assertThatThrownBy(() -> authService.login(new LoginRequest("john@example.com", "Secret123!")))
                .isInstanceOf(InvalidCredentialsException.class);
    }
}
