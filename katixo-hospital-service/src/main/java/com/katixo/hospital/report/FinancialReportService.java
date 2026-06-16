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
 * Read-only statutory financial statements assembled from the hospital's own
 * double-entry ledger: <b>trial balance</b>, <b>profit &amp; loss</b> and
 * <b>balance sheet</b>. Everything is tenant-scoped — Hibernate routes the
 * native query to the tenant schema and we also filter by {@code tenant_id}.
 *
 * <p>Reversals are mirror entries that remain in the ledger, so summing all
 * lines nets a reversed transaction to zero — the statements reflect the true
 * net position without any special-casing.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FinancialReportService {

    @PersistenceContext
    private EntityManager em;

    /** Per-account debit/credit totals for the requested window. */
    public record AccountBalance(String code, String name, String accountType,
                                 BigDecimal debit, BigDecimal credit) {
        /** Debit-normal accounts (ASSET, EXPENSE) carry debit − credit; others credit − debit. */
        BigDecimal signedBalance() {
            boolean debitNormal = "ASSET".equals(accountType) || "EXPENSE".equals(accountType);
            return debitNormal ? debit.subtract(credit) : credit.subtract(debit);
        }
    }

    // ---------------- Trial balance ----------------

    /** Trial balance as of {@code asOf} (inclusive). Sum of debit column equals sum of credit column. */
    public Map<String, Object> trialBalance(LocalDate asOf) {
        LocalDate to = asOf == null ? LocalDate.now() : asOf;
        return buildTrialBalance(fetch(null, to), to);
    }

    static Map<String, Object> buildTrialBalance(List<AccountBalance> balances, LocalDate asOf) {
        List<Map<String, Object>> rows = new ArrayList<>();
        BigDecimal totalDebit = BigDecimal.ZERO;
        BigDecimal totalCredit = BigDecimal.ZERO;
        for (AccountBalance b : balances) {
            BigDecimal net = b.signedBalance();
            if (net.signum() == 0) {
                continue; // suppress accounts with no movement / zero balance
            }
            boolean debitNormal = "ASSET".equals(b.accountType()) || "EXPENSE".equals(b.accountType());
            // A positive normal balance sits in its natural column; a negative one flips columns.
            BigDecimal debitCol = BigDecimal.ZERO;
            BigDecimal creditCol = BigDecimal.ZERO;
            if (debitNormal) {
                if (net.signum() > 0) debitCol = net; else creditCol = net.negate();
            } else {
                if (net.signum() > 0) creditCol = net; else debitCol = net.negate();
            }
            totalDebit = totalDebit.add(debitCol);
            totalCredit = totalCredit.add(creditCol);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("code", b.code());
            row.put("name", b.name());
            row.put("accountType", b.accountType());
            row.put("debit", debitCol);
            row.put("credit", creditCol);
            rows.add(row);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("asOf", asOf.toString());
        out.put("rows", rows);
        out.put("totalDebit", totalDebit);
        out.put("totalCredit", totalCredit);
        out.put("balanced", totalDebit.compareTo(totalCredit) == 0);
        return out;
    }

    // ---------------- Profit & Loss ----------------

    /** Income statement for the period [from, to]. */
    public Map<String, Object> profitAndLoss(LocalDate from, LocalDate to) {
        LocalDate f = from == null ? LocalDate.now().withDayOfMonth(1) : from;
        LocalDate t = to == null ? LocalDate.now() : to;
        return buildProfitAndLoss(fetch(f, t), f, t);
    }

    static Map<String, Object> buildProfitAndLoss(List<AccountBalance> balances, LocalDate from, LocalDate to) {
        List<Map<String, Object>> income = new ArrayList<>();
        List<Map<String, Object>> expense = new ArrayList<>();
        BigDecimal totalIncome = BigDecimal.ZERO;
        BigDecimal totalExpense = BigDecimal.ZERO;
        for (AccountBalance b : balances) {
            if ("INCOME".equals(b.accountType())) {
                BigDecimal amt = b.signedBalance();
                if (amt.signum() == 0) continue;
                totalIncome = totalIncome.add(amt);
                income.add(line(b, amt));
            } else if ("EXPENSE".equals(b.accountType())) {
                BigDecimal amt = b.signedBalance();
                if (amt.signum() == 0) continue;
                totalExpense = totalExpense.add(amt);
                expense.add(line(b, amt));
            }
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("fromDate", from.toString());
        out.put("toDate", to.toString());
        out.put("income", income);
        out.put("expense", expense);
        out.put("totalIncome", totalIncome);
        out.put("totalExpense", totalExpense);
        out.put("netSurplus", totalIncome.subtract(totalExpense));
        return out;
    }

    // ---------------- Balance sheet ----------------

    /** Balance sheet as of {@code asOf}. Current-period surplus (income − expense to date) folds into equity. */
    public Map<String, Object> balanceSheet(LocalDate asOf) {
        LocalDate to = asOf == null ? LocalDate.now() : asOf;
        return buildBalanceSheet(fetch(null, to), to);
    }

    static Map<String, Object> buildBalanceSheet(List<AccountBalance> balances, LocalDate asOf) {
        List<Map<String, Object>> assets = new ArrayList<>();
        List<Map<String, Object>> liabilities = new ArrayList<>();
        List<Map<String, Object>> equity = new ArrayList<>();
        BigDecimal totalAssets = BigDecimal.ZERO;
        BigDecimal totalLiabilities = BigDecimal.ZERO;
        BigDecimal totalEquityAccounts = BigDecimal.ZERO;
        BigDecimal income = BigDecimal.ZERO;
        BigDecimal expense = BigDecimal.ZERO;
        for (AccountBalance b : balances) {
            BigDecimal net = b.signedBalance();
            switch (b.accountType()) {
                case "ASSET" -> {
                    if (net.signum() != 0) { totalAssets = totalAssets.add(net); assets.add(line(b, net)); }
                }
                case "LIABILITY" -> {
                    if (net.signum() != 0) { totalLiabilities = totalLiabilities.add(net); liabilities.add(line(b, net)); }
                }
                case "EQUITY" -> {
                    if (net.signum() != 0) { totalEquityAccounts = totalEquityAccounts.add(net); equity.add(line(b, net)); }
                }
                case "INCOME" -> income = income.add(net);
                case "EXPENSE" -> expense = expense.add(net);
                default -> { /* ignore */ }
            }
        }
        // Retained / current-period surplus closes income & expense into equity so the sheet balances.
        BigDecimal surplus = income.subtract(expense);
        Map<String, Object> surplusLine = new LinkedHashMap<>();
        surplusLine.put("code", "RE");
        surplusLine.put("name", "Retained earnings / current surplus");
        surplusLine.put("amount", surplus);
        equity.add(surplusLine);
        BigDecimal totalEquity = totalEquityAccounts.add(surplus);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("asOf", asOf.toString());
        out.put("assets", assets);
        out.put("liabilities", liabilities);
        out.put("equity", equity);
        out.put("totalAssets", totalAssets);
        out.put("totalLiabilities", totalLiabilities);
        out.put("totalEquity", totalEquity);
        out.put("totalLiabilitiesAndEquity", totalLiabilities.add(totalEquity));
        out.put("balanced", totalAssets.compareTo(totalLiabilities.add(totalEquity)) == 0);
        return out;
    }

    private static Map<String, Object> line(AccountBalance b, BigDecimal amount) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("code", b.code());
        m.put("name", b.name());
        m.put("amount", amount);
        return m;
    }

    /**
     * Per-account debit/credit totals over [from?, to]. {@code from == null} means from the
     * beginning of time (used by trial balance and balance sheet, which are point-in-time).
     * The date filter lives inside the subquery so accounts with postings only outside the
     * window correctly contribute zero rather than their full lifetime total.
     */
    @SuppressWarnings("unchecked")
    private List<AccountBalance> fetch(LocalDate from, LocalDate to) {
        String tenant = TenantContext.get().getTenantId();
        StringBuilder sql = new StringBuilder()
                .append("SELECT a.code, a.name, a.account_type, ")
                .append("COALESCE(SUM(t.debit),0), COALESCE(SUM(t.credit),0) ")
                .append("FROM account a ")
                .append("LEFT JOIN ( ")
                .append("  SELECT jl.account_code, jl.debit, jl.credit ")
                .append("  FROM journal_line jl ")
                .append("  JOIN journal_entry je ON je.id = jl.journal_entry_id AND je.tenant_id = jl.tenant_id ")
                .append("  WHERE jl.tenant_id = :tenant ");
        if (from != null) {
            sql.append("  AND je.entry_date >= :from ");
        }
        sql.append("  AND je.entry_date <= :to ")
                .append(") t ON t.account_code = a.code ")
                .append("WHERE a.tenant_id = :tenant ")
                .append("GROUP BY a.code, a.name, a.account_type ")
                .append("ORDER BY a.code");

        Query q = em.createNativeQuery(sql.toString());
        q.setParameter("tenant", tenant);
        q.setParameter("to", to);
        if (from != null) {
            q.setParameter("from", from);
        }
        List<Object[]> rows = q.getResultList();
        List<AccountBalance> out = new ArrayList<>(rows.size());
        for (Object[] r : rows) {
            out.add(new AccountBalance(
                    (String) r[0], (String) r[1], (String) r[2],
                    toBig(r[3]), toBig(r[4])));
        }
        return out;
    }

    private static BigDecimal toBig(Object o) {
        if (o == null) return BigDecimal.ZERO;
        return o instanceof BigDecimal b ? b : new BigDecimal(o.toString());
    }
}
