package com.katixo.hospital.report;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises the pure statement-assembly logic (no DB): trial balance, P&L and
 * balance sheet derived from a small hand-built set of account balances.
 */
class FinancialReportServiceTest {

    private static FinancialReportService.AccountBalance bal(String code, String name, String type,
                                                             String debit, String credit) {
        return new FinancialReportService.AccountBalance(code, name, type,
                new BigDecimal(debit), new BigDecimal(credit));
    }

    /**
     * A self-consistent set of postings:
     *  - Cash 1010 (ASSET) debit 70000
     *  - Patient AR 1100 (ASSET) debit 30000
     *  - Trade Payables 2010 (LIABILITY) credit 20000
     *  - Service income 4020 (INCOME) credit 100000
     *  - Salaries 5100 (EXPENSE) debit 20000
     * Assets 100000 = Liabilities 20000 + surplus (100000-20000) 80000. Balanced.
     */
    private static List<FinancialReportService.AccountBalance> sample() {
        return List.of(
                bal("1010", "Cash", "ASSET", "70000", "0"),
                bal("1100", "Patient AR", "ASSET", "30000", "0"),
                bal("2010", "Trade Payables", "LIABILITY", "0", "20000"),
                bal("4020", "Hospital Service Income", "INCOME", "0", "100000"),
                bal("5100", "Salaries", "EXPENSE", "20000", "0"));
    }

    @Test
    void trialBalanceColumnsAreEqualAndBalanced() {
        Map<String, Object> tb = FinancialReportService.buildTrialBalance(sample(), LocalDate.of(2026, 6, 30));
        assertEquals(new BigDecimal("120000"), tb.get("totalDebit"));   // 70000 + 30000 + 20000
        assertEquals(new BigDecimal("120000"), tb.get("totalCredit"));  // 20000 + 100000
        assertEquals(Boolean.TRUE, tb.get("balanced"));
    }

    @Test
    void trialBalanceSkipsZeroBalances() {
        var withZero = new java.util.ArrayList<>(sample());
        withZero.add(bal("1020", "Bank", "ASSET", "5000", "5000")); // nets to zero
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>)
                FinancialReportService.buildTrialBalance(withZero, LocalDate.now()).get("rows");
        assertTrue(rows.stream().noneMatch(r -> "1020".equals(r.get("code"))));
    }

    @Test
    void profitAndLossNetsIncomeMinusExpense() {
        Map<String, Object> pl = FinancialReportService.buildProfitAndLoss(
                sample(), LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30));
        assertEquals(new BigDecimal("100000"), pl.get("totalIncome"));
        assertEquals(new BigDecimal("20000"), pl.get("totalExpense"));
        assertEquals(new BigDecimal("80000"), pl.get("netSurplus"));
    }

    @Test
    void balanceSheetFoldsSurplusIntoEquityAndBalances() {
        Map<String, Object> bs = FinancialReportService.buildBalanceSheet(sample(), LocalDate.of(2026, 6, 30));
        assertEquals(new BigDecimal("100000"), bs.get("totalAssets"));
        assertEquals(new BigDecimal("20000"), bs.get("totalLiabilities"));
        assertEquals(new BigDecimal("80000"), bs.get("totalEquity"));   // surplus folded in
        assertEquals(bs.get("totalAssets"), bs.get("totalLiabilitiesAndEquity"));
        assertEquals(Boolean.TRUE, bs.get("balanced"));
    }
}
