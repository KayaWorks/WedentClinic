package com.wedent.clinic.config;

import com.wedent.clinic.clinic.entity.Clinic;
import com.wedent.clinic.clinic.repository.ClinicRepository;
import com.wedent.clinic.company.entity.Company;
import com.wedent.clinic.company.repository.CompanyRepository;
import com.wedent.clinic.user.entity.Role;
import com.wedent.clinic.user.entity.RoleType;
import com.wedent.clinic.user.entity.User;
import com.wedent.clinic.user.entity.UserStatus;
import com.wedent.clinic.user.repository.RoleRepository;
import com.wedent.clinic.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Set;

/**
 * Dev-only seed that ensures a default Company + Clinic + CLINIC_OWNER user exist,
 * so the API can be exercised right after a fresh database reset without needing
 * a manual SQL script or a signup call.
 *
 * <p>All operations are idempotent: when the target records already exist they are
 * left untouched. In particular, if the owner user is already present we never
 * touch the password — that protects any password change the developer made at
 * runtime from being reverted on the next restart.
 *
 * <p>Activated by:
 * <pre>
 *   spring.profiles.active=dev
 *   app.bootstrap.enabled=true
 * </pre>
 */
@Slf4j
@Profile("dev")
@Component
@RequiredArgsConstructor
@EnableConfigurationProperties(BootstrapProperties.class)
public class BootstrapDataRunner implements CommandLineRunner {

    private final BootstrapProperties properties;
    private final CompanyRepository companyRepository;
    private final ClinicRepository clinicRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(String... args) {
        if (!properties.enabled()) {
            log.debug("Bootstrap seed disabled (app.bootstrap.enabled=false). Skipping.");
            return;
        }

        Company company = findOrCreateCompany();
        Clinic clinic = findOrCreateClinic(company);

        String email = properties.ownerEmailOrDefault();
        if (userRepository.existsByEmailIgnoreCase(email)) {
            log.info("Bootstrap: owner '{}' already exists; leaving account untouched.", email);
            return;
        }

        Role ownerRole = roleRepository.findByCode(RoleType.CLINIC_OWNER)
                .orElseThrow(() -> new IllegalStateException(
                        "CLINIC_OWNER role is missing. Ensure V2__rbac_seed.sql ran successfully."));

        User owner = User.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode(properties.ownerPasswordOrDefault()))
                .firstName(properties.ownerFirstNameOrDefault())
                .lastName(properties.ownerLastNameOrDefault())
                .status(UserStatus.ACTIVE)
                .company(company)
                .clinic(clinic)
                .roles(new HashSet<>(Set.of(ownerRole)))
                .build();
        userRepository.save(owner);

        log.info("Bootstrap complete: company='{}', clinic='{}', owner='{}'.",
                company.getName(), clinic.getName(), email);
    }

    private Company findOrCreateCompany() {
        String name = properties.companyNameOrDefault();
        return companyRepository.findAll().stream()
                .filter(c -> name.equalsIgnoreCase(c.getName()))
                .findFirst()
                .orElseGet(() -> companyRepository.save(
                        Company.builder()
                                .name(name)
                                .taxNumber(resolveUniqueTaxNumber())
                                .build()));
    }

    private Clinic findOrCreateClinic(Company company) {
        String name = properties.clinicNameOrDefault();
        return clinicRepository.findAllByCompanyId(company.getId()).stream()
                .filter(c -> name.equalsIgnoreCase(c.getName()))
                .findFirst()
                .orElseGet(() -> clinicRepository.save(
                        Clinic.builder()
                                .company(company)
                                .name(name)
                                .build()));
    }

    /**
     * Avoids unique-constraint collisions if the configured tax number was already
     * claimed by a previously seeded (but renamed/deleted) company.
     */
    private String resolveUniqueTaxNumber() {
        String taxNumber = properties.companyTaxNumberOrDefault();
        if (taxNumber == null || taxNumber.isBlank()) {
            return null;
        }
        return companyRepository.existsByTaxNumber(taxNumber) ? null : taxNumber;
    }
}
