package com.katixo.hospital.payroll;

import com.katixo.hospital.common.exception.BusinessException;
import com.katixo.hospital.tenant.TenantContext;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Month;
import java.time.format.TextStyle;
import java.util.Locale;

/**
 * Printable salary slip (A4 PDF) for one employee in a payroll run: earnings
 * (basic/HRA/allowances), statutory deductions (PF/ESI/PT/TDS) and net pay,
 * plus employer PF/ESI for the record. openhtmltopdf, same as the bill/voucher PDFs.
 */
@Service
@RequiredArgsConstructor
public class PayslipPdfService {

    private final PayrollService payrollService;
    private final EmployeeRepository employeeRepository;

    @Transactional(readOnly = true)
    public byte[] renderPayslipPdf(Long runId, Long employeeId) {
        PayrollRun run = payrollService.getRun(runId);
        Payslip slip = payrollService.getPayslip(runId, employeeId);
        var ctx = TenantContext.get();
        Employee emp = employeeRepository.findByIdAndTenantIdAndBranchId(
                employeeId, ctx.getTenantId(), Long.parseLong(ctx.getBranchId())).orElse(null);
        String html = buildHtml(run, slip, emp);
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            builder.withHtmlContent(html, null);
            builder.toStream(out);
            builder.run();
            return out.toByteArray();
        } catch (Exception ex) {
            throw new BusinessException("PDF_RENDER_FAILED", "Could not render payslip: " + ex.getMessage(), ex);
        }
    }

    private String buildHtml(PayrollRun run, Payslip s, Employee e) {
        String period = Month.of(run.getPeriodMonth()).getDisplayName(TextStyle.FULL, Locale.ENGLISH)
                + " " + run.getPeriodYear();
        StringBuilder html = new StringBuilder();
        html.append("""
                <!DOCTYPE html>
                <html><head><style>
                  @page { size: A4; margin: 18mm 14mm; }
                  body { font-family: sans-serif; font-size: 11px; color: #1a1a1a; }
                  h1 { font-size: 17px; margin: 0; }
                  .header { border-bottom: 2px solid #333; padding-bottom: 8px; margin-bottom: 12px; }
                  .meta td { padding: 3px 4px; }
                  .meta .label { color: #666; }
                  table.cols { width: 100%; border-collapse: collapse; margin-top: 8px; }
                  table.cols > tr > td { width: 50%; vertical-align: top; padding: 0 6px; }
                  table.ledger { width: 100%; border-collapse: collapse; }
                  table.ledger th { text-align: left; border-bottom: 1px solid #999; padding: 4px 2px;
                       font-size: 10px; text-transform: uppercase; color: #555; }
                  table.ledger td { padding: 4px 2px; border-bottom: 1px solid #eee; }
                  .num { text-align: right; white-space: nowrap; }
                  .sub { font-weight: bold; border-top: 1px solid #333; }
                  .net { font-size: 14px; font-weight: bold; border-top: 2px solid #333; margin-top: 10px; }
                  .footnote { margin-top: 20px; font-size: 9px; color: #777; }
                </style></head><body>
                """);
        html.append("<div class='header'><h1>SALARY SLIP</h1>")
                .append("<div>Pay period: <b>").append(esc(period)).append("</b></div></div>");

        html.append("<table class='meta'>")
                .append("<tr><td class='label'>Employee</td><td><b>").append(esc(s.getEmployeeName()))
                .append("</b></td><td class='label'>Code</td><td>")
                .append(esc(e == null ? "" : e.getEmployeeCode())).append("</td></tr>")
                .append("<tr><td class='label'>Designation</td><td>")
                .append(esc(e == null ? "" : e.getDesignation()))
                .append("</td><td class='label'>Department</td><td>")
                .append(esc(e == null ? "" : e.getDepartment())).append("</td></tr>")
                .append("</table>");

        html.append("<table class='cols'><tr><td>");
        html.append("<table class='ledger'><tr><th>Earnings</th><th class='num'>Amount</th></tr>")
                .append(line("Basic", s.getBasic()))
                .append(line("HRA", s.getHra()))
                .append(line("Other Allowances", s.getAllowances()))
                .append("<tr class='sub'><td>Gross</td><td class='num'>").append(money(s.getGross()))
                .append("</td></tr></table>");
        html.append("</td><td>");
        html.append("<table class='ledger'><tr><th>Deductions</th><th class='num'>Amount</th></tr>")
                .append(line("PF (employee)", s.getPfEmployee()))
                .append(line("ESI (employee)", s.getEsiEmployee()))
                .append(line("Professional Tax", s.getProfessionalTax()))
                .append(line("TDS", s.getTds()))
                .append("<tr class='sub'><td>Total Deductions</td><td class='num'>")
                .append(money(s.getTotalDeductions())).append("</td></tr></table>");
        html.append("</td></tr></table>");

        html.append("<table class='ledger net'><tr><td style='width:75%'>NET PAY</td><td class='num'>")
                .append(money(s.getNetPay())).append("</td></tr></table>");

        html.append("<div class='footnote'>Employer contributions (not deducted from salary): PF ")
                .append(money(s.getPfEmployer())).append(", ESI ").append(money(s.getEsiEmployer()))
                .append(". This is a computer-generated salary slip.</div>");
        html.append("</body></html>");
        return html.toString();
    }

    private String line(String label, BigDecimal amount) {
        return "<tr><td>" + esc(label) + "</td><td class='num'>" + money(amount) + "</td></tr>";
    }

    private String money(BigDecimal value) {
        BigDecimal v = value == null ? BigDecimal.ZERO : value;
        return "Rs. " + v.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private String esc(Object value) {
        if (value == null) {
            return "";
        }
        return String.valueOf(value).replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
