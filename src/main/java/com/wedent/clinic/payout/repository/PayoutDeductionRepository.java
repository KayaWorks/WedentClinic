package com.wedent.clinic.payout.repository;

import com.wedent.clinic.payout.entity.PayoutDeduction;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PayoutDeductionRepository extends JpaRepository<PayoutDeduction, Long> {
}
