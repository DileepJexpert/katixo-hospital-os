package com.katixo.hospital.nursing;

import com.katixo.hospital.audit.AuditLog;
import com.katixo.hospital.audit.AuditService;
import com.katixo.hospital.common.ApiException;
import com.katixo.hospital.common.entity.BaseEntity;
import com.katixo.hospital.outbox.OutboxEvent;
import com.katixo.hospital.outbox.OutboxPublisher;
import com.katixo.hospital.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class NursingService {

    private final NursingIndentRepository indentRepository;
    private final NursingIndentItemRepository itemRepository;
    private final AuditService auditService;
    private final OutboxPublisher outboxPublisher;
    private final TenantContext tenantContext;

    private static final String INDENT_NUMBER_FORMAT = "INDENT-%d-%05d";

    public NursingIndentWithItems createIndent(CreateIndentRequest request) {
        var ctx = tenantContext.current();
        var indentNumber = generateIndentNumber();

        var indent = new NursingIndent();
        indent.setTenantId(ctx.getTenantId());
        indent.setHospitalGroupId(Long.parseLong(ctx.getHospitalGroupId()));
        indent.setBranchId(Long.parseLong(ctx.getBranchId()));
        indent.setIndentNumber(indentNumber);
        indent.setAdmissionId(request.admissionId);
        indent.setWardSection(request.wardSection);
        indent.setIndentStatus(NursingIndent.IndentStatus.PENDING);
        indent.setRequestedBy(ctx.getCurrentUserId());
        indent.setNotes(request.notes);
        indent.setCreatedBy(ctx.getCurrentUserId());
        indent.setUpdatedBy(ctx.getCurrentUserId());

        indent = indentRepository.save(indent);

        List<NursingIndentItem> items = request.items.stream()
                .map(itemReq -> {
                    var item = new NursingIndentItem();
                    item.setTenantId(ctx.getTenantId());
                    item.setHospitalGroupId(Long.parseLong(ctx.getHospitalGroupId()));
                    item.setBranchId(Long.parseLong(ctx.getBranchId()));
                    item.setNursingIndentId(indent.getId());
                    item.setItemType(itemReq.itemType);
                    item.setItemCode(itemReq.itemCode);
                    item.setItemName(itemReq.itemName);
                    item.setQuantity(itemReq.quantity);
                    item.setUnit(itemReq.unit);
                    item.setReason(itemReq.reason);
                    item.setItemStatus(NursingIndentItem.ItemStatus.PENDING);
                    item.setCreatedBy(ctx.getCurrentUserId());
                    item.setUpdatedBy(ctx.getCurrentUserId());
                    return item;
                })
                .collect(Collectors.toList());

        items = itemRepository.saveAll(items);

        auditService.log(AuditLog.builder()
                .actorId(ctx.getCurrentUserId())
                .action("CREATE_INDENT")
                .entityType("NursingIndent")
                .entityId(indent.getId())
                .tenantId(ctx.getTenantId())
                .branchId(Long.parseLong(ctx.getBranchId()))
                .build());

        outboxPublisher.publish(new OutboxEvent(
                "indent.created",
                "NursingIndent",
                indent.getId(),
                ctx.getTenantId(),
                Long.parseLong(ctx.getBranchId())
        ));

        return new NursingIndentWithItems(indent, items);
    }

    public List<NursingIndentWithItems> getPendingIndents() {
        var ctx = tenantContext.current();
        var indents = indentRepository.findByTenantIdAndBranchIdAndIndentStatus(
                ctx.getTenantId(),
                Long.parseLong(ctx.getBranchId()),
                NursingIndent.IndentStatus.PENDING
        );
        return indents.stream()
                .map(indent -> {
                    var items = itemRepository.findByNursingIndentId(indent.getId());
                    return new NursingIndentWithItems(indent, items);
                })
                .collect(Collectors.toList());
    }

    public NursingIndentWithItems approveIndent(Long indentId) {
        var ctx = tenantContext.current();
        var indent = indentRepository.findById(indentId)
                .orElseThrow(() -> new ApiException("INDENT_NOT_FOUND", "Indent not found"));

        if (!indent.getTenantId().equals(ctx.getTenantId())) {
            throw new ApiException("FORBIDDEN", "Access denied");
        }

        if (indent.getIndentStatus() != NursingIndent.IndentStatus.PENDING) {
            throw new ApiException("INVALID_STATUS", "Indent is not in PENDING status");
        }

        indent.setIndentStatus(NursingIndent.IndentStatus.APPROVED);
        indent.setApprovedBy(ctx.getCurrentUserId());
        indent.setApprovedAt(LocalDateTime.now());
        indent.setUpdatedBy(ctx.getCurrentUserId());
        indent = indentRepository.save(indent);

        var items = itemRepository.findByNursingIndentId(indentId);
        items.forEach(item -> {
            item.setItemStatus(NursingIndentItem.ItemStatus.APPROVED);
            item.setUpdatedBy(ctx.getCurrentUserId());
        });
        itemRepository.saveAll(items);

        auditService.log(AuditLog.builder()
                .actorId(ctx.getCurrentUserId())
                .action("APPROVE_INDENT")
                .entityType("NursingIndent")
                .entityId(indent.getId())
                .tenantId(ctx.getTenantId())
                .branchId(Long.parseLong(ctx.getBranchId()))
                .build());

        outboxPublisher.publish(new OutboxEvent(
                "indent.approved",
                "NursingIndent",
                indent.getId(),
                ctx.getTenantId(),
                Long.parseLong(ctx.getBranchId())
        ));

        return new NursingIndentWithItems(indent, items);
    }

    public NursingIndentWithItems rejectIndent(Long indentId, String reason) {
        var ctx = tenantContext.current();
        var indent = indentRepository.findById(indentId)
                .orElseThrow(() -> new ApiException("INDENT_NOT_FOUND", "Indent not found"));

        if (!indent.getTenantId().equals(ctx.getTenantId())) {
            throw new ApiException("FORBIDDEN", "Access denied");
        }

        if (indent.getIndentStatus() != NursingIndent.IndentStatus.PENDING) {
            throw new ApiException("INVALID_STATUS", "Indent is not in PENDING status");
        }

        indent.setIndentStatus(NursingIndent.IndentStatus.REJECTED);
        indent.setRejectionReason(reason);
        indent.setUpdatedBy(ctx.getCurrentUserId());
        indent = indentRepository.save(indent);

        var items = itemRepository.findByNursingIndentId(indentId);
        items.forEach(item -> {
            item.setItemStatus(NursingIndentItem.ItemStatus.REJECTED);
            item.setRejectionReason(reason);
            item.setUpdatedBy(ctx.getCurrentUserId());
        });
        itemRepository.saveAll(items);

        auditService.log(AuditLog.builder()
                .actorId(ctx.getCurrentUserId())
                .action("REJECT_INDENT")
                .entityType("NursingIndent")
                .entityId(indent.getId())
                .tenantId(ctx.getTenantId())
                .branchId(Long.parseLong(ctx.getBranchId()))
                .build());

        outboxPublisher.publish(new OutboxEvent(
                "indent.rejected",
                "NursingIndent",
                indent.getId(),
                ctx.getTenantId(),
                Long.parseLong(ctx.getBranchId())
        ));

        return new NursingIndentWithItems(indent, items);
    }

    public NursingIndentWithItems markItemFulfilled(Long itemId) {
        var ctx = tenantContext.current();
        var item = itemRepository.findById(itemId)
                .orElseThrow(() -> new ApiException("ITEM_NOT_FOUND", "Item not found"));

        if (!item.getTenantId().equals(ctx.getTenantId())) {
            throw new ApiException("FORBIDDEN", "Access denied");
        }

        if (item.getItemStatus() != NursingIndentItem.ItemStatus.APPROVED) {
            throw new ApiException("INVALID_STATUS", "Item is not in APPROVED status");
        }

        item.setItemStatus(NursingIndentItem.ItemStatus.FULFILLED);
        item.setUpdatedBy(ctx.getCurrentUserId());
        itemRepository.save(item);

        var indent = indentRepository.findById(item.getNursingIndentId())
                .orElseThrow(() -> new ApiException("INDENT_NOT_FOUND", "Indent not found"));

        var allItems = itemRepository.findByNursingIndentId(indent.getId());
        boolean allFulfilled = allItems.stream()
                .allMatch(i -> i.getItemStatus() == NursingIndentItem.ItemStatus.FULFILLED);

        if (allFulfilled) {
            indent.setIndentStatus(NursingIndent.IndentStatus.FULFILLED);
            indentRepository.save(indent);
        }

        auditService.log(AuditLog.builder()
                .actorId(ctx.getCurrentUserId())
                .action("FULFILL_ITEM")
                .entityType("NursingIndentItem")
                .entityId(item.getId())
                .tenantId(ctx.getTenantId())
                .branchId(Long.parseLong(ctx.getBranchId()))
                .build());

        return new NursingIndentWithItems(indent, allItems);
    }

    private String generateIndentNumber() {
        var now = YearMonth.now();
        long nextSeq = 1; // In production, would use a sequence or database counter
        return String.format(INDENT_NUMBER_FORMAT, now.getYear() * 100 + now.getMonthValue(), nextSeq);
    }

    public static class CreateIndentRequest {
        public Long admissionId;
        public String wardSection;
        public String notes;
        public List<CreateItemRequest> items;
    }

    public static class CreateItemRequest {
        public NursingIndentItem.ItemType itemType;
        public String itemCode;
        public String itemName;
        public BigDecimal quantity;
        public String unit;
        public String reason;
    }

    public static class NursingIndentWithItems {
        public NursingIndent indent;
        public List<NursingIndentItem> items;

        public NursingIndentWithItems(NursingIndent indent, List<NursingIndentItem> items) {
            this.indent = indent;
            this.items = items;
        }
    }
}
