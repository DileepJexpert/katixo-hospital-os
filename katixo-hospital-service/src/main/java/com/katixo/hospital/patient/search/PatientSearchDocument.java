package com.katixo.hospital.patient.search;

import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

/**
 * Elasticsearch projection of a patient for fuzzy/typo-tolerant search. Only used
 * when {@code katixo.search.elasticsearch.enabled=true}; the document id is
 * {@code <tenantId>:<patientId>} so a tenant's patients never collide in the
 * shared index, and every query is still filtered by {@code tenantId}/{@code branchId}.
 */
@Document(indexName = "patient-search")
public class PatientSearchDocument {

    @Id
    private String id;

    @Field(type = FieldType.Keyword)
    private String tenantId;

    @Field(type = FieldType.Long)
    private Long branchId;

    @Field(type = FieldType.Long)
    private Long patientId;

    @Field(type = FieldType.Text)
    private String fullName;

    @Field(type = FieldType.Keyword)
    private String mobile;

    @Field(type = FieldType.Keyword)
    private String uhid;

    @Field(type = FieldType.Keyword)
    private String email;

    public PatientSearchDocument() {
    }

    public static String idOf(String tenantId, Long patientId) {
        return tenantId + ":" + patientId;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public Long getBranchId() {
        return branchId;
    }

    public void setBranchId(Long branchId) {
        this.branchId = branchId;
    }

    public Long getPatientId() {
        return patientId;
    }

    public void setPatientId(Long patientId) {
        this.patientId = patientId;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getMobile() {
        return mobile;
    }

    public void setMobile(String mobile) {
        this.mobile = mobile;
    }

    public String getUhid() {
        return uhid;
    }

    public void setUhid(String uhid) {
        this.uhid = uhid;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }
}
