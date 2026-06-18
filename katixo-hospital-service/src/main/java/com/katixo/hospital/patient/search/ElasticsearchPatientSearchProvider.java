package com.katixo.hospital.patient.search;

import com.katixo.hospital.patient.Patient;
import com.katixo.hospital.patient.PatientRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.query.Criteria;
import org.springframework.data.elasticsearch.core.query.CriteriaQuery;
import org.springframework.data.elasticsearch.core.query.Query;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Elasticsearch-backed patient search — fuzzy/typo-tolerant matching for larger
 * deployments. Active only when {@code katixo.search.elasticsearch.enabled=true};
 * otherwise {@link DbPatientSearchProvider} is used and no ES connection is made.
 *
 * <p>ES holds only a light projection ({@link PatientSearchDocument}); matched ids
 * are re-loaded from the SQL master (tenant + branch filtered) so the patient
 * records returned are always authoritative. Every operation is fail-soft — an
 * unreachable cluster degrades search to empty results and never blocks
 * registration or updates.
 *
 * <p>NOTE: requires a provisioned ES/OpenSearch cluster and a runtime smoke test
 * before enabling — it cannot be exercised in the build environment.
 */
@Component
@ConditionalOnProperty(name = "katixo.search.elasticsearch.enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class ElasticsearchPatientSearchProvider implements PatientSearchProvider {

    private final ElasticsearchOperations operations;
    private final PatientRepository patientRepository;

    @Override
    public List<Patient> search(String tenantId, Long branchId, String query) {
        try {
            Criteria criteria = new Criteria("tenantId").is(tenantId)
                    .and(new Criteria("branchId").is(branchId))
                    .subCriteria(new Criteria("fullName").contains(query)
                            .or(new Criteria("mobile").contains(query))
                            .or(new Criteria("uhid").contains(query)));
            Query q = new CriteriaQuery(criteria);
            SearchHits<PatientSearchDocument> hits = operations.search(q, PatientSearchDocument.class);

            List<Long> ids = hits.stream().map(h -> h.getContent().getPatientId()).toList();
            if (ids.isEmpty()) {
                return List.of();
            }
            // Re-load authoritative records from SQL, tenant/branch-scoped, preserving hit order.
            Map<Long, Patient> byId = new LinkedHashMap<>();
            for (Patient p : patientRepository.findAllById(ids)) {
                if (tenantId.equals(p.getTenantId()) && branchId.equals(p.getBranchId())) {
                    byId.put(p.getId(), p);
                }
            }
            return ids.stream().map(byId::get).filter(java.util.Objects::nonNull).toList();
        } catch (Exception e) {
            log.warn("Elasticsearch patient search failed for '{}': {}", query, e.getMessage());
            return List.of();
        }
    }

    @Override
    public void index(Patient patient) {
        try {
            PatientSearchDocument doc = new PatientSearchDocument();
            doc.setId(PatientSearchDocument.idOf(patient.getTenantId(), patient.getId()));
            doc.setTenantId(patient.getTenantId());
            doc.setBranchId(patient.getBranchId());
            doc.setPatientId(patient.getId());
            doc.setFullName(patient.getFullName());
            doc.setMobile(patient.getMobile());
            doc.setUhid(patient.getUhid());
            doc.setEmail(patient.getEmail());
            operations.save(doc);
        } catch (Exception e) {
            log.warn("Elasticsearch index failed for patient {}: {}", patient.getId(), e.getMessage());
        }
    }

    @Override
    public void remove(String tenantId, Long patientId) {
        try {
            operations.delete(PatientSearchDocument.idOf(tenantId, patientId), PatientSearchDocument.class);
        } catch (Exception e) {
            log.warn("Elasticsearch remove failed for patient {}: {}", patientId, e.getMessage());
        }
    }
}
