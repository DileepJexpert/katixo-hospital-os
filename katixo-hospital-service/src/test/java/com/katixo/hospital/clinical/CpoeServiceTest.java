package com.katixo.hospital.clinical;

import com.katixo.hospital.audit.AuditService;
import com.katixo.hospital.clinical.cds.CdsAlert;
import com.katixo.hospital.clinical.cds.CdsService;
import com.katixo.hospital.common.exception.BusinessException;
import com.katixo.hospital.patient.Patient;
import com.katixo.hospital.patient.PatientRepository;
import com.katixo.hospital.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CpoeServiceTest {

    private static final String TENANT = "demo-tenant";

    @Mock ClinicalOrderRepository orderRepository;
    @Mock ClinicalService clinicalService;
    @Mock PatientRepository patientRepository;
    @Mock CdsService cdsService;
    @Mock AuditService auditService;
    @Mock ClinicalOrderRouter orderRouter;

    private CpoeService service;

    @BeforeEach
    void setUp() {
        service = new CpoeService(orderRepository, clinicalService, patientRepository, cdsService, auditService, orderRouter);
        TenantContext.set(new TenantContext(TENANT, "1", "1", "3", "doctor"));

        Encounter enc = new Encounter();
        enc.setPatientId(55L);
        enc.setEncounterStatus(Encounter.EncounterStatus.OPEN);
        lenient().when(clinicalService.getEncounter(10L)).thenReturn(enc);
        lenient().when(patientRepository.findByIdAndTenantIdAndBranchId(55L, TENANT, 1L))
                .thenReturn(Optional.of(new Patient()));
        lenient().when(orderRepository.findByTenantIdAndBranchIdAndEncounterIdOrderByIdDesc(any(), any(), any()))
                .thenReturn(List.of());
        lenient().when(orderRepository.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void criticalAlertBlocksOrderWithoutOverride() {
        when(cdsService.evaluate(any())).thenReturn(List.of(CdsAlert.critical("ALLERGY_MATCH", "allergy!")));
        when(cdsService.hasBlocking(any())).thenReturn(true);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.placeOrder(
                10L, ClinicalOrder.OrderType.PHARMACY, "AMOX", "Amoxicillin",
                ClinicalOrder.Priority.ROUTINE, null, null));
        assertEquals("CDS_BLOCKED", ex.getCode());
        verify(orderRepository, never()).save(any());
    }

    @Test
    void criticalAlertProceedsWithOverrideReason() {
        when(cdsService.evaluate(any())).thenReturn(List.of(CdsAlert.critical("ALLERGY_MATCH", "allergy!")));
        when(cdsService.hasBlocking(any())).thenReturn(true);

        CpoeService.PlaceResult r = service.placeOrder(
                10L, ClinicalOrder.OrderType.PHARMACY, "AMOX", "Amoxicillin",
                ClinicalOrder.Priority.STAT, "give now", "benefit outweighs risk");

        assertNotNull(r.order());
        assertEquals("benefit outweighs risk", r.order().getCdsOverrideReason());
        assertEquals(ClinicalOrder.OrderStatus.PLACED, r.order().getOrderStatus());
        verify(orderRepository).save(any());
    }

    @Test
    void cleanOrderIsPlaced() {
        when(cdsService.evaluate(any())).thenReturn(List.of());
        when(cdsService.hasBlocking(any())).thenReturn(false);

        CpoeService.PlaceResult r = service.placeOrder(
                10L, ClinicalOrder.OrderType.LAB, "CBC", "Complete Blood Count",
                null, null, null);

        assertEquals(ClinicalOrder.OrderStatus.PLACED, r.order().getOrderStatus());
        assertEquals(ClinicalOrder.Priority.ROUTINE, r.order().getPriority());
        verify(orderRepository).save(any());
    }

    @Test
    void routedOrderIsBackLinked() {
        when(cdsService.evaluate(any())).thenReturn(List.of());
        when(cdsService.hasBlocking(any())).thenReturn(false);
        when(orderRouter.route(any(), any())).thenReturn(new ClinicalOrderRouter.Ref("RADIOLOGY_ORDER", 99L));

        CpoeService.PlaceResult r = service.placeOrder(
                10L, ClinicalOrder.OrderType.RADIOLOGY, "CTHEAD", "CT Head",
                null, null, null);

        assertEquals("RADIOLOGY_ORDER", r.order().getLinkedRefType());
        assertEquals(99L, r.order().getLinkedRefId());
    }

    @Test
    void routingFailureDoesNotBreakPlacement() {
        when(cdsService.evaluate(any())).thenReturn(List.of());
        when(cdsService.hasBlocking(any())).thenReturn(false);
        when(orderRouter.route(any(), any())).thenThrow(new BusinessException("TEST_NOT_FOUND", "no such test"));

        CpoeService.PlaceResult r = service.placeOrder(
                10L, ClinicalOrder.OrderType.LAB, "BOGUS", "Unknown test",
                null, null, null);

        assertEquals(ClinicalOrder.OrderStatus.PLACED, r.order().getOrderStatus());
        assertEquals(null, r.order().getLinkedRefType());
    }
}
