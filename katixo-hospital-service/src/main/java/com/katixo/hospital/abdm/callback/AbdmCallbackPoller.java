package com.katixo.hospital.abdm.callback;

import com.katixo.hospital.tenant.TenantContext;
import com.katixo.hospital.tenant.TenantRecord;
import com.katixo.hospital.tenant.TenantRegistryDao;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Drains the {@code abdm_callback} inbox and dispatches each pending callback to
 * the {@link AbdmCallbackHandler}. Mirrors {@code OutboxPublisherJob}: iterates
 * active tenants from the platform registry, binds a system {@link TenantContext}
 * per tenant (schema routing), processes each callback independently and
 * fail-soft (success → PROCESSED, error → retry, FAILED after 3 tries).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AbdmCallbackPoller {

    private final TenantRegistryDao tenantRegistryDao;
    private final AbdmCallbackRepository callbackRepository;
    private final AbdmCallbackHandler handler;

    @Value("${katixo.abdm.callback.poller.enabled:true}")
    private boolean enabled;

    @Value("${katixo.abdm.callback.poller.batch-size:50}")
    private int batchSize;

    @Scheduled(
            fixedDelayString = "${katixo.abdm.callback.poller.poll-interval-ms:5000}",
            initialDelayString = "${katixo.abdm.callback.poller.initial-delay-ms:20000}")
    public void drain() {
        if (!enabled) return;
        List<TenantRecord> tenants;
        try {
            tenants = tenantRegistryDao.findAll();
        } catch (Exception e) {
            log.warn("ABDM callback sweep skipped — could not read tenant registry: {}", e.getMessage());
            return;
        }
        for (TenantRecord tenant : tenants) {
            if (tenant.isActive()) drainTenant(tenant.tenantId());
        }
    }

    void drainTenant(String tenantId) {
        TenantContext.set(TenantContext.systemContext(tenantId));
        try {
            List<AbdmCallback> batch = callbackRepository.findByTenantIdAndStatusOrderByCreatedAtAsc(
                    tenantId, AbdmCallback.Status.PENDING, PageRequest.of(0, batchSize));
            for (AbdmCallback cb : batch) {
                try {
                    handler.handle(cb);
                    cb.markProcessed();
                } catch (Exception ex) {
                    cb.markFailed(ex.getMessage());
                    log.warn("ABDM callback {} (tenant {}, attempt {}) failed: {}",
                            cb.getId(), tenantId, cb.getRetryCount(), ex.getMessage());
                }
                callbackRepository.save(cb);
            }
        } catch (Exception e) {
            log.warn("ABDM callback sweep failed for tenant {}: {}", tenantId, e.getMessage());
        } finally {
            TenantContext.clear();
        }
    }
}
