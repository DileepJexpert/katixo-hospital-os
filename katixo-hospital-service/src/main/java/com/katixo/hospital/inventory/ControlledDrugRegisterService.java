package com.katixo.hospital.inventory;

import com.katixo.hospital.common.entity.BaseEntity;
import com.katixo.hospital.common.exception.BusinessException;
import com.katixo.hospital.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Maintains the Schedule H1 / X / NDPS controlled-drug register. Entries are
 * created automatically when a controlled item is supplied on a pharmacy sale;
 * the prescriber details (required for H1/X) can be filled in afterwards from the
 * physical prescription. Append-only; retained for inspection.
 */
@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class ControlledDrugRegisterService {

    private final ControlledDrugRegisterRepository repository;

    /** Auto-record a controlled-drug supply from a pharmacy sale line. */
    public void record(PharmacySale sale, PharmacySaleLine line, Item item, String batchNumber) {
        ControlledDrugRegisterEntry e = new ControlledDrugRegisterEntry();
        e.setEntryDate(sale.getSaleDate() == null ? LocalDate.now() : sale.getSaleDate());
        e.setDrugSchedule(item.getDrugSchedule());
        e.setItemId(item.getId());
        e.setItemCode(item.getCode());
        e.setItemName(item.getName());
        e.setQuantity(line.getQuantity());
        e.setBatchNumber(batchNumber);
        e.setPatientId(sale.getPatientId());
        e.setSaleId(sale.getId());
        e.setSaleNumber(sale.getSaleNumber());
        stamp(e);
        repository.save(e);
        log.info("Controlled-drug register: {} {} x{} (sale {})",
                item.getDrugSchedule(), item.getCode(), line.getQuantity(), sale.getSaleNumber());
    }

    @Transactional(readOnly = true)
    public List<ControlledDrugRegisterEntry> list(LocalDate from, LocalDate to, Item.DrugSchedule schedule) {
        var ctx = TenantContext.get();
        return repository.search(ctx.getTenantId(), branchId(), from, to, schedule);
    }

    /** Record/correct the prescriber details for a controlled-drug supply (Rule 65). */
    public ControlledDrugRegisterEntry setPrescriber(Long id, String name, String address) {
        var ctx = TenantContext.get();
        ControlledDrugRegisterEntry e = repository.findByIdAndTenantIdAndBranchId(id, ctx.getTenantId(), branchId())
                .orElseThrow(() -> new BusinessException("CDR_NOT_FOUND", "Register entry not found: " + id));
        e.setPrescriberName(name);
        e.setPrescriberAddress(address);
        e.setUpdatedBy(userId());
        e.setUpdatedAt(LocalDateTime.now());
        return repository.save(e);
    }

    private void stamp(ControlledDrugRegisterEntry e) {
        var ctx = TenantContext.get();
        e.setTenantId(ctx.getTenantId());
        e.setHospitalGroupId(Long.parseLong(ctx.getHospitalGroupId()));
        e.setBranchId(branchId());
        e.setCreatedBy(userId());
        e.setUpdatedBy(userId());
        e.setStatus(BaseEntity.EntityStatus.ACTIVE);
    }

    private Long branchId() {
        return Long.parseLong(TenantContext.get().getBranchId());
    }

    private Long userId() {
        return Long.parseLong(TenantContext.get().getUserId());
    }
}
