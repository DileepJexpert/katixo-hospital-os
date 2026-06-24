package com.katixo.hospital.clinical.cds;

import com.katixo.hospital.clinical.ClinicalOrder;
import org.springframework.stereotype.Component;

import java.util.List;

/** Warns when an equivalent order (same type + code/name) is already active on the encounter. */
@Component
public class DuplicateOrderCdsRule implements CdsRule {

    @Override
    public List<CdsAlert> evaluate(Context ctx) {
        ClinicalOrder p = ctx.proposed();
        if (p == null) return List.of();
        boolean dup = ctx.existingOrders().stream()
                .filter(o -> o.getOrderStatus() != ClinicalOrder.OrderStatus.CANCELLED)
                .anyMatch(o -> o.getOrderType() == p.getOrderType() && sameItem(o, p));
        if (dup) {
            return List.of(CdsAlert.warning("DUPLICATE_ORDER",
                    "A similar " + p.getOrderType() + " order (" + p.getName() + ") is already active on this encounter."));
        }
        return List.of();
    }

    private boolean sameItem(ClinicalOrder a, ClinicalOrder b) {
        if (a.getCode() != null && b.getCode() != null) {
            return a.getCode().equalsIgnoreCase(b.getCode());
        }
        return a.getName() != null && a.getName().equalsIgnoreCase(b.getName());
    }
}
