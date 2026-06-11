package com.katixo.hospital.pharmacy;

import com.katixo.hospital.audit.AuditLog;
import com.katixo.hospital.audit.AuditService;
import com.katixo.hospital.common.entity.BaseEntity;
import com.katixo.hospital.common.exception.BusinessException;
import com.katixo.hospital.prescription.Prescription;
import com.katixo.hospital.prescription.PrescriptionItem;
import com.katixo.hospital.prescription.PrescriptionRepository;
import com.katixo.hospital.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional
public class PharmacyQueueService {

    private final PrescriptionDispenseRepository prescriptionDispenseRepository;
    private final PharmacyQueueItemRepository queueItemRepository;
    private final PrescriptionRepository prescriptionRepository;
    private final AuditService auditService;

    public PrescriptionDispense sendToPharmaQueue(Long prescriptionId) {
        TenantContext tenantContext = TenantContext.get();
        String tenantId = tenantContext.getTenantId();
        Long branchId = Long.parseLong(tenantContext.getBranchId());
        Long hospitalGroupId = Long.parseLong(tenantContext.getHospitalGroupId());

        Prescription rx = prescriptionRepository.findById(prescriptionId)
                .orElseThrow(() -> new BusinessException("RX_NOT_FOUND", "Prescription not found"));

        if (rx.getItems().isEmpty()) {
            throw new BusinessException("EMPTY_RX", "Prescription has no items");
        }

        PrescriptionDispense existingDispense = prescriptionDispenseRepository
                .findByTenantIdAndBranchIdAndPrescriptionId(tenantId, branchId, prescriptionId)
                .orElse(null);

        if (existingDispense != null && existingDispense.getDispenseStatus() != PrescriptionDispense.DispenseStatus.CANCELLED) {
            throw new BusinessException("RX_IN_QUEUE", "Prescription already in pharmacy queue");
        }

        PrescriptionDispense dispense = new PrescriptionDispense();
        dispense.setTenantId(tenantId);
        dispense.setHospitalGroupId(hospitalGroupId);
        dispense.setBranchId(branchId);
        dispense.setPrescriptionId(prescriptionId);
        dispense.setPatientId(rx.getPatientId());
        dispense.setVisitId(rx.getVisitId());
        dispense.setDispenseStatus(PrescriptionDispense.DispenseStatus.QUEUED);
        dispense.setTotalItems(rx.getItems().size());
        dispense.setCreatedBy(Long.parseLong(tenantContext.getUserId()));
        dispense.setStatus(BaseEntity.EntityStatus.ACTIVE);

        dispense = prescriptionDispenseRepository.save(dispense);

        int priority = 0;
        for (PrescriptionItem item : rx.getItems()) {
            PharmacyQueueItem queueItem = new PharmacyQueueItem();
            queueItem.setTenantId(tenantId);
            queueItem.setHospitalGroupId(hospitalGroupId);
            queueItem.setBranchId(branchId);
            queueItem.setDispenseId(dispense.getId());
            queueItem.setPrescriptionId(prescriptionId);
            queueItem.setPatientId(rx.getPatientId());
            queueItem.setMedicineCode(item.getMedicineCode());
            queueItem.setMedicineName(item.getMedicineName());
            queueItem.setQuantity(item.getQuantity());
            queueItem.setDosage(item.getDosage());
            queueItem.setFrequency(item.getFrequency());
            queueItem.setQueueStatus(PharmacyQueueItem.QueueStatus.PENDING);
            queueItem.setPriority(priority++);
            queueItem.setCreatedBy(Long.parseLong(tenantContext.getUserId()));
            queueItem.setStatus(BaseEntity.EntityStatus.ACTIVE);
            queueItemRepository.save(queueItem);
        }

        auditService.audit("PrescriptionDispense", String.valueOf(dispense.getId()),
                AuditLog.AuditAction.CREATE, null, dispense, java.util.UUID.randomUUID().toString());

        return dispense;
    }

    public Page<Map<String, Object>> getPharmacyQueue(int page, int size) {
        TenantContext tenantContext = TenantContext.get();
        String tenantId = tenantContext.getTenantId();
        Long branchId = Long.parseLong(tenantContext.getBranchId());

        Pageable pageable = PageRequest.of(page, size);
        Page<PharmacyQueueItem> items = queueItemRepository.findPendingItems(tenantId, branchId, pageable);

        return items.map(this::mapToQueueItemView);
    }

    public long getQueueLength() {
        TenantContext tenantContext = TenantContext.get();
        String tenantId = tenantContext.getTenantId();
        Long branchId = Long.parseLong(tenantContext.getBranchId());
        return queueItemRepository.countPendingItems(tenantId, branchId);
    }

    public PharmacyQueueItem startDispensing(Long queueItemId) {
        TenantContext tenantContext = TenantContext.get();
        String tenantId = tenantContext.getTenantId();
        Long branchId = Long.parseLong(tenantContext.getBranchId());

        PharmacyQueueItem item = queueItemRepository.findById(queueItemId)
                .orElseThrow(() -> new BusinessException("QUEUE_ITEM_NOT_FOUND", "Queue item not found"));

        if (!item.getTenantId().equals(tenantId) || !item.getBranchId().equals(branchId)) {
            throw new BusinessException("FORBIDDEN", "Access denied");
        }

        if (item.getQueueStatus() != PharmacyQueueItem.QueueStatus.PENDING) {
            throw new BusinessException("INVALID_STATE", "Item is not pending");
        }

        item.setQueueStatus(PharmacyQueueItem.QueueStatus.IN_PROGRESS);
        item.setUpdatedBy(Long.parseLong(tenantContext.getUserId()));
        item = queueItemRepository.save(item);

        auditService.audit("PharmacyQueueItem", String.valueOf(item.getId()),
                AuditLog.AuditAction.UPDATE, null, item, java.util.UUID.randomUUID().toString());

        return item;
    }

    public PharmacyQueueItem completeDispensing(Long queueItemId) {
        TenantContext tenantContext = TenantContext.get();
        String tenantId = tenantContext.getTenantId();
        Long branchId = Long.parseLong(tenantContext.getBranchId());

        PharmacyQueueItem item = queueItemRepository.findById(queueItemId)
                .orElseThrow(() -> new BusinessException("QUEUE_ITEM_NOT_FOUND", "Queue item not found"));

        if (!item.getTenantId().equals(tenantId) || !item.getBranchId().equals(branchId)) {
            throw new BusinessException("FORBIDDEN", "Access denied");
        }

        if (item.getQueueStatus() != PharmacyQueueItem.QueueStatus.IN_PROGRESS) {
            throw new BusinessException("INVALID_STATE", "Item is not in progress");
        }

        item.setQueueStatus(PharmacyQueueItem.QueueStatus.DISPENSED);
        item.setDispensedAt(LocalDateTime.now());
        item.setDispensedBy(Long.parseLong(tenantContext.getUserId()));
        item.setUpdatedBy(Long.parseLong(tenantContext.getUserId()));
        item = queueItemRepository.save(item);

        updateDispenseStatus(item.getDispenseId());

        auditService.audit("PharmacyQueueItem", String.valueOf(item.getId()),
                AuditLog.AuditAction.UPDATE, null, item, java.util.UUID.randomUUID().toString());

        return item;
    }

    public PharmacyQueueItem overridePriority(Long queueItemId, Integer newPriority, String reason) {
        TenantContext tenantContext = TenantContext.get();
        String tenantId = tenantContext.getTenantId();
        Long branchId = Long.parseLong(tenantContext.getBranchId());

        PharmacyQueueItem item = queueItemRepository.findById(queueItemId)
                .orElseThrow(() -> new BusinessException("QUEUE_ITEM_NOT_FOUND", "Queue item not found"));

        if (!item.getTenantId().equals(tenantId) || !item.getBranchId().equals(branchId)) {
            throw new BusinessException("FORBIDDEN", "Access denied");
        }

        if (item.getQueueStatus() != PharmacyQueueItem.QueueStatus.PENDING) {
            throw new BusinessException("INVALID_STATE", "Can only override priority for pending items");
        }

        if (item.getOriginalPriority() == null) {
            item.setOriginalPriority(item.getPriority());
        }

        item.setPriority(newPriority);
        item.setPriorityOverrideAt(LocalDateTime.now());
        item.setPriorityOverrideBy(Long.parseLong(tenantContext.getUserId()));
        item.setPriorityOverrideReason(reason);
        item.setUpdatedBy(Long.parseLong(tenantContext.getUserId()));
        item = queueItemRepository.save(item);

        auditService.audit("PharmacyQueueItem", String.valueOf(item.getId()),
                AuditLog.AuditAction.UPDATE, null, item, java.util.UUID.randomUUID().toString());

        return item;
    }

    public PharmacyQueueItem rejectItem(Long queueItemId, String reason) {
        TenantContext tenantContext = TenantContext.get();
        String tenantId = tenantContext.getTenantId();
        Long branchId = Long.parseLong(tenantContext.getBranchId());

        PharmacyQueueItem item = queueItemRepository.findById(queueItemId)
                .orElseThrow(() -> new BusinessException("QUEUE_ITEM_NOT_FOUND", "Queue item not found"));

        if (!item.getTenantId().equals(tenantId) || !item.getBranchId().equals(branchId)) {
            throw new BusinessException("FORBIDDEN", "Access denied");
        }

        if (item.getQueueStatus() == PharmacyQueueItem.QueueStatus.DISPENSED) {
            throw new BusinessException("INVALID_STATE", "Cannot reject already dispensed item");
        }

        item.setQueueStatus(PharmacyQueueItem.QueueStatus.REJECTED);
        item.setUpdatedBy(Long.parseLong(tenantContext.getUserId()));
        item = queueItemRepository.save(item);

        auditService.audit("PharmacyQueueItem", String.valueOf(item.getId()),
                AuditLog.AuditAction.UPDATE, null, item, java.util.UUID.randomUUID().toString());

        return item;
    }

    public Page<Map<String, Object>> getDispensedHistory(int page, int size) {
        TenantContext tenantContext = TenantContext.get();
        String tenantId = tenantContext.getTenantId();
        Long branchId = Long.parseLong(tenantContext.getBranchId());

        Pageable pageable = PageRequest.of(page, size);
        Page<PharmacyQueueItem> items = queueItemRepository.findDispensedItems(tenantId, branchId, pageable);

        return items.map(this::mapToQueueItemView);
    }

    private void updateDispenseStatus(Long dispenseId) {
        TenantContext tenantContext = TenantContext.get();
        String tenantId = tenantContext.getTenantId();
        Long branchId = Long.parseLong(tenantContext.getBranchId());

        PrescriptionDispense dispense = prescriptionDispenseRepository.findById(dispenseId)
                .orElseThrow(() -> new BusinessException("DISPENSE_NOT_FOUND", "Dispense not found"));

        List<PharmacyQueueItem> items = queueItemRepository.findByTenantIdAndBranchIdAndDispenseId(tenantId, branchId, dispenseId);

        if (items.isEmpty()) {
            return;
        }

        long dispensedCount = items.stream()
                .filter(i -> i.getQueueStatus() == PharmacyQueueItem.QueueStatus.DISPENSED)
                .count();

        if (dispensedCount == items.size()) {
            dispense.setDispenseStatus(PrescriptionDispense.DispenseStatus.FULLY_DISPENSED);
            dispense.setDispensedAt(LocalDateTime.now());
        } else if (dispensedCount > 0) {
            dispense.setDispenseStatus(PrescriptionDispense.DispenseStatus.PARTIALLY_DISPENSED);
        }

        dispense.setUpdatedBy(Long.parseLong(tenantContext.getUserId()));
        prescriptionDispenseRepository.save(dispense);
    }

    private Map<String, Object> mapToQueueItemView(PharmacyQueueItem item) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("itemId", item.getId());
        view.put("dispenseId", item.getDispenseId());
        view.put("prescriptionId", item.getPrescriptionId());
        view.put("patientId", item.getPatientId());
        view.put("medicineCode", item.getMedicineCode());
        view.put("medicineName", item.getMedicineName());
        view.put("quantity", item.getQuantity());
        view.put("dosage", item.getDosage());
        view.put("frequency", item.getFrequency());
        view.put("queueStatus", item.getQueueStatus().name());
        view.put("priority", item.getPriority());
        view.put("isPriorityOverridden", item.isPriorityOverridden());
        if (item.isPriorityOverridden()) {
            view.put("originalPriority", item.getOriginalPriority());
            view.put("priorityOverrideReason", item.getPriorityOverrideReason());
            view.put("overriddenAt", item.getPriorityOverrideAt().toString());
        }
        if (item.getQueueStatus() == PharmacyQueueItem.QueueStatus.DISPENSED) {
            view.put("dispensedAt", item.getDispensedAt().toString());
        }
        view.put("createdAt", item.getCreatedAt().toString());
        return view;
    }
}
