package com.katixo.hospital.vendor;

import com.katixo.hospital.audit.AuditService;
import com.katixo.hospital.common.entity.BaseEntity;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VendorServiceTest {

    private static final String TENANT = "demo-tenant";

    @Mock VendorRepository vendorRepository;
    @Mock AuditService auditService;

    private VendorService service;

    @BeforeEach
    void setUp() {
        service = new VendorService(vendorRepository, auditService);
        TenantContext.set(new TenantContext(TENANT, "1", "1", "9", "billing"));
        lenient().when(vendorRepository.nextVendorSequence()).thenReturn(1001L);
        lenient().when(vendorRepository.save(any())).thenAnswer(inv -> {
            Vendor v = inv.getArgument(0);
            if (v.getId() == null) {
                ReflectionTestUtils.setField(v, "id", 1L);
            }
            return v;
        });
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void createStampsTenantAndGeneratesCode() {
        Vendor v = service.create(VendorService.VendorInput.builder()
                .name("MedSupply Distributors")
                .vendorType(Vendor.VendorType.SUPPLIER)
                .gstin("22AAAAA0000A1Z5")
                .build());

        assertEquals("VEN-1001", v.getVendorCode());
        assertEquals("MedSupply Distributors", v.getName());
        assertEquals(TENANT, v.getTenantId());
        assertTrue(v.isActive());
        assertEquals("22AAAAA0000A1Z5", v.getGstin());
    }

    @Test
    void createDefaultsTypeToSupplier() {
        Vendor v = service.create(VendorService.VendorInput.builder().name("Acme").build());
        assertEquals(Vendor.VendorType.SUPPLIER, v.getVendorType());
    }

    @Test
    void createRejectsBlankName() {
        assertThrows(BusinessException.class, () -> service.create(
                VendorService.VendorInput.builder().name("  ").build()));
    }

    @Test
    void blankOptionalFieldsStoredAsNull() {
        Vendor v = service.create(VendorService.VendorInput.builder()
                .name("Acme").gstin("   ").contactEmail("").build());
        assertEquals(null, v.getGstin());
        assertEquals(null, v.getContactEmail());
    }

    @Test
    void deactivateFlipsActiveAndStatus() {
        Vendor existing = new Vendor();
        ReflectionTestUtils.setField(existing, "id", 5L);
        existing.setVendorCode("VEN-1005");
        existing.setName("Old Vendor");
        existing.setActive(true);
        when(vendorRepository.findByIdAndTenantIdAndBranchId(anyLong(), anyString(), anyLong()))
                .thenReturn(Optional.of(existing));

        Vendor v = service.setActive(5L, false);
        assertFalse(v.isActive());
        assertEquals(BaseEntity.EntityStatus.INACTIVE, v.getStatus());
    }

    @Test
    void updateOnUnknownVendorThrows() {
        when(vendorRepository.findByIdAndTenantIdAndBranchId(anyLong(), anyString(), anyLong()))
                .thenReturn(Optional.empty());
        assertThrows(BusinessException.class, () -> service.update(99L,
                VendorService.VendorInput.builder().name("X").build()));
    }
}
