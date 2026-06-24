package com.katixo.hospital.clinical;

import com.katixo.hospital.common.event.DepartmentOrderStatusEvent;
import com.katixo.hospital.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Reverse status-sync: when a department order (Lab/Radiology) reaches a terminal
 * status, update the CPOE {@link ClinicalOrder} that routed to it, so the EMR chart
 * reflects execution. Listens for {@link DepartmentOrderStatusEvent} synchronously
 * within the publisher's transaction (same tenant schema, fail-soft).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ClinicalOrderStatusListener {

    private final ClinicalOrderRepository orderRepository;

    @EventListener
    public void onDepartmentStatus(DepartmentOrderStatusEvent e) {
        ClinicalOrder.OrderStatus status = map(e.status());
        if (status == null) {
            return;
        }
        try {
            var orders = orderRepository.findByTenantIdAndLinkedRefTypeAndLinkedRefId(
                    e.tenantId(), e.refType(), e.refId());
            for (ClinicalOrder o : orders) {
                if (o.getOrderStatus() == status) continue;
                o.setOrderStatus(status);
                o.setUpdatedAt(LocalDateTime.now());
                TenantContext ctx = TenantContext.getOrNull();
                if (ctx != null && ctx.getUserId() != null) {
                    o.setUpdatedBy(Long.parseLong(ctx.getUserId()));
                }
                orderRepository.save(o);
                log.info("CPOE order {} synced to {} from {} {}", o.getId(), status, e.refType(), e.refId());
            }
        } catch (Exception ex) {
            log.warn("CPOE status sync failed for {} {}: {}", e.refType(), e.refId(), ex.getMessage());
        }
    }

    private ClinicalOrder.OrderStatus map(String s) {
        if (s == null) return null;
        return switch (s) {
            case "COMPLETED" -> ClinicalOrder.OrderStatus.COMPLETED;
            case "IN_PROGRESS" -> ClinicalOrder.OrderStatus.IN_PROGRESS;
            case "CANCELLED" -> ClinicalOrder.OrderStatus.CANCELLED;
            default -> null;
        };
    }
}
