package com.wedent.clinic.company.repository;

import com.wedent.clinic.company.entity.Company;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CompanyRepository extends JpaRepository<Company, Long> {

    boolean existsByTaxNumber(String taxNumber);
}
