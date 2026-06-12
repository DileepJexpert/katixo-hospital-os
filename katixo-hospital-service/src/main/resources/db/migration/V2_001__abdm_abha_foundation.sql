-- ============================================================
-- ABDM / ABHA Foundation (Sprint 2)
-- V2_001__abdm_abha_foundation.sql
--
-- Links patients to their ABHA (Ayushman Bharat Health Account) so the hospital
-- can act as a Health Information Provider on the ABDM network. From 2026–2027
-- AB-PMJAY empanelment is being tied to ABDM certification, so ABHA linkage is
-- the foundation for consent-based health-record exchange and faster insurer
-- settlement.
-- ============================================================

SET search_path = hospital;

CREATE TABLE abha_link (
    id                  BIGSERIAL PRIMARY KEY,
    tenant_id           VARCHAR(50)  NOT NULL,
    hospital_group_id   BIGINT       NOT NULL,
    branch_id           BIGINT       NOT NULL,
    patient_id          BIGINT       NOT NULL REFERENCES patient(id) ON DELETE CASCADE,

    abha_number         VARCHAR(14)  NOT NULL,            -- canonical 14 digits, no hyphens
    abha_address        VARCHAR(80),                      -- name@suffix handle (optional)

    link_status         VARCHAR(20)  NOT NULL DEFAULT 'LINKED',  -- LINKED, UNLINKED
    verification_method VARCHAR(20)  NOT NULL,            -- AADHAAR_OTP, MOBILE_OTP, DEMOGRAPHICS
    linked_at           TIMESTAMP    NOT NULL,
    unlinked_at         TIMESTAMP,

    status              VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_by          BIGINT       NOT NULL,
    created_at          TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_by          BIGINT       NOT NULL,
    updated_at          TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_abha_patient ON abha_link(tenant_id, branch_id, patient_id);
CREATE INDEX idx_abha_number ON abha_link(abha_number);

-- The same health account must not be linked twice within a tenant.
ALTER TABLE abha_link
    ADD CONSTRAINT uq_abha_number_tenant UNIQUE (tenant_id, abha_number);

COMMENT ON TABLE abha_link IS
'Patient-to-ABHA linkage (hospital as Health Information Provider). One active
(LINKED) row per patient; abha_number is unique per tenant. Mutations are audited
and emit outbox events for ABDM gateway registration.';

-- ============================================================
-- ABDM policy seeds (disabled by default; each hospital opts in)
-- ============================================================

INSERT INTO hospital_policy (
    tenant_id, hospital_group_id, branch_id, policy_code, policy_value,
    description, version, effective_from, created_by, updated_by)
SELECT DISTINCT hp.tenant_id, hp.hospital_group_id, hp.branch_id, 'abdm.enabled', 'false',
       'Enable ABDM/ABHA features for this hospital', 1, NOW(), 0, 0
FROM hospital_policy hp
WHERE NOT EXISTS (
    SELECT 1 FROM hospital_policy ex
    WHERE ex.tenant_id = hp.tenant_id
      AND ex.branch_id IS NOT DISTINCT FROM hp.branch_id
      AND ex.policy_code = 'abdm.enabled'
);
