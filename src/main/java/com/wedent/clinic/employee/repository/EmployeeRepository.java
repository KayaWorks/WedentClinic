package com.wedent.clinic.employee.repository;

import com.wedent.clinic.employee.entity.Employee;
import com.wedent.clinic.employee.entity.EmployeeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface EmployeeRepository extends JpaRepository<Employee, Long> {

    @EntityGraph(attributePaths = {"company", "clinic", "user"})
    Optional<Employee> findByIdAndCompanyId(Long id, Long companyId);

    boolean existsByClinicIdAndEmailIgnoreCaseAndIdNot(Long clinicId, String email, Long id);

    boolean existsByClinicIdAndEmailIgnoreCase(Long clinicId, String email);

    @Query("""
            SELECT e FROM Employee e
            WHERE e.company.id = :companyId
              AND (:clinicId IS NULL OR e.clinic.id = :clinicId)
              AND (:type IS NULL OR e.employeeType = :type)
            """)
    Page<Employee> search(Long companyId, Long clinicId, EmployeeType type, Pageable pageable);

    @EntityGraph(attributePaths = {"clinic"})
    Optional<Employee> findByIdAndCompanyIdAndEmployeeType(Long id, Long companyId, EmployeeType type);
}
