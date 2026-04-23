package com.wedent.clinic.auth.service.impl;

import com.wedent.clinic.auth.dto.LoginRequest;
import com.wedent.clinic.auth.dto.LoginResponse;
import com.wedent.clinic.auth.dto.RefreshResponse;
import com.wedent.clinic.auth.service.AuthService;
import com.wedent.clinic.auth.service.RefreshTokenService;
import com.wedent.clinic.common.audit.AuditEventPublisher;
import com.wedent.clinic.common.audit.event.AuditEvent;
import com.wedent.clinic.common.audit.event.AuditEventType;
import com.wedent.clinic.common.exception.InvalidCredentialsException;
import com.wedent.clinic.security.AuthenticatedUser;
import com.wedent.clinic.security.JwtTokenProvider;
import com.wedent.clinic.user.entity.Role;
import com.wedent.clinic.user.entity.User;
import com.wedent.clinic.user.entity.UserStatus;
import com.wedent.clinic.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private static final String TOKEN_TYPE = "Bearer";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;
    private final RefreshTokenService refreshTokenService;
    private final AuditEventPublisher auditEventPublisher;

    @Override
    @Transactional
    public LoginResponse login(LoginRequest request, String ipAddress, String userAgent) {
        User user = userRepository.findByEmailIgnoreCase(request.email())
                .orElseThrow(InvalidCredentialsException::new);

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new InvalidCredentialsException();
        }
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }

        AuthenticatedUser principal = toPrincipal(user);
        String accessToken = tokenProvider.generateAccessToken(principal);
        RefreshTokenService.Issued refresh = refreshTokenService.issue(user, ipAddress, userAgent);

        return new LoginResponse(
                accessToken,
                TOKEN_TYPE,
                tokenProvider.getExpirationMillis(),
                refresh.rawToken(),
                refreshTokenService.expirationMillis(),
                user.getId(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                user.getCompany().getId(),
                user.getClinic() != null ? user.getClinic().getId() : null,
                principal.roles()
        );
    }

    @Override
    @Transactional
    public RefreshResponse refresh(String rawRefreshToken, String ipAddress, String userAgent) {
        RefreshTokenService.Rotated rotated = refreshTokenService.rotate(rawRefreshToken, ipAddress, userAgent);
        User user = rotated.newRow().getUser();

        AuthenticatedUser principal = toPrincipal(user);
        String accessToken = tokenProvider.generateAccessToken(principal);

        auditEventPublisher.publish(AuditEvent.builder(AuditEventType.TOKEN_REFRESHED)
                .actorUserId(user.getId())
                .actorEmail(user.getEmail())
                .companyId(user.getCompany().getId())
                .clinicId(user.getClinic() != null ? user.getClinic().getId() : null)
                .ipAddress(ipAddress)
                .build());

        return new RefreshResponse(
                accessToken,
                TOKEN_TYPE,
                tokenProvider.getExpirationMillis(),
                rotated.newRawToken(),
                refreshTokenService.expirationMillis()
        );
    }

    @Override
    @Transactional
    public void logout(String rawRefreshToken) {
        // Silent no-op for unknown/empty tokens — prevents enumeration.
        refreshTokenService.revoke(rawRefreshToken);
    }

    private static AuthenticatedUser toPrincipal(User user) {
        Set<String> roleCodes = user.getRoles().stream()
                .map(Role::getCode)
                .map(Enum::name)
                .collect(Collectors.toUnmodifiableSet());

        List<GrantedAuthority> authorities = roleCodes.stream()
                .map(r -> "ROLE_" + r)
                .map(SimpleGrantedAuthority::new)
                .map(a -> (GrantedAuthority) a)
                .toList();

        return new AuthenticatedUser(
                user.getId(),
                user.getEmail(),
                user.getCompany().getId(),
                user.getClinic() != null ? user.getClinic().getId() : null,
                roleCodes,
                authorities
        );
    }
}
