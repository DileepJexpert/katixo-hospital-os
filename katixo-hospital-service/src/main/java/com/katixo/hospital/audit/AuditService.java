package com.katixo.hospital.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.hash.Hashing;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static com.katixo.hospital.tenant.TenantContext.get;

@Service
@Slf4j
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    public void audit(String entityType, UUID entityId, AuditLog.AuditAction action,
                      Object beforeState, Object afterState, String correlationId) {
        try {
            var tenantContext = get();
            String beforeHash = beforeState != null ? hash(objectMapper.writeValueAsString(beforeState)) : null;
            String afterHash = afterState != null ? hash(objectMapper.writeValueAsString(afterState)) : null;
            String ipAddress = extractIpAddress();

            AuditLog log = AuditLog.builder()
                    .tenantId(tenantContext.getTenantId())
                    .branchId(tenantContext.getBranchId())
                    .actorId(tenantContext.getUserId())
                    .action(action)
                    .entityType(entityType)
                    .entityId(entityId)
                    .beforeHash(beforeHash)
                    .afterHash(afterHash)
                    .correlationId(correlationId)
                    .ipAddress(ipAddress)
                    .build();

            auditLogRepository.save(log);
        } catch (Exception e) {
            log.error("Failed to write audit log for entity {} - {}", entityType, entityId, e);
        }
    }

    private String hash(String data) {
        return Hashing.sha256()
                .hashString(data, StandardCharsets.UTF_8)
                .toString();
    }

    private String extractIpAddress() {
        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                String clientIp = attributes.getRequest().getHeader("X-Forwarded-For");
                if (clientIp == null || clientIp.isEmpty()) {
                    clientIp = attributes.getRequest().getRemoteAddr();
                }
                return clientIp;
            }
        } catch (Exception e) {
            log.debug("Could not extract IP address", e);
        }
        return "unknown";
    }
}
