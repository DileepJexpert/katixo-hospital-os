-- ============================================================
-- Nursing Vitals Module Schema
-- V1_014__nursing_vitals_schema.sql
-- ============================================================

SET search_path = hospital;

-- ============================================================
-- NURSING VITAL SIGN RECORD
-- ============================================================

CREATE TABLE nursing_vital (
    id                  BIGSERIAL PRIMARY KEY,
    tenant_id           VARCHAR(50)  NOT NULL,
    hospital_group_id   BIGINT       NOT NULL,
    branch_id           BIGINT       NOT NULL,

    admission_id        BIGINT       NOT NULL,
    patient_id          BIGINT       NOT NULL,
    recorded_by         BIGINT       NOT NULL,   -- staff_id (nurse)

    temperature_celsius NUMERIC(5,2),            -- 36-40°C normal
    heart_rate_bpm      INT,                     -- 60-100 normal
    respiratory_rate    INT,                     -- 12-20 normal
    systolic_bp         INT,                     -- mmHg
    diastolic_bp        INT,                     -- mmHg
    spo2_percent        NUMERIC(5,2),            -- 95-100 normal
    blood_glucose       NUMERIC(7,2),            -- optional, for diabetics

    -- Clinical notes on round
    observations        TEXT,
    complaints          TEXT,
    pain_level          INT,                     -- 0-10 scale
    nutrition_status    VARCHAR(50),             -- Good, Fair, Poor

    -- Alert flags for abnormal readings
    is_abnormal         BOOLEAN DEFAULT false,
    abnormality_notes   TEXT,

    round_status        VARCHAR(20) NOT NULL DEFAULT 'RECORDED',
                        -- RECORDED, REVIEWED, FLAGGED
    reviewed_by         BIGINT,
    reviewed_at         TIMESTAMP,

    status              VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_by          BIGINT,
    created_at          TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_by          BIGINT,
    updated_at          TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_nursing_vital_tenant_branch ON nursing_vital(tenant_id, branch_id);
CREATE INDEX idx_nursing_vital_admission ON nursing_vital(admission_id);
CREATE INDEX idx_nursing_vital_patient ON nursing_vital(patient_id);
CREATE INDEX idx_nursing_vital_recorded_at ON nursing_vital(created_at);
CREATE INDEX idx_nursing_vital_abnormal ON nursing_vital(is_abnormal, round_status);
