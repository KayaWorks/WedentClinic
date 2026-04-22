package com.wedent.clinic.auth.service.impl;

import com.wedent.clinic.auth.dto.LoginRequest;
import com.wedent.clinic.auth.dto.LoginResponse;
import com.wedent.clinic.auth.service.AuthService;
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

    @Override
    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findByEmailIgnoreCase(request.email())
                .orElseThrow(InvalidCredentialsException::new);

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new InvalidCredentialsException();
        }
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }

        Set<String> roleCodes = user.getRoles().stream()
                .map(Role::getCode)
                .map(Enum::name)
                .collect(Collectors.toUnmodifiableSet());

        List<GrantedAuthority> authorities = roleCodes.stream()
                .map(r -> "ROLE_" + r)
                .map(SimpleGrantedAuthority::new)
                .map(a -> (GrantedAuthority) a)
                .toList();

        AuthenticatedUser principal = new AuthenticatedUser(
                user.getId(),
                user.getEmail(),
                user.getCompany().getId(),
                user.getClinic() != null ? user.getClinic().getId() : null,
                roleCodes,
                authorities
        );

        String token = tokenProvider.generateAccessToken(principal);

        return new LoginResponse(
                token,
                TOKEN_TYPE,
                tokenProvider.getExpirationMillis(),
                user.getId(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                user.getCompany().getId(),
                user.getClinic() != null ? user.getClinic().getId() : null,
                roleCodes
        );
    }
}
