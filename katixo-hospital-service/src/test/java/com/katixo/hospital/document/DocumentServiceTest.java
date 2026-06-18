package com.katixo.hospital.document;

import com.katixo.hospital.audit.AuditService;
import com.katixo.hospital.common.entity.BaseEntity;
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

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocumentServiceTest {

    private static final String TENANT = "demo-tenant";

    @Mock DocumentMetadataRepository repository;
    @Mock DocumentStorageProvider storageProvider;
    @Mock AuditService auditService;

    private DocumentService service;

    @BeforeEach
    void setUp() {
        service = new DocumentService(repository, storageProvider, auditService);
        ReflectionTestUtils.setField(service, "maxBytes", 10_485_760L);
        TenantContext.set(new TenantContext(TENANT, "1", "1", "9", "frontdesk1"));
        lenient().when(repository.save(any())).thenAnswer(inv -> {
            DocumentMetadata m = inv.getArgument(0);
            if (m.getId() == null) ReflectionTestUtils.setField(m, "id", 55L);
            return m;
        });
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void uploadStoresBytesAndSavesMetadata() {
        when(storageProvider.store(eq(TENANT), eq("scan.pdf"), any()))
                .thenReturn("demo-tenant/abc_scan.pdf");
        byte[] content = "hello".getBytes();

        DocumentMetadata saved = service.upload("LAB_REPORT", 7L, "scan.pdf", "application/pdf", content);

        assertEquals("demo-tenant/abc_scan.pdf", saved.getStorageKey());
        assertEquals("frontdesk1", saved.getUploadedByName());
        assertEquals("LAB_REPORT", saved.getEntityType());
        assertEquals(7L, saved.getEntityId());
        assertEquals(5L, saved.getSizeBytes());
        assertEquals(TENANT, saved.getTenantId());
        verify(storageProvider).store(eq(TENANT), eq("scan.pdf"), eq(content));
        verify(repository).save(any());
    }

    @Test
    void uploadRejectsEmptyContent() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.upload("PATIENT", 1L, "x.pdf", "application/pdf", new byte[0]));
        assertEquals("DOC_EMPTY", ex.getCode());
        verify(storageProvider, never()).store(any(), any(), any());
    }

    @Test
    void uploadRejectsOverMaxSize() {
        ReflectionTestUtils.setField(service, "maxBytes", 4L);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.upload("PATIENT", 1L, "x.pdf", "application/pdf", "12345".getBytes()));
        assertEquals("DOC_TOO_LARGE", ex.getCode());
        verify(storageProvider, never()).store(any(), any(), any());
    }

    @Test
    void uploadRejectsMissingEntityType() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.upload("  ", 1L, "x.pdf", "application/pdf", "data".getBytes()));
        assertEquals("DOC_ENTITY_TYPE_REQUIRED", ex.getCode());
        verify(storageProvider, never()).store(any(), any(), any());
    }

    @Test
    void listByEntityUsesEntityFinder() {
        when(repository.findByTenantIdAndBranchIdAndEntityTypeAndEntityIdOrderByIdDesc(
                TENANT, 1L, "TPA_CASE", 3L)).thenReturn(List.of(doc(1L, BaseEntity.EntityStatus.ACTIVE)));
        List<DocumentMetadata> result = service.list("TPA_CASE", 3L);
        assertEquals(1, result.size());
        verify(repository).findByTenantIdAndBranchIdAndEntityTypeAndEntityIdOrderByIdDesc(
                TENANT, 1L, "TPA_CASE", 3L);
        verify(repository, never()).findByTenantIdAndBranchIdOrderByIdDesc(any(), any(), any());
    }

    @Test
    void listByEntityExcludesDeleted() {
        when(repository.findByTenantIdAndBranchIdAndEntityTypeAndEntityIdOrderByIdDesc(
                TENANT, 1L, "PATIENT", 2L)).thenReturn(List.of(
                doc(1L, BaseEntity.EntityStatus.ACTIVE), doc(2L, BaseEntity.EntityStatus.DELETED)));
        assertEquals(1, service.list("PATIENT", 2L).size());
    }

    @Test
    void deleteSoftDeletesAndCallsProvider() {
        DocumentMetadata m = doc(8L, BaseEntity.EntityStatus.ACTIVE);
        m.setStorageKey("demo-tenant/k_file.pdf");
        when(repository.findByIdAndTenantIdAndBranchId(8L, TENANT, 1L)).thenReturn(Optional.of(m));

        service.delete(8L);

        ArgumentCaptor<DocumentMetadata> cap = ArgumentCaptor.forClass(DocumentMetadata.class);
        verify(repository).save(cap.capture());
        assertEquals(BaseEntity.EntityStatus.DELETED, cap.getValue().getStatus());
        verify(storageProvider).delete(TENANT, "demo-tenant/k_file.pdf");
    }

    @Test
    void getRejectsCrossTenant() {
        // repository returns empty when scoped to this tenant+branch (cross-tenant row not found)
        when(repository.findByIdAndTenantIdAndBranchId(99L, TENANT, 1L)).thenReturn(Optional.empty());
        BusinessException ex = assertThrows(BusinessException.class, () -> service.get(99L));
        assertEquals("DOC_NOT_FOUND", ex.getCode());
    }

    @Test
    void downloadLoadsBytesFromProvider() {
        DocumentMetadata m = doc(8L, BaseEntity.EntityStatus.ACTIVE);
        m.setStorageKey("demo-tenant/k_file.pdf");
        when(repository.findByIdAndTenantIdAndBranchId(8L, TENANT, 1L)).thenReturn(Optional.of(m));
        when(storageProvider.load(TENANT, "demo-tenant/k_file.pdf")).thenReturn("bytes".getBytes());

        DocumentService.DocumentDownload dl = service.download(8L);
        assertEquals("bytes", new String(dl.content()));
        assertEquals(m, dl.meta());
    }

    private DocumentMetadata doc(Long id, BaseEntity.EntityStatus status) {
        DocumentMetadata m = new DocumentMetadata();
        ReflectionTestUtils.setField(m, "id", id);
        m.setTenantId(TENANT);
        m.setHospitalGroupId(1L);
        m.setBranchId(1L);
        m.setEntityType("PATIENT");
        m.setEntityId(2L);
        m.setFileName("file.pdf");
        m.setContentType("application/pdf");
        m.setSizeBytes(5L);
        m.setStorageKey("demo-tenant/k_file.pdf");
        m.setStatus(status);
        return m;
    }
}
