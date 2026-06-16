package com.katixo.hospital.vendor;

import com.katixo.hospital.audit.AuditLog;
import com.katixo.hospital.audit.AuditService;
import com.katixo.hospital.common.entity.BaseEntity;
import com.katixo.hospital.common.exception.BusinessException;
import com.katixo.hospital.tenant.TenantContext;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Vendor/supplier master. Vendors are reusable across expenses (and future
 * purchase bills). Never hard-deleted — deactivated instead, so historical
 * expenses keep their link.
 */
@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class VendorService {

    private final VendorRepository vendorRepository;
    private final AuditService auditService;

    /** Mutable field bag for create/update so the signature stays stable as the master grows. */
    @Getter
    @Builder
    public static class VendorInput {
        private String name;
        private Vendor.VendorType vendorType;
        private String gstin;
        private String pan;
        private String contactPerson;
        private String contactPhone;
        private String contactEmail;
        private String addressLine;
        private String city;
        private String state;
        private String pincode;
        private String bankAccountName;
        private String bankAccountNumber;
        private String bankIfsc;
        private String notes;
    }

    public Vendor create(VendorInput in) {
        if (in == null || in.getName() == null || in.getName().isBlank()) {
            throw new BusinessException("VENDOR_NAME_REQUIRED", "Vendor name is required");
        }
        var ctx = TenantContext.get();
        Vendor v = new Vendor();
        v.setVendorCode("VEN-" + vendorRepository.nextVendorSequence());
        applyInput(v, in);
        if (v.getVendorType() == null) {
            v.setVendorType(Vendor.VendorType.SUPPLIER);
        }
        v.setActive(true);
        stamp(v);
        Vendor saved = vendorRepository.save(v);
        auditService.audit("Vendor", String.valueOf(saved.getId()), AuditLog.AuditAction.CREATE,
                null, Map.of("vendorCode", saved.getVendorCode(), "name", saved.getName(),
                        "type", saved.getVendorType().name()), UUID.randomUUID().toString());
        log.info("Vendor {} created: {}", saved.getVendorCode(), saved.getName());
        return saved;
    }

    public Vendor update(Long id, VendorInput in) {
        Vendor v = getOwned(id);
        if (in.getName() != null && in.getName().isBlank()) {
            throw new BusinessException("VENDOR_NAME_REQUIRED", "Vendor name cannot be blank");
        }
        applyInput(v, in);
        v.setUpdatedBy(userId());
        Vendor saved = vendorRepository.save(v);
        auditService.audit("Vendor", String.valueOf(id), AuditLog.AuditAction.UPDATE,
                null, Map.of("vendorCode", saved.getVendorCode(), "name", saved.getName()),
                UUID.randomUUID().toString());
        return saved;
    }

    public Vendor setActive(Long id, boolean active) {
        Vendor v = getOwned(id);
        v.setActive(active);
        v.setStatus(active ? BaseEntity.EntityStatus.ACTIVE : BaseEntity.EntityStatus.INACTIVE);
        v.setUpdatedBy(userId());
        Vendor saved = vendorRepository.save(v);
        auditService.audit("Vendor", String.valueOf(id), AuditLog.AuditAction.UPDATE,
                null, Map.of("active", active), UUID.randomUUID().toString());
        return saved;
    }

    @Transactional(readOnly = true)
    public List<Vendor> list(boolean includeInactive) {
        var ctx = TenantContext.get();
        return includeInactive
                ? vendorRepository.findByTenantIdAndBranchIdOrderByName(ctx.getTenantId(), branchId())
                : vendorRepository.findByTenantIdAndBranchIdAndActiveTrueOrderByName(ctx.getTenantId(), branchId());
    }

    @Transactional(readOnly = true)
    public Vendor get(Long id) {
        return getOwned(id);
    }

    /** Resolves a vendor that must belong to the current tenant/branch. Used by other modules (e.g. expenses). */
    @Transactional(readOnly = true)
    public Vendor require(Long id) {
        return getOwned(id);
    }

    private void applyInput(Vendor v, VendorInput in) {
        if (in.getName() != null) v.setName(in.getName());
        if (in.getVendorType() != null) v.setVendorType(in.getVendorType());
        if (in.getGstin() != null) v.setGstin(blankToNull(in.getGstin()));
        if (in.getPan() != null) v.setPan(blankToNull(in.getPan()));
        if (in.getContactPerson() != null) v.setContactPerson(blankToNull(in.getContactPerson()));
        if (in.getContactPhone() != null) v.setContactPhone(blankToNull(in.getContactPhone()));
        if (in.getContactEmail() != null) v.setContactEmail(blankToNull(in.getContactEmail()));
        if (in.getAddressLine() != null) v.setAddressLine(blankToNull(in.getAddressLine()));
        if (in.getCity() != null) v.setCity(blankToNull(in.getCity()));
        if (in.getState() != null) v.setState(blankToNull(in.getState()));
        if (in.getPincode() != null) v.setPincode(blankToNull(in.getPincode()));
        if (in.getBankAccountName() != null) v.setBankAccountName(blankToNull(in.getBankAccountName()));
        if (in.getBankAccountNumber() != null) v.setBankAccountNumber(blankToNull(in.getBankAccountNumber()));
        if (in.getBankIfsc() != null) v.setBankIfsc(blankToNull(in.getBankIfsc()));
        if (in.getNotes() != null) v.setNotes(blankToNull(in.getNotes()));
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private Vendor getOwned(Long id) {
        var ctx = TenantContext.get();
        return vendorRepository.findByIdAndTenantIdAndBranchId(id, ctx.getTenantId(), branchId())
                .orElseThrow(() -> new BusinessException("VENDOR_NOT_FOUND", "Vendor not found: " + id));
    }

    private void stamp(BaseEntity entity) {
        var ctx = TenantContext.get();
        entity.setTenantId(ctx.getTenantId());
        entity.setHospitalGroupId(Long.parseLong(ctx.getHospitalGroupId()));
        entity.setBranchId(branchId());
        entity.setCreatedBy(userId());
        entity.setUpdatedBy(userId());
        entity.setStatus(BaseEntity.EntityStatus.ACTIVE);
    }

    private Long branchId() {
        return Long.parseLong(TenantContext.get().getBranchId());
    }

    private Long userId() {
        return Long.parseLong(TenantContext.get().getUserId());
    }
}
