package com.wedent.clinic.user.service;

import com.wedent.clinic.user.dto.PermissionResponse;
import com.wedent.clinic.user.dto.RoleResponse;

import java.util.List;

/**
 * Read-only lookup service for RBAC metadata (roles + permissions).
 *
 * <p>These are global, rarely-changing rows — the listing is safely
 * cacheable and shared across tenants. Results come back sorted by
 * {@code code} so the frontend can render deterministic selectors without
 * an extra sort pass.</p>
 */
public interface CatalogService {

    List<RoleResponse> listRoles();

    List<PermissionResponse> listPermissions();
}
