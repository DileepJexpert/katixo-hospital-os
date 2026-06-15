package com.katixo.hospital.dashboard;

import com.katixo.hospital.tenant.TenantContext;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Owner / MIS dashboard. Read-only KPIs aggregated from the hospital's own
 * ledger (revenue, expense, receivables, cash) and operational tables (OPD/IPD
 * volumes, pharmacy, beds). Everything is tenant-scoped; Hibernate routes the
 * native queries to the tenant schema and we also filter by tenant_id.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DashboardService {

    @PersistenceContext
    private EntityManager em;

    public Map<String, Object> summary(LocalDate from, LocalDate to) {
        String tenant = TenantContext.get().getTenantId();
        LocalDate f = from == null ? LocalDate.now().withDayOfMonth(1) : from;
        LocalDate t = to == null ? LocalDate.now() : to;
        LocalDate tExclusive = t.plusDays(1); // for timestamp columns: include all of 'to'

        // --- financials (period, by account type via the ledger) ---
        BigDecimal revenue = ledgerByType(tenant, "INCOME", true, f, t);   // credit-normal
        BigDecimal expense = ledgerByType(tenant, "EXPENSE", false, f, t); // debit-normal
        BigDecimal pharmacyRevenue = ledgerByCodePeriod(tenant, List.of("4010"), true, f, t);
        BigDecimal serviceRevenue = ledgerByCodePeriod(tenant, List.of("4020"), true, f, t);

        // --- balances as of now (no date filter) ---
        BigDecimal cashAndBank = ledgerByCode(tenant, List.of("1010", "1020"), true);  // debit-normal asset
        BigDecimal patientReceivable = ledgerByCode(tenant, List.of("1100"), true);
        BigDecimal insuranceReceivable = ledgerByCode(tenant, List.of("1110"), true);

        // --- volumes (period) ---
        long opdVisits = countTimestamp(tenant, "opd_visit", "created_at", f, tExclusive);
        long ipdAdmissions = countTimestamp(tenant, "ipd_admission", "admitted_at", f, tExclusive);
        long newPatients = countTimestamp(tenant, "patient", "created_at", f, tExclusive);
        long pharmacySalesCount = pharmacySalesCount(tenant, f, t);
        BigDecimal pharmacySalesValue = pharmacySalesValue(tenant, f, t);

        // --- current snapshot ---
        long currentInpatients = currentInpatients(tenant);
        long totalBeds = totalBeds(tenant);
        BigDecimal occupancyPct = totalBeds == 0 ? BigDecimal.ZERO
                : BigDecimal.valueOf(currentInpatients * 100.0 / totalBeds)
                        .setScale(1, java.math.RoundingMode.HALF_UP);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("fromDate", f.toString());
        out.put("toDate", t.toString());

        Map<String, Object> financial = new LinkedHashMap<>();
        financial.put("revenue", revenue);
        financial.put("expense", expense);
        financial.put("netSurplus", revenue.subtract(expense));
        financial.put("pharmacyRevenue", pharmacyRevenue);
        financial.put("serviceRevenue", serviceRevenue);
        out.put("financial", financial);

        Map<String, Object> receivables = new LinkedHashMap<>();
        receivables.put("cashAndBank", cashAndBank);
        receivables.put("patientReceivable", patientReceivable);
        receivables.put("insuranceReceivable", insuranceReceivable);
        out.put("receivables", receivables);

        Map<String, Object> volumes = new LinkedHashMap<>();
        volumes.put("opdVisits", opdVisits);
        volumes.put("ipdAdmissions", ipdAdmissions);
        volumes.put("newPatients", newPatients);
        volumes.put("pharmacySalesCount", pharmacySalesCount);
        volumes.put("pharmacySalesValue", pharmacySalesValue);
        out.put("volumes", volumes);

        Map<String, Object> occupancy = new LinkedHashMap<>();
        occupancy.put("currentInpatients", currentInpatients);
        occupancy.put("totalBeds", totalBeds);
        occupancy.put("occupancyPct", occupancyPct);
        out.put("occupancy", occupancy);

        return out;
    }

    // ---------------- ledger aggregates ----------------

    /** Net for all accounts of a type over a period. creditNormal => SUM(credit-debit), else SUM(debit-credit). */
    private BigDecimal ledgerByType(String tenant, String accountType, boolean creditNormal,
                                    LocalDate from, LocalDate to) {
        String expr = creditNormal ? "(jl.credit - jl.debit)" : "(jl.debit - jl.credit)";
        Query q = em.createNativeQuery(
                "SELECT COALESCE(SUM(" + expr + "),0) FROM journal_line jl "
                        + "JOIN journal_entry je ON je.id = jl.journal_entry_id AND je.tenant_id = jl.tenant_id "
                        + "JOIN account a ON a.code = jl.account_code AND a.tenant_id = jl.tenant_id "
                        + "WHERE jl.tenant_id = :tenant AND a.account_type = :type "
                        + "AND je.entry_date >= :from AND je.entry_date <= :to");
        q.setParameter("tenant", tenant);
        q.setParameter("type", accountType);
        q.setParameter("from", from);
        q.setParameter("to", to);
        return scalar(q);
    }

    private BigDecimal ledgerByCodePeriod(String tenant, List<String> codes, boolean creditNormal,
                                          LocalDate from, LocalDate to) {
        String expr = creditNormal ? "(jl.credit - jl.debit)" : "(jl.debit - jl.credit)";
        Query q = em.createNativeQuery(
                "SELECT COALESCE(SUM(" + expr + "),0) FROM journal_line jl "
                        + "JOIN journal_entry je ON je.id = jl.journal_entry_id AND je.tenant_id = jl.tenant_id "
                        + "WHERE jl.tenant_id = :tenant AND jl.account_code IN (:codes) "
                        + "AND je.entry_date >= :from AND je.entry_date <= :to");
        q.setParameter("tenant", tenant);
        q.setParameter("codes", codes);
        q.setParameter("from", from);
        q.setParameter("to", to);
        return scalar(q);
    }

    /** Balance as of now for specific account codes. */
    private BigDecimal ledgerByCode(String tenant, List<String> codes, boolean debitNormal) {
        String expr = debitNormal ? "(jl.debit - jl.credit)" : "(jl.credit - jl.debit)";
        Query q = em.createNativeQuery(
                "SELECT COALESCE(SUM(" + expr + "),0) FROM journal_line jl "
                        + "WHERE jl.tenant_id = :tenant AND jl.account_code IN (:codes)");
        q.setParameter("tenant", tenant);
        q.setParameter("codes", codes);
        return scalar(q);
    }

    // ---------------- operational counts ----------------

    private long countTimestamp(String tenant, String table, String col, LocalDate from, LocalDate toExclusive) {
        Query q = em.createNativeQuery("SELECT COUNT(*) FROM " + table
                + " WHERE tenant_id = :tenant AND " + col + " >= :from AND " + col + " < :toEx");
        q.setParameter("tenant", tenant);
        q.setParameter("from", from.atStartOfDay());
        q.setParameter("toEx", toExclusive.atStartOfDay());
        return count(q);
    }

    private long pharmacySalesCount(String tenant, LocalDate from, LocalDate to) {
        Query q = em.createNativeQuery("SELECT COUNT(*) FROM pharmacy_sale "
                + "WHERE tenant_id = :tenant AND status = 'ACTIVE' AND sale_date >= :from AND sale_date <= :to");
        q.setParameter("tenant", tenant);
        q.setParameter("from", from);
        q.setParameter("to", to);
        return count(q);
    }

    private BigDecimal pharmacySalesValue(String tenant, LocalDate from, LocalDate to) {
        Query q = em.createNativeQuery("SELECT COALESCE(SUM(grand_total),0) FROM pharmacy_sale "
                + "WHERE tenant_id = :tenant AND status = 'ACTIVE' AND sale_date >= :from AND sale_date <= :to");
        q.setParameter("tenant", tenant);
        q.setParameter("from", from);
        q.setParameter("to", to);
        return scalar(q);
    }

    private long currentInpatients(String tenant) {
        Query q = em.createNativeQuery("SELECT COUNT(*) FROM ipd_admission "
                + "WHERE tenant_id = :tenant AND admission_status = 'ADMITTED'");
        q.setParameter("tenant", tenant);
        return count(q);
    }

    private long totalBeds(String tenant) {
        Query q = em.createNativeQuery("SELECT COUNT(*) FROM bed "
                + "WHERE tenant_id = :tenant AND status = 'ACTIVE'");
        q.setParameter("tenant", tenant);
        return count(q);
    }

    // ---------------- helpers ----------------

    private BigDecimal scalar(Query q) {
        Object r = q.getSingleResult();
        if (r == null) {
            return BigDecimal.ZERO;
        }
        return (r instanceof BigDecimal bd) ? bd : new BigDecimal(r.toString());
    }

    private long count(Query q) {
        Object r = q.getSingleResult();
        return r == null ? 0L : ((Number) r).longValue();
    }
}
