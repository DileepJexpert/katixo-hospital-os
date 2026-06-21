package com.katixo.hospital.abdm.terminology;

import com.katixo.hospital.common.entity.BaseEntity;
import com.katixo.hospital.common.exception.BusinessException;
import com.katixo.hospital.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Lookup + maintenance of the clinical terminology map (SNOMED CT / LOINC). Used
 * by the FHIR bundle assembler (once HAPI FHIR is wired) to code free-text
 * diagnoses/tests/medicines; usable now to build the map.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class TerminologyService {

    private final ClinicalCodeRepository repository;

    /** Exact (case-insensitive) lookup of a local term within a category. */
    @Transactional(readOnly = true)
    public Optional<ClinicalCode> lookup(String category, String localTerm) {
        if (category == null || localTerm == null || localTerm.isBlank()) return Optional.empty();
        return repository.findByTenantIdAndCategoryAndLocalTerm(
                TenantContext.get().getTenantId(), category.toUpperCase(), localTerm.trim().toLowerCase());
    }

    @Transactional(readOnly = true)
    public List<ClinicalCode> search(String category, String q, int limit) {
        String like = (q == null || q.isBlank()) ? null : "%" + q.trim().toLowerCase() + "%";
        return repository.search(TenantContext.get().getTenantId(),
                category == null ? "DIAGNOSIS" : category.toUpperCase(), like,
                PageRequest.of(0, Math.max(1, Math.min(limit, 200))));
    }

    /** Upsert a mapping (admin/setup). */
    public ClinicalCode upsert(String category, String codeSystem, String code, String display, String localTerm) {
        if (code == null || code.isBlank() || localTerm == null || localTerm.isBlank()) {
            throw new BusinessException("TERMINOLOGY_INVALID", "code and localTerm are required");
        }
        var ctx = TenantContext.get();
        String cat = category == null ? "DIAGNOSIS" : category.toUpperCase();
        String key = localTerm.trim().toLowerCase();
        ClinicalCode c = repository.findByTenantIdAndCategoryAndLocalTerm(ctx.getTenantId(), cat, key)
                .orElseGet(ClinicalCode::new);
        boolean isNew = c.getId() == null;
        c.setCategory(cat);
        c.setCodeSystem(codeSystem == null ? "SNOMED_CT" : codeSystem.toUpperCase());
        c.setCode(code.trim());
        c.setDisplay(display == null ? code.trim() : display.trim());
        c.setLocalTerm(key);
        Long userId = Long.parseLong(ctx.getUserId());
        LocalDateTime now = LocalDateTime.now();
        if (isNew) {
            c.setTenantId(ctx.getTenantId());
            c.setHospitalGroupId(Long.parseLong(ctx.getHospitalGroupId()));
            c.setBranchId(Long.parseLong(ctx.getBranchId()));
            c.setCreatedBy(userId);
            c.setCreatedAt(now);
            c.setStatus(BaseEntity.EntityStatus.ACTIVE);
        }
        c.setUpdatedBy(userId);
        c.setUpdatedAt(now);
        return repository.save(c);
    }
}
