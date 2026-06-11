package com.katixo.hospital.nursing;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/nursing")
@RequiredArgsConstructor
@Slf4j
public class NursingController {

    private final NursingService nursingService;

    @PostMapping("/indents")
    @PreAuthorize("hasAnyRole('NURSE', 'ADMIN')")
    public ResponseEntity<IndentResponse> createIndent(@RequestBody NursingService.CreateIndentRequest request) {
        log.info("Creating nursing indent with {} items", request.items.size());
        var result = nursingService.createIndent(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(toIndentResponse(result));
    }

    @GetMapping("/indents/pending")
    @PreAuthorize("hasAnyRole('ADMIN', 'NURSE_SUPERVISOR', 'DOCTOR')")
    public ResponseEntity<List<IndentResponse>> getPendingIndents() {
        log.info("Fetching pending nursing indents");
        var indents = nursingService.getPendingIndents();
        return ResponseEntity.ok(indents.stream()
                .map(this::toIndentResponse)
                .collect(Collectors.toList()));
    }

    @PostMapping("/indents/{indentId}/approve")
    @PreAuthorize("hasAnyRole('ADMIN', 'NURSE_SUPERVISOR')")
    public ResponseEntity<IndentResponse> approveIndent(@PathVariable Long indentId) {
        log.info("Approving nursing indent {}", indentId);
        var result = nursingService.approveIndent(indentId);
        return ResponseEntity.ok(toIndentResponse(result));
    }

    @PostMapping("/indents/{indentId}/reject")
    @PreAuthorize("hasAnyRole('ADMIN', 'NURSE_SUPERVISOR')")
    public ResponseEntity<IndentResponse> rejectIndent(
            @PathVariable Long indentId,
            @RequestBody RejectRequest request) {
        log.info("Rejecting nursing indent {}", indentId);
        var result = nursingService.rejectIndent(indentId, request.reason);
        return ResponseEntity.ok(toIndentResponse(result));
    }

    @PostMapping("/indent-items/{itemId}/fulfill")
    @PreAuthorize("hasAnyRole('NURSE', 'ADMIN')")
    public ResponseEntity<IndentResponse> markItemFulfilled(@PathVariable Long itemId) {
        log.info("Marking indent item {} as fulfilled", itemId);
        var result = nursingService.markItemFulfilled(itemId);
        return ResponseEntity.ok(toIndentResponse(result));
    }

    private IndentResponse toIndentResponse(NursingService.NursingIndentWithItems result) {
        return new IndentResponse(
                result.indent.getId(),
                result.indent.getIndentNumber(),
                result.indent.getAdmissionId(),
                result.indent.getWardSection(),
                result.indent.getIndentStatus().name(),
                result.indent.getRequestedBy(),
                result.indent.getApprovedBy(),
                result.indent.getNotes(),
                result.items.stream()
                        .map(item -> new ItemResponse(
                                item.getId(),
                                item.getItemType().name(),
                                item.getItemName(),
                                item.getQuantity(),
                                item.getUnit(),
                                item.getReason(),
                                item.getItemStatus().name()
                        ))
                        .collect(Collectors.toList())
        );
    }

    public static class IndentResponse {
        public Long id;
        public String indentNumber;
        public Long admissionId;
        public String wardSection;
        public String indentStatus;
        public Long requestedBy;
        public Long approvedBy;
        public String notes;
        public List<ItemResponse> items;

        public IndentResponse(Long id, String indentNumber, Long admissionId, String wardSection,
                             String indentStatus, Long requestedBy, Long approvedBy, String notes,
                             List<ItemResponse> items) {
            this.id = id;
            this.indentNumber = indentNumber;
            this.admissionId = admissionId;
            this.wardSection = wardSection;
            this.indentStatus = indentStatus;
            this.requestedBy = requestedBy;
            this.approvedBy = approvedBy;
            this.notes = notes;
            this.items = items;
        }
    }

    public static class ItemResponse {
        public Long id;
        public String itemType;
        public String itemName;
        public java.math.BigDecimal quantity;
        public String unit;
        public String reason;
        public String itemStatus;

        public ItemResponse(Long id, String itemType, String itemName,
                           java.math.BigDecimal quantity, String unit, String reason, String itemStatus) {
            this.id = id;
            this.itemType = itemType;
            this.itemName = itemName;
            this.quantity = quantity;
            this.unit = unit;
            this.reason = reason;
            this.itemStatus = itemStatus;
        }
    }

    public static class RejectRequest {
        public String reason;
    }
}
