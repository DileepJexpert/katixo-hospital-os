package com.katixo.hospital.ipd;

import com.katixo.hospital.audit.AuditService;
import com.katixo.hospital.patient.PatientRepository;
import com.katixo.hospital.patient.PatientVisitSummaryRepository;
import com.katixo.hospital.policy.HospitalPolicyCode;
import com.katixo.hospital.policy.PolicyService;
import com.katixo.hospital.realtime.BoardBroadcaster;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IPDServiceTest {

    @Mock WardRepository wardRepository;
    @Mock RoomRepository roomRepository;
    @Mock BedRepository bedRepository;
    @Mock IPDAdmissionRepository admissionRepository;
    @Mock BedAllocationRepository allocationRepository;
    @Mock BedIsolationRepository isolationRepository;
    @Mock PatientRepository patientRepository;
    @Mock PatientVisitSummaryRepository visitSummaryRepository;
    @Mock PolicyService policyService;
    @Mock AuditService auditService;
    @Mock com.katixo.hospital.outbox.OutboxEventService outboxEventService;
    @Mock BoardBroadcaster boardBroadcaster;
    @Mock com.katixo.hospital.clinical.ClinicalService clinicalService;

    @InjectMocks IPDService service;

    @Test
    @SuppressWarnings("unchecked")
    void dischargeChecklistParsesBothTiers() {
        when(policyService.getPolicyValue(HospitalPolicyCode.IPD_DISCHARGE_CHECKLIST_BLOCKING_ITEMS, ""))
                .thenReturn("FINAL_BILL_CLEARED, medicines_returned ,FINAL_BILL_CLEARED");
        when(policyService.getPolicyValue(HospitalPolicyCode.IPD_DISCHARGE_CHECKLIST_WARNING_ITEMS, ""))
                .thenReturn("FOLLOW_UP_SCHEDULED");

        Map<String, Object> cl = service.getDischargeChecklist();

        List<String> blocking = (List<String>) cl.get("blockingItems");
        List<String> warning = (List<String>) cl.get("warningItems");
        // trimmed, upper-cased, de-duplicated
        assertEquals(List.of("FINAL_BILL_CLEARED", "MEDICINES_RETURNED"), blocking);
        assertEquals(List.of("FOLLOW_UP_SCHEDULED"), warning);
    }

    @Test
    @SuppressWarnings("unchecked")
    void dischargeChecklistEmptyWhenPoliciesBlank() {
        lenient().when(policyService.getPolicyValue(HospitalPolicyCode.IPD_DISCHARGE_CHECKLIST_BLOCKING_ITEMS, ""))
                .thenReturn("");
        lenient().when(policyService.getPolicyValue(HospitalPolicyCode.IPD_DISCHARGE_CHECKLIST_WARNING_ITEMS, ""))
                .thenReturn(null);

        Map<String, Object> cl = service.getDischargeChecklist();

        assertTrue(((List<String>) cl.get("blockingItems")).isEmpty());
        assertTrue(((List<String>) cl.get("warningItems")).isEmpty());
    }
}
