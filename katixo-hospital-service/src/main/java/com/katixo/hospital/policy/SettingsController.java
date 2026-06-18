package com.katixo.hospital.policy;

import com.katixo.hospital.common.dto.ApiResponse;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Hospital-level feature flags the UI reads to show/hide modules — e.g. whether
 * this hospital runs its own pharmacy. Backed by the policy engine.
 */
@RestController
@RequestMapping("/api/v1/settings")
@RequiredArgsConstructor
public class SettingsController {

    private final PolicyService policyService;

    @GetMapping("/features")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Map<String, Object>>> features() {
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("pharmacyEnabled",
                policyService.getPolicyAsBoolean(HospitalPolicyCode.PHARMACY_ENABLED, true));
        v.put("smsEnabled",
                policyService.getPolicyAsBoolean(HospitalPolicyCode.ENABLE_SMS_NOTIFICATION, false));
        v.put("whatsappEnabled",
                policyService.getPolicyAsBoolean(HospitalPolicyCode.ENABLE_WHATSAPP_NOTIFICATION, false));
        v.put("patientPortalEnabled",
                policyService.getPolicyAsBoolean(HospitalPolicyCode.ENABLE_PATIENT_PORTAL, false));
        v.put("expenseApprovalThreshold",
                policyService.getPolicyAsBigDecimal(HospitalPolicyCode.EXPENSE_APPROVAL_THRESHOLD,
                        java.math.BigDecimal.ZERO));
        v.put("dischargeChecklistBlockingItems",
                policyService.getPolicyValue(HospitalPolicyCode.IPD_DISCHARGE_CHECKLIST_BLOCKING_ITEMS, ""));
        v.put("dischargeChecklistWarningItems",
                policyService.getPolicyValue(HospitalPolicyCode.IPD_DISCHARGE_CHECKLIST_WARNING_ITEMS, ""));
        return respond(v, "Feature flags");
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FeaturesRequest {
        private Boolean pharmacyEnabled;
        private Boolean smsEnabled;
        private Boolean whatsappEnabled;
        private Boolean patientPortalEnabled;
        private java.math.BigDecimal expenseApprovalThreshold;
        private String dischargeChecklistBlockingItems;
        private String dischargeChecklistWarningItems;
    }

    @PutMapping("/features")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> updateFeatures(@RequestBody FeaturesRequest req) {
        if (req.getPharmacyEnabled() != null) {
            policyService.setPolicy(HospitalPolicyCode.PHARMACY_ENABLED, req.getPharmacyEnabled().toString());
        }
        if (req.getSmsEnabled() != null) {
            policyService.setPolicy(HospitalPolicyCode.ENABLE_SMS_NOTIFICATION, req.getSmsEnabled().toString());
        }
        if (req.getWhatsappEnabled() != null) {
            policyService.setPolicy(HospitalPolicyCode.ENABLE_WHATSAPP_NOTIFICATION, req.getWhatsappEnabled().toString());
        }
        if (req.getPatientPortalEnabled() != null) {
            policyService.setPolicy(HospitalPolicyCode.ENABLE_PATIENT_PORTAL, req.getPatientPortalEnabled().toString());
        }
        if (req.getExpenseApprovalThreshold() != null) {
            policyService.setPolicy(HospitalPolicyCode.EXPENSE_APPROVAL_THRESHOLD,
                    req.getExpenseApprovalThreshold().toPlainString());
        }
        if (req.getDischargeChecklistBlockingItems() != null) {
            policyService.setPolicy(HospitalPolicyCode.IPD_DISCHARGE_CHECKLIST_BLOCKING_ITEMS,
                    req.getDischargeChecklistBlockingItems().trim());
        }
        if (req.getDischargeChecklistWarningItems() != null) {
            policyService.setPolicy(HospitalPolicyCode.IPD_DISCHARGE_CHECKLIST_WARNING_ITEMS,
                    req.getDischargeChecklistWarningItems().trim());
        }
        return features();
    }

    private ResponseEntity<ApiResponse<Map<String, Object>>> respond(Map<String, Object> data, String message) {
        return ResponseEntity.ok(ApiResponse.<Map<String, Object>>builder()
                .success(true).status(HttpStatus.OK.value()).message(message)
                .correlationId(UUID.randomUUID()).data(data).build());
    }
}
