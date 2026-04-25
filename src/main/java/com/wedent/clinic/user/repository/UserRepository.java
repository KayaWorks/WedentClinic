package com.wedent.clinic.user.repository;

import com.wedent.clinic.user.entity.RoleType;
import com.wedent.clinic.user.entity.User;
import com.wedent.clinic.user.entity.UserStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    @EntityGraph(attributePaths = {"roles", "roles.permissions", "company", "clinic"})
    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    @EntityGraph(attributePaths = {"roles", "company", "clinic"})
    Optional<User> findByIdAndCompanyId(Long id, Long companyId);

    /**
     * Tenant-scoped admin search.
     *
     * <p>{@code search} matches case-insensitively against first name,
     * last name, and email. The role filter pivots through the join table
     * so callers can narrow to "all DOCTORs in this clinic".</p>
     *
     * <p>{@code DISTINCT} is required because the role join fans out one
     * row per (user, role) pair; without it paged counts lie.</p>
     */
    @Query("""
            SELECT DISTINCT u FROM User u
            LEFT JOIN u.roles r
            WHERE u.company.id = :companyId
              AND (:clinicId IS NULL OR u.clinic.id = :clinicId)
              AND (:status IS NULL OR u.status = :status)
              AND (:roleCode IS NULL OR r.code = :roleCode)
              AND (:search IS NULL
                   OR LOWER(u.firstName) LIKE LOWER(CONCAT('%', :search, '%'))
                   OR LOWER(u.lastName)  LIKE LOWER(CONCAT('%', :search, '%'))
                   OR LOWER(u.email)     LIKE LOWER(CONCAT('%', :search, '%')))
            """)
    Page<User> search(Long companyId, Long clinicId, UserStatus status, RoleType roleCode, String search, Pageable pageable);

    /**
     * Active-owner headcount. Used by last-owner protection when an admin
     * tries to disable, remove the owner role from, or (indirectly) delete
     * the final CLINIC_OWNER on the company.
     */
    @Query("""
            SELECT COUNT(DISTINCT u) FROM User u JOIN u.roles r
            WHERE u.company.id = :companyId
              AND u.status = com.wedent.clinic.user.entity.UserStatus.ACTIVE
              AND r.code = com.wedent.clinic.user.entity.RoleType.CLINIC_OWNER
            """)
    long countActiveOwners(Long companyId);
}
