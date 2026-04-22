package com.wedent.clinic.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the dev-only bootstrap seed.
 *
 * <p>Bound from {@code app.bootstrap.*} in {@code application-dev.yml}. Kept as a
 * record with nullable fields and in-code defaults so missing config keys don't
 * blow up startup — we just fall back to sensible local-dev values.
 */
@ConfigurationProperties(prefix = "app.bootstrap")
public record BootstrapProperties(
        boolean enabled,
        String companyName,
        String companyTaxNumber,
        String clinicName,
        String ownerEmail,
        String ownerPassword,
        String ownerFirstName,
        String ownerLastName
) {

    public String companyNameOrDefault() {
        return companyName != null && !companyName.isBlank() ? companyName : "Wedent Demo";
    }

    public String companyTaxNumberOrDefault() {
        return companyTaxNumber != null && !companyTaxNumber.isBlank() ? companyTaxNumber : "0000000001";
    }

    public String clinicNameOrDefault() {
        return clinicName != null && !clinicName.isBlank() ? clinicName : "Wedent Main Clinic";
    }

    public String ownerEmailOrDefault() {
        return ownerEmail != null && !ownerEmail.isBlank() ? ownerEmail : "owner@wedent.local";
    }

    public String ownerPasswordOrDefault() {
        return ownerPassword != null && !ownerPassword.isBlank() ? ownerPassword : "ChangeMe!123";
    }

    public String ownerFirstNameOrDefault() {
        return ownerFirstName != null && !ownerFirstName.isBlank() ? ownerFirstName : "Wedent";
    }

    public String ownerLastNameOrDefault() {
        return ownerLastName != null && !ownerLastName.isBlank() ? ownerLastName : "Owner";
    }
}
