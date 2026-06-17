package com.katixo.hospital.billing;

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

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Billing packages: define bundled fixed-price packages with their component
 * services, and apply a package to an encounter (which adds its price as an
 * UNBILLED charge via {@link BillingService#addPackageCharge}, flowing into the
 * normal bill). Auto-deduction of included items for EXCESS/ITEMIZED packages is
 * future work; today a package bills as a priced line.
 */
@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class PackageService {

    private final BillingPackageRepository packageRepository;
    private final PackageComponentRepository componentRepository;
    private final BillingService billingService;
    private final AuditService auditService;

    @Getter
    @Builder
    public static class ComponentInput {
        private String serviceCode;
        private String serviceName;
        private BigDecimal includedQuantity;
    }

    public BillingPackage createPackage(String code, String name, BillingPackage.PackageType type,
                                        BigDecimal price, String notes, List<ComponentInput> components) {
        if (code == null || code.isBlank()) {
            throw new BusinessException("PKG_CODE_REQUIRED", "Package code is required");
        }
        if (name == null || name.isBlank()) {
            throw new BusinessException("PKG_NAME_REQUIRED", "Package name is required");
        }
        if (price == null || price.signum() < 0) {
            throw new BusinessException("PKG_PRICE_INVALID", "Package price must be non-negative");
        }
        var ctx = TenantContext.get();
        if (packageRepository.existsByTenantIdAndBranchIdAndCode(ctx.getTenantId(), branchId(), code.trim())) {
            throw new BusinessException("PKG_CODE_EXISTS", "A package with code " + code + " already exists");
        }
        BillingPackage pkg = new BillingPackage();
        pkg.setCode(code.trim());
        pkg.setName(name.trim());
        pkg.setPackageType(type == null ? BillingPackage.PackageType.FIXED : type);
        pkg.setPackagePrice(price);
        pkg.setNotes(notes);
        pkg.setActive(true);
        stamp(pkg);
        BillingPackage saved = packageRepository.save(pkg);

        if (components != null) {
            for (ComponentInput in : components) {
                if (in.getServiceCode() == null || in.getServiceCode().isBlank()) {
                    continue;
                }
                PackageComponent c = new PackageComponent();
                c.setPackageId(saved.getId());
                c.setServiceCode(in.getServiceCode().trim());
                c.setServiceName(in.getServiceName() == null ? in.getServiceCode().trim() : in.getServiceName());
                c.setIncludedQuantity(in.getIncludedQuantity() == null ? BigDecimal.ONE : in.getIncludedQuantity());
                stamp(c);
                componentRepository.save(c);
            }
        }
        auditService.audit("BillingPackage", String.valueOf(saved.getId()), AuditLog.AuditAction.CREATE,
                null, Map.of("code", saved.getCode(), "price", price), UUID.randomUUID().toString());
        log.info("Billing package {} created ({}, {})", saved.getCode(), saved.getPackageType(), price);
        return saved;
    }

    @Transactional(readOnly = true)
    public List<BillingPackage> listPackages() {
        var ctx = TenantContext.get();
        return packageRepository.findByTenantIdAndBranchIdOrderByName(ctx.getTenantId(), branchId());
    }

    @Transactional(readOnly = true)
    public BillingPackage getPackage(Long id) {
        return getOwned(id);
    }

    @Transactional(readOnly = true)
    public List<PackageComponent> getComponents(Long packageId) {
        getOwned(packageId);
        return componentRepository.findByTenantIdAndPackageIdOrderById(TenantContext.get().getTenantId(), packageId);
    }

    /** Applies the package price to an encounter as an UNBILLED charge. */
    public HospitalCharge applyToEncounter(Long packageId, Long patientId,
                                           HospitalCharge.SourceType sourceType, Long sourceId) {
        BillingPackage pkg = getOwned(packageId);
        if (!pkg.isActive()) {
            throw new BusinessException("PKG_INACTIVE", "Package " + pkg.getCode() + " is inactive");
        }
        HospitalCharge charge = billingService.addPackageCharge(patientId, sourceType, sourceId,
                pkg.getCode(), pkg.getName(), pkg.getPackagePrice());
        log.info("Package {} applied to {} {} ({})", pkg.getCode(), sourceType, sourceId, pkg.getPackagePrice());
        return charge;
    }

    private BillingPackage getOwned(Long id) {
        var ctx = TenantContext.get();
        return packageRepository.findByIdAndTenantIdAndBranchId(id, ctx.getTenantId(), branchId())
                .orElseThrow(() -> new BusinessException("PKG_NOT_FOUND", "Package not found: " + id));
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
