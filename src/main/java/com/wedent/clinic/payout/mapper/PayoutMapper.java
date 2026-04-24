package com.wedent.clinic.payout.mapper;

import com.wedent.clinic.employee.entity.DoctorProfile;
import com.wedent.clinic.employee.entity.Employee;
import com.wedent.clinic.payout.dto.PayoutDeductionResponse;
import com.wedent.clinic.payout.dto.PayoutResponse;
import com.wedent.clinic.payout.dto.PayoutTreatmentLine;
import com.wedent.clinic.payout.entity.PayoutDeduction;
import com.wedent.clinic.payout.entity.PayoutPeriod;
import com.wedent.clinic.treatment.entity.Treatment;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * Response-only mapper. Request payloads are materialized by the
 * service (snapshot semantics make a round-trip mapper awkward),
 * so this class just flattens the aggregate into a DTO tree.
 */
@Component
public class PayoutMapper {

    public PayoutResponse toResponse(PayoutPeriod period,
                                     List<PayoutTreatmentLine> includedTreatments) {
        DoctorProfile profile = period.getDoctorProfile();
        Employee employee = profile == null ? null : profile.getEmployee();

        List<PayoutDeductionResponse> deductions = period.getDeductions() == null
                ? List.of()
                : period.getDeductions().stream().map(this::toDeduction).toList();

        return new PayoutResponse(
                period.getId(),
                period.getCompany().getId(),
                period.getClinic().getId(),
                profile == null ? null : profile.getId(),
                employee == null ? null : employee.getId(),
                employee == null ? null : employee.getFirstName() + " " + employee.getLastName(),
                period.getPeriodStart(),
                period.getPeriodEnd(),
                period.getStatus(),
                period.getTreatmentTotalSnapshot(),
                period.getCommissionRateSnapshot(),
                period.getGrossAmount(),
                period.getTotalDeduction(),
                period.getNetAmount(),
                period.getApprovedAt(),
                period.getPaidAt(),
                period.getCreatedAt(),
                period.getUpdatedAt(),
                deductions,
                includedTreatments == null ? List.of() : includedTreatments
        );
    }

    /** List-endpoint variant — skips the treatments join. */
    public PayoutResponse toSummary(PayoutPeriod period) {
        return toResponse(period, Collections.emptyList());
    }

    public PayoutDeductionResponse toDeduction(PayoutDeduction d) {
        return new PayoutDeductionResponse(
                d.getId(),
                d.getType(),
                d.getDescription(),
                d.getAmount()
        );
    }

    public PayoutTreatmentLine toTreatmentLine(Treatment t) {
        var patient = t.getPatient();
        String patientName = patient == null ? null
                : (patient.getFirstName() + " " + patient.getLastName()).trim();
        return new PayoutTreatmentLine(
                t.getId(),
                patient == null ? null : patient.getId(),
                patientName,
                t.getName(),
                t.getCompletedAt(),
                t.getFee()
        );
    }
}
