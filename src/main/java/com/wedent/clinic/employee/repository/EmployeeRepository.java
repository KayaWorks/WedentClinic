package com.wedent.clinic.employee.repository;

import com.wedent.clinic.employee.entity.Employee;
import com.wedent.clinic.employee.entity.EmployeeStatus;
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

    @EntityGraph(attributePaths = {"company", "clinic", "user"})
    Optional<Employee> findByUserIdAndCompanyId(Long userId, Long companyId);

    /**
     * Batch-loads all employees for the given user ids, tenant-scoped.
     * Used by the admin user list to stitch phone / employee type into
     * the response without N+1-ing per row.
     */
    @Query("""
            SELECT e FROM Employee e
            WHERE e.company.id = :companyId
              AND e.user.id IN :userIds
            """)
    java.util.List<Employee> findAllByUserIdsAndCompanyId(java.util.Collection<Long> userIds, Long companyId);

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

    /** Dashboard: headcount with a status filter (usually {@code ACTIVE}). */
    @Query("""
            SELECT COUNT(e) FROM Employee e
            WHERE e.company.id = :companyId
              AND (:clinicId IS NULL OR e.clinic.id = :clinicId)
              AND e.status = :status
            """)
    long countByScopeAndStatus(Long companyId, Long clinicId, EmployeeStatus status);

    /** Dashboard: doctor headcount split ({@code DOCTOR} + {@code ACTIVE}). */
    @Query("""
            SELECT COUNT(e) FROM Employee e
            WHERE e.company.id = :companyId
              AND (:clinicId IS NULL OR e.clinic.id = :clinicId)
              AND e.employeeType = :type
              AND e.status = :status
            """)
    long countByScopeAndTypeAndStatus(Long companyId, Long clinicId, EmployeeType type, EmployeeStatus status);
}
