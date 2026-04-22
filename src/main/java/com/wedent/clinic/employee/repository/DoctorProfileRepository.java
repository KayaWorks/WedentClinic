package com.wedent.clinic.employee.repository;

import com.wedent.clinic.employee.entity.DoctorProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DoctorProfileRepository extends JpaRepository<DoctorProfile, Long> {

    Optional<DoctorProfile> findByEmployeeId(Long employeeId);

    boolean existsByEmployeeId(Long employeeId);
}
