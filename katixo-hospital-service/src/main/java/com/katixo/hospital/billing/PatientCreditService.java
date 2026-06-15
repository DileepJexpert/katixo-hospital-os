package com.katixo.hospital.billing;

import com.katixo.hospital.common.exception.BusinessException;
import com.katixo.hospital.patient.Patient;
import com.katixo.hospital.patient.PatientRepository;
import com.katixo.hospital.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Patient credit: outstanding hospital-charge balance vs a configurable credit
 * limit, with an OK / WARN / BLOCK status the billing UI checks before allowing
 * a credit transaction. The in-process replacement for the ERP patient-credit API.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class PatientCreditService {

    private static final BigDecimal WARN_FRACTION = new BigDecimal("0.80");

    private final PatientRepository patientRepository;
    private final PatientBillRepository billRepository;

    @Transactional(readOnly = true)
    public Map<String, Object> credit(Long patientId) {
        var ctx = TenantContext.get();
        Patient patient = patientRepository.findByIdAndTenantIdAndBranchId(patientId, ctx.getTenantId(), branchId())
                .orElseThrow(() -> new BusinessException("PATIENT_NOT_FOUND", "Patient not found: " + patientId));
        BigDecimal limit = nz(patient.getCreditLimit());
        BigDecimal outstanding = nz(billRepository.sumOutstandingForPatient(ctx.getTenantId(), branchId(), patientId));

        Map<String, Object> view = new LinkedHashMap<>();
        view.put("patientId", patientId);
        view.put("creditLimit", limit);
        view.put("outstanding", outstanding);
        view.put("available", limit.signum() > 0 ? limit.subtract(outstanding) : null);
        view.put("status", creditStatus(limit, outstanding));
        return view;
    }

    public Map<String, Object> setCreditLimit(Long patientId, BigDecimal limit) {
        var ctx = TenantContext.get();
        Patient patient = patientRepository.findByIdAndTenantIdAndBranchId(patientId, ctx.getTenantId(), branchId())
                .orElseThrow(() -> new BusinessException("PATIENT_NOT_FOUND", "Patient not found: " + patientId));
        if (limit != null && limit.signum() < 0) {
            throw new BusinessException("INVALID_LIMIT", "Credit limit cannot be negative");
        }
        patient.setCreditLimit(nz(limit));
        patient.setUpdatedBy(userId());
        patientRepository.save(patient);
        return credit(patientId);
    }

    /** OK below 80% of limit, WARN from 80%, BLOCK at/over limit. NO_LIMIT when no ceiling set. */
    public static String creditStatus(BigDecimal limit, BigDecimal outstanding) {
        BigDecimal l = limit == null ? BigDecimal.ZERO : limit;
        BigDecimal o = outstanding == null ? BigDecimal.ZERO : outstanding;
        if (l.signum() <= 0) {
            return "NO_LIMIT";
        }
        if (o.compareTo(l) >= 0) {
            return "BLOCK";
        }
        if (o.compareTo(l.multiply(WARN_FRACTION)) >= 0) {
            return "WARN";
        }
        return "OK";
    }

    private BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private Long branchId() {
        return Long.parseLong(TenantContext.get().getBranchId());
    }

    private Long userId() {
        return Long.parseLong(TenantContext.get().getUserId());
    }
}
