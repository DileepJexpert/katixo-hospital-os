package com.katixo.hospital.nursing;

import com.katixo.hospital.audit.AuditService;
import com.katixo.hospital.common.exception.BusinessException;
import com.katixo.hospital.ipd.IPDAdmission;
import com.katixo.hospital.ipd.IPDAdmissionRepository;
import com.katixo.hospital.policy.HospitalPolicyCode;
import com.katixo.hospital.policy.PolicyService;
import com.katixo.hospital.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NursingIndentServiceTest {

    private static final String TENANT = "demo-tenant";

    @Mock
    private NursingIndentRepository indentRepository;
    @Mock
    private NursingIndentItemRepository itemRepository;
    @Mock
    private IPDAdmissionRepository admissionRepository;
    @Mock
    private PolicyService policyService;
    @Mock
    private AuditService auditService;
    @Mock
    private com.katixo.hospital.inventory.PharmacySaleService pharmacySaleService;

    private NursingIndentService service;

    @BeforeEach
    void setUp() {
        service = new NursingIndentService(indentRepository, itemRepository, admissionRepository,
                policyService, auditService, pharmacySaleService);
        TenantContext.set(new TenantContext(TENANT, "1", "1", "9", "nurse"));

        IPDAdmission admission = new IPDAdmission();
        admission.setPatientId(11L);
        admission.setAdmissionStatus(IPDAdmission.AdmissionStatus.ADMITTED);
        lenient().when(admissionRepository.findByIdAndTenantIdAndBranchId(5L, TENANT, 1L))
                .thenReturn(Optional.of(admission));
        lenient().when(indentRepository.nextIndentSequence()).thenReturn(100001L);
        lenient().when(indentRepository.save(any())).thenAnswer(inv -> {
            NursingIndent i = inv.getArgument(0);
            if (i.getId() == null) {
                ReflectionTestUtils.setField(i, "id", 42L);
            }
            return i;
        });
        lenient().when(itemRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(policyService.getPolicyValue(eq(HospitalPolicyCode.IPD_INDENT_APPROVAL_CATEGORIES), any()))
                .thenReturn("IMPLANT,NARCOTIC");
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private NursingIndentService.ItemRequest med(String code, int qty) {
        return new NursingIndentService.ItemRequest(code, code, qty, NursingIndentItem.ItemCategory.MEDICINE);
    }

    private NursingIndent owned(NursingIndent.IndentStatus status) {
        NursingIndent indent = new NursingIndent();
        indent.setTenantId(TENANT);
        indent.setHospitalGroupId(1L);
        indent.setBranchId(1L);
        indent.setAdmissionId(5L);
        indent.setPatientId(11L);
        indent.setIndentNumber("INDENT-100001");
        indent.setTotalItems(1);
        indent.setIndentStatus(status);
        ReflectionTestUtils.setField(indent, "id", 42L);
        lenient().when(indentRepository.findByIdAndTenantIdAndBranchId(42L, TENANT, 1L))
                .thenReturn(Optional.of(indent));
        return indent;
    }

    @Test
    void routineMedicineIndentIsAutoApproved() {
        NursingIndent indent = service.createIndent(5L, null, List.of(med("PARA-500", 10)));
        assertEquals(NursingIndent.IndentStatus.APPROVED, indent.getIndentStatus());
    }

    @Test
    void restrictedCategoryRequiresApproval() {
        NursingIndent indent = service.createIndent(5L, null, List.of(
                med("PARA-500", 10),
                new NursingIndentService.ItemRequest("STENT-1", "Cardiac stent", 1,
                        NursingIndentItem.ItemCategory.IMPLANT)));
        assertEquals(NursingIndent.IndentStatus.REQUESTED, indent.getIndentStatus());
    }

    @Test
    void emptyApprovalPolicyAutoApprovesEverything() {
        when(policyService.getPolicyValue(eq(HospitalPolicyCode.IPD_INDENT_APPROVAL_CATEGORIES), any()))
                .thenReturn("");
        NursingIndent indent = service.createIndent(5L, null, List.of(
                new NursingIndentService.ItemRequest("STENT-1", "Stent", 1,
                        NursingIndentItem.ItemCategory.IMPLANT)));
        assertEquals(NursingIndent.IndentStatus.APPROVED, indent.getIndentStatus());
    }

    @Test
    void indentForDischargedAdmissionIsRejected() {
        IPDAdmission discharged = new IPDAdmission();
        discharged.setPatientId(11L);
        discharged.setAdmissionStatus(IPDAdmission.AdmissionStatus.DISCHARGED);
        when(admissionRepository.findByIdAndTenantIdAndBranchId(5L, TENANT, 1L))
                .thenReturn(Optional.of(discharged));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.createIndent(5L, null, List.of(med("PARA-500", 1))));
        assertEquals("NOT_ADMITTED", ex.getCode());
    }

    @Test
    void approveOnlyFromRequested() {
        owned(NursingIndent.IndentStatus.DISPENSED);
        BusinessException ex = assertThrows(BusinessException.class, () -> service.approve(42L));
        assertEquals("INVALID_STATE", ex.getCode());
    }

    @Test
    void rejectRequiresReason() {
        owned(NursingIndent.IndentStatus.REQUESTED);
        BusinessException ex = assertThrows(BusinessException.class, () -> service.reject(42L, " "));
        assertEquals("REJECTION_REASON_REQUIRED", ex.getCode());
    }

    @Test
    void dispenseRequiresApprovedAndRaisesCreditSale() {
        owned(NursingIndent.IndentStatus.APPROVED);
        NursingIndentItem item = new NursingIndentItem();
        item.setMedicineCode("PARA-500");
        item.setQuantity(10);
        when(itemRepository.findByTenantIdAndIndentIdOrderById(TENANT, 42L)).thenReturn(List.of(item));

        com.katixo.hospital.inventory.PharmacySale sale = new com.katixo.hospital.inventory.PharmacySale();
        sale.setSaleNumber("PS-100001");
        sale.setGrandTotal(new java.math.BigDecimal("25.00"));
        ReflectionTestUtils.setField(sale, "id", 7L);
        when(pharmacySaleService.createSale(any())).thenReturn(sale);

        NursingIndent result = service.dispense(42L);

        assertEquals(NursingIndent.IndentStatus.DISPENSED, result.getIndentStatus());
        assertEquals("PS-100001", result.getSaleNumber());
        // CREDIT sale (settled at discharge), referencing this indent
        org.mockito.ArgumentCaptor<com.katixo.hospital.inventory.PharmacySaleService.SaleRequest> captor =
                org.mockito.ArgumentCaptor.forClass(com.katixo.hospital.inventory.PharmacySaleService.SaleRequest.class);
        verify(pharmacySaleService).createSale(captor.capture());
        assertEquals(com.katixo.hospital.inventory.PharmacySale.SaleType.CREDIT, captor.getValue().saleType());
        assertEquals("INDENT-42", captor.getValue().referenceId());
    }

    @Test
    void dispenseOfRequestedIndentIsBlocked() {
        owned(NursingIndent.IndentStatus.REQUESTED);
        BusinessException ex = assertThrows(BusinessException.class, () -> service.dispense(42L));
        assertEquals("INVALID_STATE", ex.getCode());
    }

    @Test
    void cancelledIndentCannotBeDispensed() {
        owned(NursingIndent.IndentStatus.CANCELLED);
        assertThrows(BusinessException.class, () -> service.dispense(42L));
        verify(pharmacySaleService, org.mockito.Mockito.never()).createSale(any());
    }
}
