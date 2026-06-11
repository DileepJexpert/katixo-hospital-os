-- Prescription Dispense Tracking
CREATE TABLE prescription_dispense (
    id BIGSERIAL PRIMARY KEY,
    tenant_id VARCHAR(50) NOT NULL,
    hospital_group_id BIGINT NOT NULL,
    branch_id BIGINT NOT NULL,
    prescription_id BIGINT NOT NULL,
    patient_id BIGINT NOT NULL,
    visit_id BIGINT NOT NULL,
    dispense_status VARCHAR(30) NOT NULL DEFAULT 'QUEUED',
    dispensed_at TIMESTAMP,
    total_items INTEGER NOT NULL,
    status VARCHAR(20) DEFAULT 'ACTIVE',
    created_by BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_prescription_dispense_tenant_branch ON prescription_dispense(tenant_id, branch_id);
CREATE INDEX idx_prescription_dispense_rx ON prescription_dispense(prescription_id);
CREATE INDEX idx_prescription_dispense_patient ON prescription_dispense(patient_id);
CREATE INDEX idx_prescription_dispense_status ON prescription_dispense(dispense_status);

-- Pharmacy Queue Items (FIFO with priority override)
CREATE TABLE pharmacy_queue_item (
    id BIGSERIAL PRIMARY KEY,
    tenant_id VARCHAR(50) NOT NULL,
    hospital_group_id BIGINT NOT NULL,
    branch_id BIGINT NOT NULL,
    dispense_id BIGINT NOT NULL REFERENCES prescription_dispense(id),
    prescription_id BIGINT NOT NULL,
    patient_id BIGINT NOT NULL,
    medicine_code VARCHAR(50) NOT NULL,
    medicine_name VARCHAR(200) NOT NULL,
    quantity INTEGER NOT NULL,
    dosage VARCHAR(100),
    frequency VARCHAR(100),
    queue_status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    priority INTEGER NOT NULL DEFAULT 0,
    original_priority INTEGER,
    priority_override_at TIMESTAMP,
    priority_override_by BIGINT,
    priority_override_reason TEXT,
    dispensed_at TIMESTAMP,
    dispensed_by BIGINT,
    status VARCHAR(20) DEFAULT 'ACTIVE',
    created_by BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_pharmacy_queue_item_tenant_branch ON pharmacy_queue_item(tenant_id, branch_id);
CREATE INDEX idx_pharmacy_queue_item_status ON pharmacy_queue_item(queue_status, priority, created_at);
CREATE INDEX idx_pharmacy_queue_item_dispense ON pharmacy_queue_item(dispense_id);
CREATE INDEX idx_pharmacy_queue_item_patient ON pharmacy_queue_item(patient_id);
