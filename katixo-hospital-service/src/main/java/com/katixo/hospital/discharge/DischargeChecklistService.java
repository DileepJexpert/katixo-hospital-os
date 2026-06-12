package com.katixo.hospital.discharge;

import com.katixo.hospital.audit.AuditLog;
import com.katixo.hospital.audit.AuditService;
import com.katixo.hospital.common.entity.BaseEntity;
import com.katixo.hospital.common.exception.BusinessException;
import com.katixo.hospital.policy.HospitalPolicyCode;
import com.katixo.hospital.policy.PolicyService;
import com.katixo.hospital.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Discharge checklist: per-admission items seeded from a default set;
 * which incomplete items BLOCK discharge (vs only warn) comes from the
 * IPD_DISCHARGE_CHECKLIST_BLOCKING_ITEMS policy — a comma-separated list
 * of item codes — never hardcoded business rules.
 */
@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class DischargeChecklistService {

    private final DischargeChecklistItemRepository checklistRepository;
    private final PolicyService policyService;
    private final AuditService auditService;

    /** Seeded for every admission on first access; hospitals tune blocking via policy. */
    private static final Map<String, String> DEFAULT_ITEMS = new LinkedHashMap<>() {{
        put("FINAL_BILL_SETTLED", "Final bill settled / payment arranged");
        put("MEDICATIONS_HANDED_OVER", "Discharge medications handed over");
        put("DISCHARGE_SUMMARY_READY", "Discharge summary approved");
        put("FOLLOW_UP_SCHEDULED", "Follow-up appointment scheduled");
        put("PATIENT_EDUCATION_DONE", "Patient/family education completed");
        put("BELONGINGS_RETURNED", "Patient belongings returned");
    }};

    private static final String DEFAULT_BLOCKING_CODES =
            "FINAL_BILL_SETTLED,DISCHARGE_SUMMARY_READY,MEDICATIONS_HANDED_OVER";

    public ChecklistResponse getChecklist(Long admissionId) {
        var ctx = TenantContext.get();
        var items = checklistRepository.findByTenantIdAndBranchIdAndAdmissionIdOrderByIdAsc(
                ctx.getTenantId(), Long.parseLong(ctx.getBranchId()), admissionId);

        if (items.isEmpty()) {
            items = seedDefaults(admissionId);
        }
        return toResponse(admissionId, items);
    }

    public ChecklistResponse completeItem(Long itemId, String notes) {
        return setCompletion(itemId, true, notes);
    }

    public ChecklistResponse reopenItem(Long itemId) {
        return setCompletion(itemId, false, null);
    }

    /**
     * Discharge gate used by DischargeService.finalize: throws if any
     * policy-designated blocking item is incomplete. Returns the codes of
     * incomplete non-blocking items so callers can surface warnings.
     */
    @Transactional(readOnly = true)
    public List<String> assertDischargeAllowed(Long admissionId) {
        var ctx = TenantContext.get();
        var items = checklistRepository.findByTenantIdAndBranchIdAndAdmissionIdOrderByIdAsc(
                ctx.getTenantId(), Long.parseLong(ctx.getBranchId()), admissionId);
        if (items.isEmpty()) {
            // No checklist started for this admission — every default blocking item is open.
            throw new BusinessException("CHECKLIST_INCOMPLETE",
                    "Discharge checklist has not been completed for this admission");
        }

        var blocking = blockingCodes();
        var openBlocking = items.stream()
                .filter(i -> !Boolean.TRUE.equals(i.getCompleted()) && blocking.contains(i.getItemCode()))
                .map(DischargeChecklistItem::getItemName)
                .toList();
        if (!openBlocking.isEmpty()) {
            throw new BusinessException("CHECKLIST_BLOCKING_ITEMS_OPEN",
                    "Blocking checklist items incomplete: " + String.join(", ", openBlocking));
        }

        return items.stream()
                .filter(i -> !Boolean.TRUE.equals(i.getCompleted()))
                .map(DischargeChecklistItem::getItemCode)
                .toList();
    }

    private ChecklistResponse setCompletion(Long itemId, boolean completed, String notes) {
        var ctx = TenantContext.get();
        var item = checklistRepository.findByIdAndTenantIdAndBranchId(
                        itemId, ctx.getTenantId(), Long.parseLong(ctx.getBranchId()))
                .orElseThrow(() -> new BusinessException("CHECKLIST_ITEM_NOT_FOUND", "Checklist item not found"));

        var before = item.getCompleted();
        item.setCompleted(completed);
        item.setCompletedBy(completed ? Long.parseLong(ctx.getUserId()) : null);
        item.setCompletedAt(completed ? LocalDateTime.now() : null);
        if (notes != null && !notes.isBlank()) {
            item.setNotes(notes);
        }
        item.setUpdatedBy(Long.parseLong(ctx.getUserId()));
        item = checklistRepository.save(item);

        auditService.audit("DischargeChecklistItem", String.valueOf(item.getId()),
                AuditLog.AuditAction.UPDATE,
                Map.of("completed", String.valueOf(before)),
                Map.of("completed", String.valueOf(item.getCompleted()), "itemCode", item.getItemCode()),
                UUID.randomUUID().toString());

        return toResponse(item.getAdmissionId(),
                checklistRepository.findByTenantIdAndBranchIdAndAdmissionIdOrderByIdAsc(
                        ctx.getTenantId(), Long.parseLong(ctx.getBranchId()), item.getAdmissionId()));
    }

    private List<DischargeChecklistItem> seedDefaults(Long admissionId) {
        var ctx = TenantContext.get();
        var userId = Long.parseLong(ctx.getUserId());
        var items = DEFAULT_ITEMS.entrySet().stream().map(e -> {
            var item = new DischargeChecklistItem();
            item.setTenantId(ctx.getTenantId());
            item.setHospitalGroupId(Long.parseLong(ctx.getHospitalGroupId()));
            item.setBranchId(Long.parseLong(ctx.getBranchId()));
            item.setAdmissionId(admissionId);
            item.setItemCode(e.getKey());
            item.setItemName(e.getValue());
            item.setCompleted(false);
            item.setCreatedBy(userId);
            item.setUpdatedBy(userId);
            item.setStatus(BaseEntity.EntityStatus.ACTIVE);
            return item;
        }).collect(Collectors.toList());
        return checklistRepository.saveAll(items);
    }

    private Set<String> blockingCodes() {
        var csv = policyService.getPolicyValue(
                HospitalPolicyCode.IPD_DISCHARGE_CHECKLIST_BLOCKING_ITEMS, DEFAULT_BLOCKING_CODES);
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());
    }

    private ChecklistResponse toResponse(Long admissionId, List<DischargeChecklistItem> items) {
        var blocking = blockingCodes();
        var itemResponses = items.stream()
                .map(i -> new ChecklistItemResponse(
                        i.getId(), i.getItemCode(), i.getItemName(),
                        Boolean.TRUE.equals(i.getCompleted()),
                        blocking.contains(i.getItemCode()),
                        i.getCompletedBy(), i.getCompletedAt(), i.getNotes()))
                .toList();
        var blocked = itemResponses.stream().anyMatch(i -> i.blocking && !i.completed);
        return new ChecklistResponse(admissionId, blocked, itemResponses);
    }

    public record ChecklistItemResponse(Long id, String itemCode, String itemName,
                                        boolean completed, boolean blocking,
                                        Long completedBy, LocalDateTime completedAt, String notes) {
    }

    public record ChecklistResponse(Long admissionId, boolean dischargeBlocked,
                                    List<ChecklistItemResponse> items) {
    }
}
