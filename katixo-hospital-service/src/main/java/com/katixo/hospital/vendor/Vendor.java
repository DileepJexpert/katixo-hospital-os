package com.katixo.hospital.vendor;

import com.katixo.hospital.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A supplier/vendor the hospital buys from or pays (medicine distributor,
 * landlord, utility, service contractor, government body, …). Replaces the
 * free-text payee on expenses with a reusable master record carrying GSTIN,
 * contact and bank details so payments and (future) purchase bills can link
 * to a single recurring entity. Free-text payee on the expense remains a
 * fallback when no vendor is selected.
 */
@Entity
@Table(name = "vendor",
        uniqueConstraints = @UniqueConstraint(name = "uq_vendor_tenant_code", columnNames = {"tenant_id", "vendor_code"}),
        indexes = {
                @Index(name = "idx_vendor_tenant_branch", columnList = "tenant_id,branch_id"),
                @Index(name = "idx_vendor_name", columnList = "tenant_id,name")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Vendor extends BaseEntity {

    @Column(nullable = false, length = 30)
    private String vendorCode;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(nullable = false, length = 30)
    @Enumerated(EnumType.STRING)
    private VendorType vendorType = VendorType.SUPPLIER;

    /** 15-char GST identification number (optional — unregistered vendors have none). */
    @Column(length = 15)
    private String gstin;

    @Column(length = 10)
    private String pan;

    @Column(length = 150)
    private String contactPerson;

    @Column(length = 20)
    private String contactPhone;

    @Column(length = 150)
    private String contactEmail;

    @Column(length = 300)
    private String addressLine;

    @Column(length = 100)
    private String city;

    @Column(length = 100)
    private String state;

    @Column(length = 10)
    private String pincode;

    @Column(length = 150)
    private String bankAccountName;

    @Column(length = 30)
    private String bankAccountNumber;

    @Column(length = 15)
    private String bankIfsc;

    @Column(length = 300)
    private String notes;

    @Column(nullable = false)
    private boolean active = true;

    /** Broad classification used for filtering and (loosely) mapping to expense categories. */
    public enum VendorType {
        SUPPLIER, SERVICE_PROVIDER, LANDLORD, UTILITY, CONTRACTOR, GOVERNMENT, OTHER
    }
}
