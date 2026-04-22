package com.wedent.clinic.user.repository;

import com.wedent.clinic.user.entity.Role;
import com.wedent.clinic.user.entity.RoleType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RoleRepository extends JpaRepository<Role, Long> {

    Optional<Role> findByCode(RoleType code);
}
