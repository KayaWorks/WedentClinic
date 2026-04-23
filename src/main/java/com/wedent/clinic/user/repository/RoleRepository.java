package com.wedent.clinic.user.repository;

import com.wedent.clinic.user.entity.Role;
import com.wedent.clinic.user.entity.RoleType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface RoleRepository extends JpaRepository<Role, Long> {

    Optional<Role> findByCode(RoleType code);

    /**
     * Roles with their permissions loaded in a single pass. Uses
     * {@code LEFT JOIN FETCH DISTINCT} instead of an {@code @EntityGraph} so
     * that callers can iterate {@code role.permissions} outside the session
     * (response mapping happens after the tx returns in some call sites).
     */
    @Query("SELECT DISTINCT r FROM Role r LEFT JOIN FETCH r.permissions ORDER BY r.code")
    List<Role> findAllWithPermissions();
}
