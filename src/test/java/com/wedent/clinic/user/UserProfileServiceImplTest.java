package com.wedent.clinic.user;

import com.wedent.clinic.auth.service.RefreshTokenService;
import com.wedent.clinic.clinic.entity.Clinic;
import com.wedent.clinic.common.audit.AuditEventPublisher;
import com.wedent.clinic.common.audit.event.AuditEventType;
import com.wedent.clinic.common.exception.BusinessException;
import com.wedent.clinic.common.exception.InvalidCredentialsException;
import com.wedent.clinic.company.entity.Company;
import com.wedent.clinic.security.AuthenticatedUser;
import com.wedent.clinic.user.dto.PasswordChangeRequest;
import com.wedent.clinic.user.dto.UserProfileResponse;
import com.wedent.clinic.user.dto.UserProfileUpdateRequest;
import com.wedent.clinic.user.entity.Permission;
import com.wedent.clinic.user.entity.Role;
import com.wedent.clinic.user.entity.RoleType;
import com.wedent.clinic.user.entity.User;
import com.wedent.clinic.user.entity.UserStatus;
import com.wedent.clinic.user.repository.UserRepository;
import com.wedent.clinic.user.service.impl.UserProfileServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit coverage for the self-service profile service.
 *
 * <p>SecurityContext is seeded per-test with a hand-crafted
 * {@link AuthenticatedUser}; anything that depends on the DB is mocked so
 * we're really exercising the service's contract, not JPA.</p>
 */
class UserProfileServiceImplTest {

    private UserRepository userRepository;
    private PasswordEncoder passwordEncoder;
    private RefreshTokenService refreshTokenService;
    private AuditEventPublisher auditEventPublisher;
    private UserProfileServiceImpl service;

    @BeforeEach
    void setUp() {
        userRepository = Mockito.mock(UserRepository.class);
        passwordEncoder = new BCryptPasswordEncoder(4); // fast for tests
        refreshTokenService = Mockito.mock(RefreshTokenService.class);
        auditEventPublisher = Mockito.mock(AuditEventPublisher.class);

        service = new UserProfileServiceImpl(
                userRepository, passwordEncoder, refreshTokenService, auditEventPublisher);

        // Simulate a logged-in user in the security context
        AuthenticatedUser principal = new AuthenticatedUser(
                42L, "jane@example.com", 7L, 11L,
                Set.of("MANAGER"),
                List.of());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, "n/a", principal.authorities()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void getCurrent_flattensRolesAndPermissions() {
        Permission perm1 = permission("patient:read");
        Permission perm2 = permission("patient:write");
        Role role = Role.builder()
                .code(RoleType.MANAGER)
                .permissions(Set.of(perm1, perm2))
                .build();
        role.setId(1L);

        User user = seededUser(42L, role);
        when(userRepository.findById(42L)).thenReturn(Optional.of(user));

        UserProfileResponse resp = service.getCurrent();

        assertThat(resp.id()).isEqualTo(42L);
        assertThat(resp.email()).isEqualTo("jane@example.com");
        assertThat(resp.roles()).containsExactly("MANAGER");
        // Permission codes are flattened + sorted — the frontend uses these
        // directly to show/hide buttons, no second round-trip needed.
        assertThat(resp.permissions()).containsExactly("patient:read", "patient:write");
        assertThat(resp.companyId()).isEqualTo(7L);
        assertThat(resp.companyName()).isEqualTo("Acme");
        assertThat(resp.clinicId()).isEqualTo(11L);
        assertThat(resp.clinicName()).isEqualTo("Main Clinic");
    }

    @Test
    void updateCurrent_persistsTrimmedNameFields() {
        User user = seededUser(42L);
        when(userRepository.findById(42L)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        UserProfileResponse resp = service.updateCurrent(
                new UserProfileUpdateRequest("  Jane ", " Doe "));

        assertThat(resp.firstName()).isEqualTo("Jane");
        assertThat(resp.lastName()).isEqualTo("Doe");
        verify(userRepository).save(argThat(u ->
                u.getFirstName().equals("Jane") && u.getLastName().equals("Doe")));
    }

    @Test
    void changePassword_rotatesHash_revokesSessions_auditsEvent() {
        User user = seededUser(42L);
        user.setPasswordHash(passwordEncoder.encode("OldPass123!"));
        when(userRepository.findById(42L)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(refreshTokenService.revokeAllForUser(42L)).thenReturn(3);

        service.changePassword(new PasswordChangeRequest("OldPass123!", "NewPass456!"),
                "10.0.0.1");

        assertThat(passwordEncoder.matches("NewPass456!", user.getPasswordHash())).isTrue();
        verify(refreshTokenService).revokeAllForUser(42L);
        verify(auditEventPublisher).publish(argThat(e ->
                e.type() == AuditEventType.USER_PASSWORD_CHANGED
                        && e.ipAddress().equals("10.0.0.1")));
    }

    @Test
    void changePassword_wrongCurrent_rejectsAsInvalidCredentials() {
        User user = seededUser(42L);
        user.setPasswordHash(passwordEncoder.encode("OldPass123!"));
        when(userRepository.findById(42L)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.changePassword(
                new PasswordChangeRequest("WrongOld!", "NewPass456!"),
                "10.0.0.1"))
                .isInstanceOf(InvalidCredentialsException.class);

        verify(refreshTokenService, never()).revokeAllForUser(anyLong());
        verify(auditEventPublisher, never()).publish(any());
    }

    @Test
    void changePassword_sameAsCurrent_rejectsAsBusinessRuleViolation() {
        User user = seededUser(42L);
        user.setPasswordHash(passwordEncoder.encode("OldPass123!"));
        when(userRepository.findById(42L)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.changePassword(
                new PasswordChangeRequest("OldPass123!", "OldPass123!"),
                "10.0.0.1"))
                .isInstanceOf(BusinessException.class);

        verify(refreshTokenService, never()).revokeAllForUser(anyLong());
    }

    // ─────────────────────────────────────────────────────────────────────────

    private static User seededUser(long id, Role... roles) {
        Company company = Company.builder().name("Acme").build();
        company.setId(7L);
        Clinic clinic = Clinic.builder().name("Main Clinic").company(company).build();
        clinic.setId(11L);

        User user = User.builder()
                .email("jane@example.com")
                .firstName("Jane")
                .lastName("Smith")
                .passwordHash("placeholder")
                .status(UserStatus.ACTIVE)
                .company(company)
                .clinic(clinic)
                .roles(new java.util.HashSet<>(Set.of(roles)))
                .build();
        user.setId(id);
        return user;
    }

    private static Permission permission(String code) {
        Permission p = Permission.builder().code(code).build();
        p.setId((long) code.hashCode());
        return p;
    }
}
