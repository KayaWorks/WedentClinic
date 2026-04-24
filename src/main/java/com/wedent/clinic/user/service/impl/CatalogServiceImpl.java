package com.wedent.clinic.user.service.impl;

import com.wedent.clinic.user.dto.PermissionResponse;
import com.wedent.clinic.user.dto.RoleResponse;
import com.wedent.clinic.user.entity.Permission;
import com.wedent.clinic.user.entity.Role;
import com.wedent.clinic.user.repository.PermissionRepository;
import com.wedent.clinic.user.repository.RoleRepository;
import com.wedent.clinic.user.service.CatalogService;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

/**
 * Redis-cached so the RBAC selectors don't round-trip the DB on every
 * admin page view. The data set is tiny and almost never changes; when it
 * does (seed migration), a manual {@code FLUSHDB} or TTL expiry is fine.
 */
@Service
@RequiredArgsConstructor
public class CatalogServiceImpl implements CatalogService {

    /** Cache names are namespaced by {@code RedisCacheManager} with the {@code wedent:cache:} prefix. */
    public static final String ROLE_CACHE = "rbac-roles";
    public static final String PERMISSION_CACHE = "rbac-permissions";

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;

    @Override
    @Transactional(readOnly = true)
    @Cacheable(cacheNames = ROLE_CACHE)
    public List<RoleResponse> listRoles() {
        return roleRepository.findAllWithPermissions().stream()
                .map(CatalogServiceImpl::toRoleResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(cacheNames = PERMISSION_CACHE)
    public List<PermissionResponse> listPermissions() {
        return permissionRepository.findAll(Sort.by("code")).stream()
                .map(CatalogServiceImpl::toPermissionResponse)
                .toList();
    }

    // ─── mapping ────────────────────────────────────────────────────────────

    private static RoleResponse toRoleResponse(Role role) {
        List<PermissionResponse> perms = role.getPermissions().stream()
                .sorted(Comparator.comparing(Permission::getCode))
                .map(CatalogServiceImpl::toPermissionResponse)
                .toList();
        return new RoleResponse(role.getId(), role.getCode(), role.getDescription(), perms);
    }

    private static PermissionResponse toPermissionResponse(Permission p) {
        return new PermissionResponse(p.getId(), p.getCode(), p.getDescription());
    }
}
