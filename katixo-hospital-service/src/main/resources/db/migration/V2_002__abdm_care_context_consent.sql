-- ============================================================
-- ABDM Care Context + Consent Artifact (Sprint 2)
-- V2_002__abdm_care_context_consent.sql
--
-- care_context     — one row per care episode (OPD visit / IPD admission) attached
--                    to the patient's ABHA; registered with the ABDM gateway
--                    asynchronously via outbox (link_status tracks registration).
-- consent_artifact — stored HIE-CM consent grants. Data may move over the ABDM
--                    network ONLY while a matching artifact is GRANTED and
--                    unexpired. Never deleted: revocation flips status so the
--                    consent trail stays auditable.
-- ============================================================

SET search_path = hospital;

-- ============================================================
-- CARE CONTEXT
-- ============================================================

CREATE TABLE care_context (
    id                     BIGSERIAL PRIMARY KEY,
    tenant_id              VARCHAR(50)  NOT NULL,
    hospital_group_id      BIGINT       NOT NULL,
    branch_id              BIGINT       NOT NULL,
    patient_id             BIGINT       NOT NULL REFERENCES patient(id) ON DELETE CASCADE,
    abha_link_id           BIGINT       NOT NULL REFERENCES abha_link(id),

    source_type            VARCHAR(20)  NOT NULL,   -- OPD_VISIT, IPD_ADMISSION
    source_id              BIGINT       NOT NULL,
    care_context_reference VARCHAR(50)  NOT NULL,   -- e.g. OPD-123, IPD-45
    display_name           VARCHAR(200) NOT NULL,   -- shown in the patient's PHR app
    link_status            VARCHAR(20)  NOT NULL DEFAULT 'PENDING_LINK',  -- PENDING_LINK, LINKED, FAILED

    status                 VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_by             BIGINT       NOT NULL,
    created_at             TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_by             BIGINT       NOT NULL,
    updated_at             TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_care_ctx_patient ON care_context(tenant_id, branch_id, patient_id);
CREATE INDEX idx_care_ctx_source ON care_context(tenant_id, source_type, source_id);

ALTER TABLE care_context
    ADD CONSTRAINT uq_care_ctx_reference UNIQUE (tenant_id, care_context_reference);

COMMENT ON TABLE care_context IS
'ABDM care context per care episode, attached to the patient''s ABHA link.
Gateway registration is asynchronous (outbox -> integration-service);
link_status is updated from the gateway callback.';

-- ============================================================
-- CONSENT ARTIFACT
-- ============================================================

CREATE TABLE consent_artifact (
    id                  BIGSERIAL PRIMARY KEY,
    tenant_id           VARCHAR(50)  NOT NULL,
    hospital_group_id   BIGINT       NOT NULL,
    branch_id           BIGINT       NOT NULL,
    patient_id          BIGINT       NOT NULL REFERENCES patient(id) ON DELETE CASCADE,
    abha_link_id        BIGINT       NOT NULL REFERENCES abha_link(id),

    artifact_id         VARCHAR(64)  NOT NULL,   -- HIE-CM id (locally generated until gateway integration)
    purpose_code        VARCHAR(20)  NOT NULL,   -- e.g. CAREMGT, BTG
    hi_types            VARCHAR(300) NOT NULL,   -- CSV: Prescription,DiagnosticReport,...
    data_from           TIMESTAMP    NOT NULL,   -- data period covered
    data_to             TIMESTAMP    NOT NULL,
    expires_at          TIMESTAMP    NOT NULL,   -- when the consent itself lapses
    consent_status      VARCHAR(20)  NOT NULL DEFAULT 'GRANTED',  -- GRANTED, REVOKED, EXPIRED
    granted_at          TIMESTAMP    NOT NULL,
    revoked_at          TIMESTAMP,

    status              VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_by          BIGINT       NOT NULL,
    created_at          TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_by          BIGINT       NOT NULL,
    updated_at          TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_consent_patient ON consent_artifact(tenant_id, branch_id, patient_id);
CREATE INDEX idx_consent_status ON consent_artifact(tenant_id, consent_status);

ALTER TABLE consent_artifact
    ADD CONSTRAINT uq_consent_artifact_id UNIQUE (tenant_id, artifact_id);

COMMENT ON TABLE consent_artifact IS
'Stored ABDM consent grants. Record transfer is gated on an artifact being
GRANTED, unexpired, and covering the requested HI type. Never hard-deleted.';
