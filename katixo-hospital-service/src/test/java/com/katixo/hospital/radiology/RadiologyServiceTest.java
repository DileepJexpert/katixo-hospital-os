package com.katixo.hospital.radiology;

import com.katixo.hospital.audit.AuditService;
import com.katixo.hospital.common.exception.BusinessException;
import com.katixo.hospital.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RadiologyServiceTest {

    private static final String TENANT = "demo-tenant";

    @Mock RadiologyOrderRepository orderRepository;
    @Mock AuditService auditService;

    private RadiologyService service;

    @BeforeEach
    void setUp() {
        service = new RadiologyService(orderRepository, auditService);
        TenantContext.set(new TenantContext(TENANT, "1", "1", "9", "doctor"));
        lenient().when(orderRepository.nextOrderSequence()).thenReturn(1001L);
        lenient().when(orderRepository.save(any())).thenAnswer(inv -> {
            RadiologyOrder o = inv.getArgument(0);
            if (o.getId() == null) ReflectionTestUtils.setField(o, "id", 4L);
            return o;
        });
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void orderCreatesOrdered() {
        RadiologyOrder o = service.order(100L, 200L, RadiologyOrder.Modality.CT, "CT Head", null);
        assertEquals("RAD-1001", o.getOrderNumber());
        assertEquals(RadiologyOrder.RadiologyStatus.ORDERED, o.getRadiologyStatus());
    }

    @Test
    void orderRequiresStudyName() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.order(100L, 200L, RadiologyOrder.Modality.XRAY, "  ", null));
        assertEquals("RAD_STUDY_REQUIRED", ex.getCode());
    }

    @Test
    void performThenReportReleasesStudy() {
        RadiologyOrder o = new RadiologyOrder();
        o.setRadiologyStatus(RadiologyOrder.RadiologyStatus.ORDERED);
        ReflectionTestUtils.setField(o, "id", 4L);
        when(orderRepository.findByIdAndTenantIdAndBranchId(4L, TENANT, 1L)).thenReturn(Optional.of(o));

        assertEquals(RadiologyOrder.RadiologyStatus.PERFORMED, service.markPerformed(4L).getRadiologyStatus());
        RadiologyOrder reported = service.report(4L, "No fracture", "Normal study");
        assertEquals(RadiologyOrder.RadiologyStatus.REPORTED, reported.getRadiologyStatus());
        assertEquals("Normal study", reported.getImpression());
    }

    @Test
    void reportRequiresImpression() {
        RadiologyOrder o = new RadiologyOrder();
        o.setRadiologyStatus(RadiologyOrder.RadiologyStatus.PERFORMED);
        ReflectionTestUtils.setField(o, "id", 4L);
        when(orderRepository.findByIdAndTenantIdAndBranchId(4L, TENANT, 1L)).thenReturn(Optional.of(o));
        BusinessException ex = assertThrows(BusinessException.class, () -> service.report(4L, "x", "  "));
        assertEquals("RAD_IMPRESSION_REQUIRED", ex.getCode());
    }

    @Test
    void cannotCancelReported() {
        RadiologyOrder o = new RadiologyOrder();
        o.setRadiologyStatus(RadiologyOrder.RadiologyStatus.REPORTED);
        ReflectionTestUtils.setField(o, "id", 4L);
        when(orderRepository.findByIdAndTenantIdAndBranchId(4L, TENANT, 1L)).thenReturn(Optional.of(o));
        BusinessException ex = assertThrows(BusinessException.class, () -> service.cancel(4L, "x"));
        assertEquals("INVALID_STATE", ex.getCode());
    }
}
