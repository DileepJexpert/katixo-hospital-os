package com.katixo.hospital.billing;

import com.katixo.hospital.audit.AuditService;
import com.katixo.hospital.common.exception.BusinessException;
import com.katixo.hospital.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PackageServiceTest {

    private static final String TENANT = "demo-tenant";

    @Mock BillingPackageRepository packageRepository;
    @Mock PackageComponentRepository componentRepository;
    @Mock BillingService billingService;
    @Mock AuditService auditService;

    private PackageService service;

    @BeforeEach
    void setUp() {
        service = new PackageService(packageRepository, componentRepository, billingService, auditService);
        TenantContext.set(new TenantContext(TENANT, "1", "1", "9", "admin"));
        lenient().when(packageRepository.save(any())).thenAnswer(inv -> {
            BillingPackage p = inv.getArgument(0);
            if (p.getId() == null) ReflectionTestUtils.setField(p, "id", 3L);
            return p;
        });
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private PackageService.ComponentInput comp(String code, String name, String qty) {
        return PackageService.ComponentInput.builder()
                .serviceCode(code).serviceName(name).includedQuantity(new BigDecimal(qty)).build();
    }

    @Test
    void createPackageWithComponents() {
        BillingPackage p = service.createPackage("DELIV", "Normal delivery",
                BillingPackage.PackageType.FIXED, new BigDecimal("25000"), null,
                List.of(comp("ROOM", "Room", "3"), comp("DOC", "Doctor", "1")));
        assertEquals("DELIV", p.getCode());
        assertEquals(new BigDecimal("25000"), p.getPackagePrice());
        verify(componentRepository, org.mockito.Mockito.times(2)).save(any());
    }

    @Test
    void createRejectsDuplicateCode() {
        when(packageRepository.existsByTenantIdAndBranchIdAndCode(TENANT, 1L, "DELIV")).thenReturn(true);
        BusinessException ex = assertThrows(BusinessException.class, () -> service.createPackage(
                "DELIV", "X", BillingPackage.PackageType.FIXED, new BigDecimal("1"), null, List.of()));
        assertEquals("PKG_CODE_EXISTS", ex.getCode());
    }

    @Test
    void applyToEncounterAddsPackageCharge() {
        BillingPackage p = new BillingPackage();
        p.setCode("DELIV");
        p.setName("Normal delivery");
        p.setPackagePrice(new BigDecimal("25000"));
        p.setActive(true);
        ReflectionTestUtils.setField(p, "id", 3L);
        when(packageRepository.findByIdAndTenantIdAndBranchId(3L, TENANT, 1L)).thenReturn(Optional.of(p));

        service.applyToEncounter(3L, 100L, HospitalCharge.SourceType.IPD_ADMISSION, 50L);

        verify(billingService).addPackageCharge(eq(100L), eq(HospitalCharge.SourceType.IPD_ADMISSION),
                eq(50L), eq("DELIV"), eq("Normal delivery"), eq(new BigDecimal("25000")));
    }

    @Test
    void applyRejectsInactivePackage() {
        BillingPackage p = new BillingPackage();
        p.setActive(false);
        ReflectionTestUtils.setField(p, "id", 3L);
        when(packageRepository.findByIdAndTenantIdAndBranchId(3L, TENANT, 1L)).thenReturn(Optional.of(p));
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.applyToEncounter(3L, 100L, HospitalCharge.SourceType.OPD_VISIT, 50L));
        assertEquals("PKG_INACTIVE", ex.getCode());
    }
}
