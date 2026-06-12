package com.katixo.hospital.policy;

public enum HospitalPolicyCode {
    // OPD Policies
    OPD_FOLLOW_UP_FREE_DAYS("opd.followup.free_days", "Days within which follow-up is free"),
    OPD_FOLLOW_UP_REDUCED_FEE("opd.followup.reduced_fee", "Reduced fee percentage for follow-up"),
    OPD_CONSULTATION_FEE("opd.consultation.fee", "Standard OPD consultation fee"),
    OPD_DOCTOR_LEAVE_REQUIRES_APPROVAL("opd.doctor.leave_requires_approval", "Require admin approval for doctor leave (default true)"),
    OPD_REFERRAL_FEE_PERCENTAGE("opd.referral.fee_percentage", "Percentage of consultation fee to referral doctor (default 25)"),

    // IPD Policies
    IPD_GENERAL_BED_DAILY_RATE("ipd.general_bed.daily_rate", "General bed daily charging rate"),
    IPD_ICU_HOURLY_RATE("ipd.icu.hourly_rate", "ICU bed hourly charging rate"),
    IPD_INDENT_APPROVAL_REQUIRED("ipd.indent.approval_required", "Require approval for indent items"),
    IPD_INDENT_APPROVAL_CATEGORIES("ipd.indent.approval.required_categories",
            "CSV of indent item categories that need approval before dispense"),
    IPD_DISCHARGE_CHECKLIST_BLOCKING_ITEMS("ipd.discharge.checklist_blocking_items", "Items that block discharge"),
    IPD_BED_ISOLATION_DEFAULT_HOURS("ipd.bed.isolation_default_hours", "Default bed isolation duration in hours"),

    // Prescription Policies
    RX_ALLERGY_CHECK_ENABLED("rx.allergy.check_enabled", "Block prescribing medicines that match patient allergies"),

    // Pharmacy Policies
    PHARMACY_SUBSTITUTION_ALLOWED("pharmacy.substitution.allowed", "Allow medicine substitution"),
    PHARMACY_GENERIC_SUBSTITUTION("pharmacy.generic.substitution", "Auto-suggest generic medicines"),

    // Billing Policies
    BILLING_PATIENT_CREDIT_LIMIT("billing.patient.credit_limit", "Maximum credit limit per patient"),
    BILLING_PATIENT_CREDIT_AUTO_DEDUCT("billing.patient.credit.auto_deduct", "Auto-deduct from patient credit when bill is generated"),
    BILLING_PATIENT_CREDIT_LIMIT_BLOCK_ACTION("billing.patient.credit.limit_block_action", "Action when credit limit exceeded: WARN, BLOCK, ALLOW"),
    BILLING_DISCOUNT_THRESHOLD_LEVEL_1("billing.discount.threshold_level_1", "Threshold for 1st level discount approval"),
    BILLING_DISCOUNT_LEVEL_1_PERCENTAGE("billing.discount.level_1_percentage", "Percentage discount for level 1"),
    BILLING_DISCOUNT_THRESHOLD_LEVEL_2("billing.discount.threshold_level_2", "Threshold for 2nd level discount approval"),

    // TPA Policies
    TPA_PREAUTH_AUTO_APPROVE_AMOUNT("tpa.preauth.auto_approve_amount", "Auto-approve preauth below this amount"),
    TPA_DOCUMENT_REMINDER_DAYS("tpa.document.reminder_days", "Days to remind about overdue documents"),

    // Patient Policies
    PATIENT_UHID_FORMAT("patient.uhid_format", "UHID format pattern (e.g., HOS-{branch}-{seq})"),
    PATIENT_UHID_SEQ_START("patient.uhid_seq_start", "Starting sequence number for UHID generation"),

    // General Policies
    ENABLE_PATIENT_PORTAL("general.enable_patient_portal", "Enable patient self-service portal"),
    ENABLE_SMS_NOTIFICATION("general.enable_sms_notification", "Enable SMS notifications"),
    ENABLE_WHATSAPP_NOTIFICATION("general.enable_whatsapp_notification", "Enable WhatsApp notifications");

    private final String code;
    private final String description;

    HospitalPolicyCode(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }
}
