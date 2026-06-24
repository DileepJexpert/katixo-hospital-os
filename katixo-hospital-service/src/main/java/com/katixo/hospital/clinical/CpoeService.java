package com.katixo.hospital.clinical;

import com.katixo.hospital.audit.AuditLog;
import com.katixo.hospital.audit.AuditService;
import com.katixo.hospital.clinical.cds.CdsAlert;
import com.katixo.hospital.clinical.cds.CdsRule;
import com.katixo.hospital.clinical.cds.CdsService;
import com.katixo.hospital.common.entity.BaseEntity;
import com.katixo.hospital.common.exception.BusinessException;
import com.katixo.hospital.patient.Patient;
import com.katixo.hospital.patient.PatientRepository;
import com.katixo.hospital.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * CPOE — computerized order entry. Places a unified {@link ClinicalOrder} on an
 * encounter after running {@link CdsService}; CRITICAL alerts block placement
 * unless an override reason is supplied (captured + audited). Routing the order
 * to the department service (Lab/Radiology/Pharmacy) is the next slice — the
 * unified record + CDS gate land first.
 */
@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class CpoeService {

    private final ClinicalOrderRepository orderRepository;
    private final ClinicalService clinicalService;
    private final PatientRepository patientRepository;
    private final CdsService cdsService;
    private final AuditService auditService;
    private final ClinicalOrderRouter orderRouter;

    public record PlaceResult(ClinicalOrder order, List<CdsAlert> alerts) {}

    public PlaceResult placeOrder(Long encounterId, ClinicalOrder.OrderType orderType, String code, String name,
                                  ClinicalOrder.Priority priority, String instructions, String overrideReason) {
        if (orderType == null || name == null || name.isBlank()) {
            throw new BusinessException("ORDER_INVALID", "orderType and name are required");
        }
        Encounter enc = clinicalService.getEncounter(encounterId);
        if (enc.getEncounterStatus() == Encounter.EncounterStatus.CLOSED) {
            throw new BusinessException("ENCOUNTER_CLOSED", "Cannot place orders on a closed encounter");
        }
        var ctx = TenantContext.get();
        Patient patient = patientRepository.findByIdAndTenantIdAndBranchId(
                enc.getPatientId(), ctx.getTenantId(), branchId()).orElse(null);

        ClinicalOrder proposed = new ClinicalOrder();
        proposed.setEncounterId(encounterId);
        proposed.setPatientId(enc.getPatientId());
        proposed.setOrderType(orderType);
        proposed.setCode(code);
        proposed.setName(name.trim());
        proposed.setPriority(priority == null ? ClinicalOrder.Priority.ROUTINE : priority);
        proposed.setInstructions(instructions);

        List<ClinicalOrder> existing = orderRepository.findByTenantIdAndBranchIdAndEncounterIdOrderByIdDesc(
                ctx.getTenantId(), branchId(), encounterId);
        List<CdsAlert> alerts = cdsService.evaluate(new CdsRule.Context(patient, proposed, existing));

        boolean overridden = overrideReason != null && !overrideReason.isBlank();
        if (cdsService.hasBlocking(alerts) && !overridden) {
            String msg = alerts.stream()
                    .filter(a -> a.severity() == CdsAlert.Severity.CRITICAL)
                    .map(CdsAlert::message)
                    .collect(Collectors.joining("; "));
            throw new BusinessException("CDS_BLOCKED", msg + " Supply an override reason to proceed.");
        }
        if (overridden) {
            proposed.setCdsOverrideReason(overrideReason);
        }
        proposed.setOrderStatus(ClinicalOrder.OrderStatus.PLACED);
        proposed.setPlacedByDoctorId(userId());
        stamp(proposed);
        ClinicalOrder saved = orderRepository.save(proposed);

        // Route to the executing department (Lab/Radiology) — best-effort + isolated:
        // a routing failure (own tx) never breaks the CPOE placement; on success the
        // unified order back-links to the real department order.
        try {
            ClinicalOrderRouter.Ref ref = orderRouter.route(saved, enc);
            if (ref != null) {
                saved.setLinkedRefType(ref.type());
                saved.setLinkedRefId(ref.id());
                saved = orderRepository.save(saved);
                log.info("CPOE order {} routed to {} {}", saved.getId(), ref.type(), ref.id());
            }
        } catch (RuntimeException ex) {
            log.warn("CPOE order {} not routed ({} {}): {}", saved.getId(), orderType, saved.getName(), ex.getMessage());
        }

        auditService.audit("ClinicalOrder", String.valueOf(saved.getId()), AuditLog.AuditAction.CREATE,
                null, Map.of("encounterId", encounterId, "type", orderType.name(), "name", saved.getName(),
                        "overridden", overridden), UUID.randomUUID().toString());
        log.info("CPOE order {} placed: {} {} on encounter {} (alerts={}, overridden={})",
                saved.getId(), orderType, saved.getName(), encounterId, alerts.size(), overridden);
        return new PlaceResult(saved, alerts);
    }

    public ClinicalOrder updateStatus(Long orderId, ClinicalOrder.OrderStatus status) {
        var ctx = TenantContext.get();
        ClinicalOrder o = orderRepository.findByIdAndTenantIdAndBranchId(orderId, ctx.getTenantId(), branchId())
                .orElseThrow(() -> new BusinessException("ORDER_NOT_FOUND", "Order not found: " + orderId));
        o.setOrderStatus(status);
        o.setUpdatedBy(userId());
        return orderRepository.save(o);
    }

    @Transactional(readOnly = true)
    public List<ClinicalOrder> listOrders(Long encounterId) {
        var ctx = TenantContext.get();
        return orderRepository.findByTenantIdAndBranchIdAndEncounterIdOrderByIdDesc(
                ctx.getTenantId(), branchId(), encounterId);
    }

    private void stamp(BaseEntity entity) {
        var ctx = TenantContext.get();
        entity.setTenantId(ctx.getTenantId());
        entity.setHospitalGroupId(Long.parseLong(ctx.getHospitalGroupId()));
        entity.setBranchId(branchId());
        entity.setCreatedBy(userId());
        entity.setUpdatedBy(userId());
        entity.setStatus(BaseEntity.EntityStatus.ACTIVE);
    }

    private Long branchId() {
        return Long.parseLong(TenantContext.get().getBranchId());
    }

    private Long userId() {
        return Long.parseLong(TenantContext.get().getUserId());
    }
}
