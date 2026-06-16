package com.katixo.hospital.realtime;

import com.katixo.hospital.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Fan-out hub services call when a real-time board's data changes. Resolves the
 * current tenant:branch and pushes a topic nudge to its WebSocket sessions —
 * <strong>after commit</strong> when a transaction is active, so clients never
 * re-fetch stale (pre-commit) data. Always fail-soft: a broadcast hiccup must
 * never break the clinical/financial flow that triggered it.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BoardBroadcaster {

    private final BoardWebSocketHandler handler;

    /** OPD queue board changed (token issued / called / consultation started or completed). */
    public void queueChanged() {
        nudge("queue");
    }

    /** Bed board changed (admit / transfer / discharge). */
    public void bedsChanged() {
        nudge("beds");
    }

    /** Pharmacy dispense queue changed (enqueued / started / completed / rejected / re-prioritised). */
    public void pharmacyChanged() {
        nudge("pharmacy");
    }

    private void nudge(String topic) {
        try {
            TenantContext ctx = TenantContext.getOrNull();
            if (ctx == null) {
                return;
            }
            final String key = ctx.getTenantId() + ":" + ctx.getBranchId();
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        handler.broadcast(key, topic);
                    }
                });
            } else {
                handler.broadcast(key, topic);
            }
        } catch (RuntimeException e) {
            log.debug("Board nudge '{}' skipped: {}", topic, e.getMessage());
        }
    }
}
