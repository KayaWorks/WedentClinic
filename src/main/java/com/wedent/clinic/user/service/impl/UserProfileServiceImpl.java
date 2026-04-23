package com.wedent.clinic.user.service.impl;

import com.wedent.clinic.auth.service.RefreshTokenService;
import com.wedent.clinic.common.audit.AuditEventPublisher;
import com.wedent.clinic.common.audit.event.AuditEvent;
import com.wedent.clinic.common.audit.event.AuditEventType;
import com.wedent.clinic.common.exception.BusinessException;
import com.wedent.clinic.common.exception.ErrorCode;
import com.wedent.clinic.common.exception.InvalidCredentialsException;
import com.wedent.clinic.common.exception.ResourceNotFoundException;
import com.wedent.clinic.security.AuthenticatedUser;
import com.wedent.clinic.security.SecurityUtils;
import com.wedent.clinic.user.dto.PasswordChangeRequest;
import com.wedent.clinic.user.dto.UserProfileResponse;
import com.wedent.clinic.user.dto.UserProfileUpdateRequest;
import com.wedent.clinic.user.entity.Permission;
import com.wedent.clinic.user.entity.Role;
import com.wedent.clinic.user.entity.User;
import com.wedent.clinic.user.repository.UserRepository;
import com.wedent.clinic.user.service.UserProfileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserProfileServiceImpl implements UserProfileService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokenService;
    private final AuditEventPublisher auditEventPublisher;

    @Override
    @Transactional(readOnly = true)
    public UserProfileResponse getCurrent() {
        return toResponse(loadCurrent());
    }

    @Override
    @Transactional
    public UserProfileResponse updateCurrent(UserProfileUpdateRequest request) {
        User user = loadCurrent();
        user.setFirstName(request.firstName().trim());
        user.setLastName(request.lastName().trim());
        // save is implicit (managed entity inside tx), but calling it is
        // explicit + lets the read-back use the flushed @Version bump.
        User saved = userRepository.save(user);
        return toResponse(saved);
    }

    @Override
    @Transactional
    public void changePassword(PasswordChangeRequest request, String ipAddress) {
        User user = loadCurrent();

        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            // Treat as InvalidCredentials so brute-forcing the old password
            // through this endpoint surfaces the same 401 shape as /login.
            throw new InvalidCredentialsException();
        }
        if (passwordEncoder.matches(request.newPassword(), user.getPasswordHash())) {
            // Forcing a real change stops users from "rotating" to the same
            // value when an admin asks them to — and avoids a pointless
            // session blast-radius below.
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                    "New password must differ from the current one");
        }

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);

        // Torch every live session — the password change intent is "nobody
        // else should be logged in with the old credentials any more",
        // including this very caller's other devices.
        int revoked = refreshTokenService.revokeAllForUser(user.getId());

        auditEventPublisher.publish(AuditEvent.builder(AuditEventType.USER_PASSWORD_CHANGED)
                .actorUserId(user.getId())
                .actorEmail(user.getEmail())
                .companyId(user.getCompany().getId())
                .clinicId(user.getClinic() != null ? user.getClinic().getId() : null)
                .ipAddress(ipAddress)
                .detail(Map.of("revokedSessions", revoked))
                .build());

        log.info("Password changed userId={} revokedSessions={}", user.getId(), revoked);
    }

    // ─── helpers ────────────────────────────────────────────────────────────

    private User loadCurrent() {
        AuthenticatedUser principal = SecurityUtils.currentUser();
        return userRepository.findById(principal.userId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Authenticated user not found: " + principal.userId()));
    }

    private static UserProfileResponse toResponse(User user) {
        Set<String> roles = user.getRoles().stream()
                .map(Role::getCode)
                .map(Enum::name)
                .collect(Collectors.toCollection(TreeSet::new));

        // Roles × permissions is small (typical deployments have <10 roles and
        // a few dozen permission codes) so flattening here is cheap, and
        // saves the frontend from re-deriving it on every guard check.
        Set<String> permissions = user.getRoles().stream()
                .flatMap(r -> r.getPermissions().stream())
                .map(Permission::getCode)
                .collect(Collectors.toCollection(TreeSet::new));

        return new UserProfileResponse(
                user.getId(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                user.getStatus(),
                user.getCompany().getId(),
                user.getCompany().getName(),
                user.getClinic() != null ? user.getClinic().getId() : null,
                user.getClinic() != null ? user.getClinic().getName() : null,
                roles,
                permissions,
                user.getCreatedAt(),
                user.getUpdatedAt()
        );
    }
}
