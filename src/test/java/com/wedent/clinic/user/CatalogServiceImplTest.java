package com.wedent.clinic.user;

import com.wedent.clinic.user.dto.PermissionResponse;
import com.wedent.clinic.user.dto.RoleResponse;
import com.wedent.clinic.user.entity.Permission;
import com.wedent.clinic.user.entity.Role;
import com.wedent.clinic.user.entity.RoleType;
import com.wedent.clinic.user.repository.PermissionRepository;
import com.wedent.clinic.user.repository.RoleRepository;
import com.wedent.clinic.user.service.impl.CatalogServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit coverage for the RBAC lookup service. The @Cacheable behaviour is
 * opaque here (we mock the repositories directly) but the transformation
 * logic — flattening, sorting, permission nesting — is fully exercised.
 */
class CatalogServiceImplTest {

    private RoleRepository roleRepository;
    private PermissionRepository permissionRepository;
    private CatalogServiceImpl service;

    @BeforeEach
    void setUp() {
        roleRepository = Mockito.mock(RoleRepository.class);
        permissionRepository = Mockito.mock(PermissionRepository.class);
        service = new CatalogServiceImpl(roleRepository, permissionRepository);
    }

    @Test
    void listRoles_nestsSortedPermissions() {
        Permission p1 = permission(1L, "patient:write", "Create/update patients");
        Permission p2 = permission(2L, "appointment:read", "View appointments");
        Role manager = Role.builder()
                .code(RoleType.MANAGER)
                .description("Clinic manager")
                .permissions(Set.of(p1, p2))
                .build();
        manager.setId(10L);
        when(roleRepository.findAllWithPermissions()).thenReturn(List.of(manager));

        List<RoleResponse> roles = service.listRoles();

        assertThat(roles).hasSize(1);
        RoleResponse r = roles.get(0);
        assertThat(r.code()).isEqualTo(RoleType.MANAGER);
        assertThat(r.description()).isEqualTo("Clinic manager");
        // Permissions come back sorted by code — the frontend relies on that
        // for a stable checkbox order without a second client-side sort.
        assertThat(r.permissions()).extracting(PermissionResponse::code)
                .containsExactly("appointment:read", "patient:write");
    }

    @Test
    void listPermissions_delegatesToRepoWithCodeSort() {
        Permission p1 = permission(1L, "appointment:read", null);
        Permission p2 = permission(2L, "patient:write", null);
        when(permissionRepository.findAll(Sort.by("code"))).thenReturn(List.of(p1, p2));

        List<PermissionResponse> perms = service.listPermissions();

        assertThat(perms).extracting(PermissionResponse::code)
                .containsExactly("appointment:read", "patient:write");
        verify(permissionRepository).findAll(Sort.by("code"));
    }

    // ─────────────────────────────────────────────────────────────────────────

    private static Permission permission(long id, String code, String description) {
        Permission p = Permission.builder().code(code).description(description).build();
        p.setId(id);
        return p;
    }
}
