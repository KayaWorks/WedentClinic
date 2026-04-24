package com.wedent.clinic.payout.repository;

import com.wedent.clinic.payout.entity.PayoutPeriod;
import com.wedent.clinic.payout.entity.PayoutStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.Optional;

public interface PayoutPeriodRepository extends JpaRepository<PayoutPeriod, Long> {

    /**
     * Tenant-scoped fetch with relations. Deductions are left lazy — the
     * list endpoint doesn't need them; the detail endpoint pulls them in
     * its own session.
     */
    @EntityGraph(attributePaths = {"company", "clinic", "doctorProfile", "doctorProfile.employee"})
    Optional<PayoutPeriod> findByIdAndCompanyId(Long id, Long companyId);

    /**
     * Filter query — every parameter is nullable. Using {@code :param IS
     * NULL OR ...} keeps the query plan stable while letting the FE
     * leave filters blank.
     */
    @Query("""
            SELECT p FROM PayoutPeriod p
            WHERE p.company.id = :companyId
              AND (:clinicId IS NULL OR p.clinic.id = :clinicId)
              AND (:doctorProfileId IS NULL OR p.doctorProfile.id = :doctorProfileId)
              AND (:status IS NULL OR p.status = :status)
              AND (:from IS NULL OR p.periodStart >= :from)
              AND (:to   IS NULL OR p.periodEnd   <= :to)
            """)
    Page<PayoutPeriod> search(Long companyId,
                              Long clinicId,
                              Long doctorProfileId,
                              PayoutStatus status,
                              LocalDate from,
                              LocalDate to,
                              Pageable pageable);
}
