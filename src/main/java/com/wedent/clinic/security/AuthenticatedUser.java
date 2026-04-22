package com.wedent.clinic.security;

import org.springframework.security.core.GrantedAuthority;

import java.util.Collection;
import java.util.Set;

/**
 * Lightweight principal injected into the SecurityContext after JWT validation.
 * Avoids holding a full User entity in memory and exposes tenant scope.
 */
public record AuthenticatedUser(
        Long userId,
        String email,
        Long companyId,
        Long clinicId,
        Set<String> roles,
        Collection<? extends GrantedAuthority> authorities
) {}
