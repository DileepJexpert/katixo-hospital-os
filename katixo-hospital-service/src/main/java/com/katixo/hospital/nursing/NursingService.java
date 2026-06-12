package com.katixo.hospital.nursing;

import com.katixo.hospital.audit.AuditLog;
import com.katixo.hospital.audit.AuditService;
import com.katixo.hospital.common.entity.BaseEntity;
import com.katixo.hospital.common.exception.BusinessException;
import com.katixo.hospital.outbox.OutboxEventService;
import com.katixo.hospital.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class NursingService {

    private final NursingIndentRepository indentRepository;
    private final NursingIndentItemRepository itemRepository;
    private final AuditService auditService;
    private final OutboxEventService outboxEventService;

    private static final String INDENT_NUMBER_FORMAT = "INDENT-%d-%05d";

    public NursingIndentWithItems createIndent(CreateIndentRequest request) {
        var ctx = TenantContext.get();
        var userId = Long.parseLong(ctx.getUserId());
        var indentNumber = generateIndentNumber();

        var indent = new NursingIndent();
        indent.setTenantId(ctx.getTenantId());
        indent.setHospitalGroupId(Long.parseLong(ctx.getHospitalGroupId()));
        indent.setBranchId(Long.parseLong(ctx.getBranchId()));
        indent.setIndentNumber(indentNumber);
        indent.setAdmissionId(request.admissionId);
        indent.setWardSection(request.wardSection);
        indent.setIndentStatus(NursingIndent.IndentStatus.PENDING);
        indent.setRequestedBy(userId);
        indent.setNotes(request.notes);
        indent.setCreatedBy(userId);
        indent.setUpdatedBy(userId);
        indent.setStatus(BaseEntity.EntityStatus.ACTIVE);

        indent = indentRepository.save(indent);
        final var savedIndent = indent;

        List<NursingIndentItem> items = request.items.stream()
                .map(itemReq -> {
                    var item = new NursingIndentItem();
                    item.setTenantId(ctx.getTenantId());
                    item.setHospitalGroupId(Long.parseLong(ctx.getHospitalGroupId()));
                    item.setBranchId(Long.parseLong(ctx.getBranchId()));
                    item.setNursingIndentId(savedIndent.getId());
                    item.setItemType(itemReq.itemType);
                    item.setItemCode(itemReq.itemCode);
                    item.setItemName(itemReq.itemName);
                    item.setQuantity(itemReq.quantity);
                    item.setUnit(itemReq.unit);
                    item.setReason(itemReq.reason);
                    item.setItemStatus(NursingIndentItem.ItemStatus.PENDING);
                    item.setCreatedBy(userId);
                    item.setUpdatedBy(userId);
                    item.setStatus(BaseEntity.EntityStatus.ACTIVE);
                    return item;
                })
                .collect(Collectors.toList());

        items = itemRepository.saveAll(items);

        auditService.audit("NursingIndent", String.valueOf(indent.getId()),
                AuditLog.AuditAction.CREATE, null,
                Map.of("indentNumber", indent.getIndentNumber(),
                        "indentStatus", indent.getIndentStatus().name(),
                        "itemCount", items.size()),
                UUID.randomUUID().toString());

        outboxEventService.publish("NursingIndent", String.valueOf(indent.getId()),
                "indent.created",
                Map.of("indentId", indent.getId(),
                        "indentNumber", indent.getIndentNumber(),
                        "indentStatus", indent.getIndentStatus().name()));

        return new NursingIndentWithItems(indent, items);
    }

    public List<NursingIndentWithItems> getPendingIndents() {
        var ctx = TenantContext.get();
        var indents = indentRepository.findByTenantIdAndBranchIdAndIndentStatus(
                ctx.getTenantId(),
                Long.parseLong(ctx.getBranchId()),
                NursingIndent.IndentStatus.PENDING
        );
        return indents.stream()
                .map(indent -> {
                    var items = itemRepository.findByTenantIdAndBranchIdAndNursingIndentId(
                            ctx.getTenantId(), Long.parseLong(ctx.getBranchId()), indent.getId());
                    return new NursingIndentWithItems(indent, items);
                })
                .collect(Collectors.toList());
    }

    public NursingIndentWithItems approveIndent(Long indentId) {
        var ctx = TenantContext.get();
        var userId = Long.parseLong(ctx.getUserId());
        var indent = indentRepository.findByIdAndTenantIdAndBranchId(
                        indentId, ctx.getTenantId(), Long.parseLong(ctx.getBranchId()))
                .orElseThrow(() -> new BusinessException("INDENT_NOT_FOUND", "Indent not found"));

        if (indent.getIndentStatus() != NursingIndent.IndentStatus.PENDING) {
            throw new BusinessException("INVALID_STATUS", "Indent is not in PENDING status");
        }

        var beforeStatus = indent.getIndentStatus().name();

        indent.setIndentStatus(NursingIndent.IndentStatus.APPROVED);
        indent.setApprovedBy(userId);
        indent.setApprovedAt(LocalDateTime.now());
        indent.setUpdatedBy(userId);
        indent = indentRepository.save(indent);

        var items = itemRepository.findByTenantIdAndBranchIdAndNursingIndentId(
                ctx.getTenantId(), Long.parseLong(ctx.getBranchId()), indentId);
        items.forEach(item -> {
            item.setItemStatus(NursingIndentItem.ItemStatus.APPROVED);
            item.setUpdatedBy(userId);
        });
        itemRepository.saveAll(items);

        auditService.audit("NursingIndent", String.valueOf(indent.getId()),
                AuditLog.AuditAction.UPDATE,
                Map.of("indentStatus", beforeStatus),
                Map.of("indentStatus", indent.getIndentStatus().name()),
                UUID.randomUUID().toString());

        outboxEventService.publish("NursingIndent", String.valueOf(indent.getId()),
                "indent.approved",
                Map.of("indentId", indent.getId(),
                        "indentNumber", indent.getIndentNumber(),
                        "indentStatus", indent.getIndentStatus().name()));

        return new NursingIndentWithItems(indent, items);
    }

    public NursingIndentWithItems rejectIndent(Long indentId, String reason) {
        var ctx = TenantContext.get();
        var userId = Long.parseLong(ctx.getUserId());
        var indent = indentRepository.findByIdAndTenantIdAndBranchId(
                        indentId, ctx.getTenantId(), Long.parseLong(ctx.getBranchId()))
                .orElseThrow(() -> new BusinessException("INDENT_NOT_FOUND", "Indent not found"));

        if (indent.getIndentStatus() != NursingIndent.IndentStatus.PENDING) {
            throw new BusinessException("INVALID_STATUS", "Indent is not in PENDING status");
        }

        var beforeStatus = indent.getIndentStatus().name();

        indent.setIndentStatus(NursingIndent.IndentStatus.REJECTED);
        indent.setRejectionReason(reason);
        indent.setUpdatedBy(userId);
        indent = indentRepository.save(indent);

        var items = itemRepository.findByTenantIdAndBranchIdAndNursingIndentId(
                ctx.getTenantId(), Long.parseLong(ctx.getBranchId()), indentId);
        items.forEach(item -> {
            item.setItemStatus(NursingIndentItem.ItemStatus.REJECTED);
            item.setRejectionReason(reason);
            item.setUpdatedBy(userId);
        });
        itemRepository.saveAll(items);

        auditService.audit("NursingIndent", String.valueOf(indent.getId()),
                AuditLog.AuditAction.UPDATE,
                Map.of("indentStatus", beforeStatus),
                Map.of("indentStatus", indent.getIndentStatus().name()),
                UUID.randomUUID().toString());

        outboxEventService.publish("NursingIndent", String.valueOf(indent.getId()),
                "indent.rejected",
                Map.of("indentId", indent.getId(),
                        "indentNumber", indent.getIndentNumber(),
                        "indentStatus", indent.getIndentStatus().name()));

        return new NursingIndentWithItems(indent, items);
    }

    public NursingIndentWithItems markItemFulfilled(Long itemId) {
        var ctx = TenantContext.get();
        var userId = Long.parseLong(ctx.getUserId());
        var item = itemRepository.findByIdAndTenantIdAndBranchId(
                        itemId, ctx.getTenantId(), Long.parseLong(ctx.getBranchId()))
                .orElseThrow(() -> new BusinessException("ITEM_NOT_FOUND", "Item not found"));

        if (item.getItemStatus() != NursingIndentItem.ItemStatus.APPROVED) {
            throw new BusinessException("INVALID_STATUS", "Item is not in APPROVED status");
        }

        var beforeStatus = item.getItemStatus().name();

        item.setItemStatus(NursingIndentItem.ItemStatus.FULFILLED);
        item.setUpdatedBy(userId);
        itemRepository.save(item);

        var indent = indentRepository.findByIdAndTenantIdAndBranchId(
                        item.getNursingIndentId(), ctx.getTenantId(), Long.parseLong(ctx.getBranchId()))
                .orElseThrow(() -> new BusinessException("INDENT_NOT_FOUND", "Indent not found"));

        var allItems = itemRepository.findByTenantIdAndBranchIdAndNursingIndentId(
                ctx.getTenantId(), Long.parseLong(ctx.getBranchId()), indent.getId());
        boolean allFulfilled = allItems.stream()
                .allMatch(i -> i.getItemStatus() == NursingIndentItem.ItemStatus.FULFILLED);

        if (allFulfilled) {
            indent.setIndentStatus(NursingIndent.IndentStatus.FULFILLED);
            indentRepository.save(indent);
        }

        auditService.audit("NursingIndentItem", String.valueOf(item.getId()),
                AuditLog.AuditAction.UPDATE,
                Map.of("itemStatus", beforeStatus),
                Map.of("itemStatus", item.getItemStatus().name()),
                UUID.randomUUID().toString());

        return new NursingIndentWithItems(indent, allItems);
    }

    private String generateIndentNumber() {
        var now = YearMonth.now();
        long nextSeq = indentRepository.nextIndentSequence();
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
