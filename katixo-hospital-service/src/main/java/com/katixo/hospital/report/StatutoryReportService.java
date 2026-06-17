package com.katixo.hospital.report;

import com.katixo.hospital.tenant.TenantContext;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Statutory / operational reports off the ledger: GST output-tax summary
 * (GSTR-1/3B for the hospital's only taxable supply — pharmacy sales),
 * the day book (chronological journal), and cash / bank books (one ledger
 * account with a running balance). All tenant-scoped and read-only.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StatutoryReportService {

    private static final String CASH_ACCOUNT = "1010";
    private static final String BANK_ACCOUNT = "1020";

    @PersistenceContext
    private EntityManager em;

    /** GST output-tax summary for the period. Pharmacy sales are the only taxable supply; reversed sales excluded. */
    public Map<String, Object> gstSummary(LocalDate from, LocalDate to) {
        LocalDate f = from == null ? LocalDate.now().withDayOfMonth(1) : from;
        LocalDate t = to == null ? LocalDate.now() : to;
        Query q = em.createNativeQuery(
                "SELECT COALESCE(SUM(taxable_total),0), COALESCE(SUM(cgst_total),0), "
                        + "COALESCE(SUM(sgst_total),0), COALESCE(SUM(igst_total),0), "
                        + "COALESCE(SUM(grand_total),0), COUNT(*) "
                        + "FROM pharmacy_sale WHERE tenant_id = :t AND reversed = false "
                        + "AND sale_date BETWEEN :from AND :to");
        q.setParameter("t", tenant());
        q.setParameter("from", f);
        q.setParameter("to", t);
        Object[] r = (Object[]) q.getSingleResult();

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("fromDate", f.toString());
        out.put("toDate", t.toString());
        out.put("taxableValue", big(r[0]));
        out.put("cgst", big(r[1]));
        out.put("sgst", big(r[2]));
        out.put("igst", big(r[3]));
        out.put("totalTax", big(r[1]).add(big(r[2])).add(big(r[3])));
        out.put("totalValue", big(r[4]));
        out.put("invoiceCount", ((Number) r[5]).longValue());
        out.put("note", "Hospital service charges are GST-exempt healthcare supplies; "
                + "only pharmacy/consumable sales carry output GST.");
        return out;
    }

    /** Day book: every journal entry in the range with its lines, chronological. */
    @SuppressWarnings("unchecked")
    public Map<String, Object> dayBook(LocalDate from, LocalDate to) {
        LocalDate f = from == null ? LocalDate.now() : from;
        LocalDate t = to == null ? f : to;
        Query q = em.createNativeQuery(
                "SELECT je.id, je.entry_date, je.entry_number, je.description, je.source_module, "
                        + "jl.account_code, jl.debit, jl.credit, jl.line_description "
                        + "FROM journal_line jl "
                        + "JOIN journal_entry je ON je.id = jl.journal_entry_id AND je.tenant_id = jl.tenant_id "
                        + "WHERE je.tenant_id = :t AND je.entry_date BETWEEN :from AND :to "
                        + "ORDER BY je.entry_date, je.id, jl.id");
        q.setParameter("t", tenant());
        q.setParameter("from", f);
        q.setParameter("to", t);
        List<Object[]> rows = q.getResultList();

        List<Map<String, Object>> entries = new ArrayList<>();
        Map<String, Object> current = null;
        Long currentId = null;
        BigDecimal totalDebit = BigDecimal.ZERO;
        for (Object[] r : rows) {
            Long id = ((Number) r[0]).longValue();
            if (!id.equals(currentId)) {
                current = new LinkedHashMap<>();
                current.put("entryDate", r[1].toString());
                current.put("entryNumber", r[2]);
                current.put("description", r[3]);
                current.put("sourceModule", r[4]);
                current.put("lines", new ArrayList<Map<String, Object>>());
                entries.add(current);
                currentId = id;
            }
            Map<String, Object> line = new LinkedHashMap<>();
            line.put("accountCode", r[5]);
            line.put("debit", big(r[6]));
            line.put("credit", big(r[7]));
            line.put("description", r[8]);
            ((List<Map<String, Object>>) current.get("lines")).add(line);
            totalDebit = totalDebit.add(big(r[6]));
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("fromDate", f.toString());
        out.put("toDate", t.toString());
        out.put("entries", entries);
        out.put("entryCount", entries.size());
        out.put("totalDebit", totalDebit);
        return out;
    }

    public Map<String, Object> cashBook(LocalDate from, LocalDate to) {
        return accountBook(CASH_ACCOUNT, "Cash", from, to);
    }

    public Map<String, Object> bankBook(LocalDate from, LocalDate to) {
        return accountBook(BANK_ACCOUNT, "Bank", from, to);
    }

    /** One ledger account with opening balance, period movements and a running balance. */
    @SuppressWarnings("unchecked")
    private Map<String, Object> accountBook(String accountCode, String label, LocalDate from, LocalDate to) {
        LocalDate f = from == null ? LocalDate.now().withDayOfMonth(1) : from;
        LocalDate t = to == null ? LocalDate.now() : to;

        Query openingQ = em.createNativeQuery(
                "SELECT COALESCE(SUM(jl.debit - jl.credit),0) FROM journal_line jl "
                        + "JOIN journal_entry je ON je.id = jl.journal_entry_id AND je.tenant_id = jl.tenant_id "
                        + "WHERE jl.tenant_id = :t AND jl.account_code = :code AND je.entry_date < :from");
        openingQ.setParameter("t", tenant());
        openingQ.setParameter("code", accountCode);
        openingQ.setParameter("from", f);
        BigDecimal opening = big(openingQ.getSingleResult());

        Query q = em.createNativeQuery(
                "SELECT je.entry_date, je.entry_number, je.description, jl.debit, jl.credit "
                        + "FROM journal_line jl "
                        + "JOIN journal_entry je ON je.id = jl.journal_entry_id AND je.tenant_id = jl.tenant_id "
                        + "WHERE jl.tenant_id = :t AND jl.account_code = :code "
                        + "AND je.entry_date BETWEEN :from AND :to "
                        + "ORDER BY je.entry_date, je.id, jl.id");
        q.setParameter("t", tenant());
        q.setParameter("code", accountCode);
        q.setParameter("from", f);
        q.setParameter("to", t);
        List<Object[]> rows = q.getResultList();

        List<Map<String, Object>> entries = new ArrayList<>();
        BigDecimal running = opening;
        BigDecimal totalIn = BigDecimal.ZERO;
        BigDecimal totalOut = BigDecimal.ZERO;
        for (Object[] r : rows) {
            BigDecimal debit = big(r[3]);
            BigDecimal credit = big(r[4]);
            running = running.add(debit).subtract(credit);
            totalIn = totalIn.add(debit);
            totalOut = totalOut.add(credit);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("entryDate", r[0].toString());
            row.put("entryNumber", r[1]);
            row.put("description", r[2]);
            row.put("inflow", debit);
            row.put("outflow", credit);
            row.put("balance", running);
            entries.add(row);
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("account", label);
        out.put("accountCode", accountCode);
        out.put("fromDate", f.toString());
        out.put("toDate", t.toString());
        out.put("openingBalance", opening);
        out.put("entries", entries);
        out.put("totalInflow", totalIn);
        out.put("totalOutflow", totalOut);
        out.put("closingBalance", running);
        return out;
    }

    private String tenant() {
        return TenantContext.get().getTenantId();
    }

    private static BigDecimal big(Object o) {
        if (o == null) return BigDecimal.ZERO;
        return o instanceof BigDecimal b ? b : new BigDecimal(o.toString());
    }
}
