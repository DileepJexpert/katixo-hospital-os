package com.katixo.hospital.expense;

import com.katixo.hospital.common.exception.BusinessException;
import com.katixo.hospital.tenant.TenantContext;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Printable expense voucher (A4 PDF): the document/proof for a single recorded
 * expense — payee, category, amount, payment status and the posted journal
 * reference. Rendered via openhtmltopdf, same approach as {@code BillPdfService}.
 */
@Service
@RequiredArgsConstructor
public class ExpenseVoucherPdfService {

    private final ExpenseRepository expenseRepository;

    @Transactional(readOnly = true)
    public byte[] renderVoucherPdf(Long expenseId) {
        var ctx = TenantContext.get();
        Expense e = expenseRepository.findByIdAndTenantIdAndBranchId(
                        expenseId, ctx.getTenantId(), Long.parseLong(ctx.getBranchId()))
                .orElseThrow(() -> new BusinessException("EXPENSE_NOT_FOUND", "Expense not found: " + expenseId));
        String html = buildHtml(e);
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            builder.withHtmlContent(html, null);
            builder.toStream(out);
            builder.run();
            return out.toByteArray();
        } catch (Exception ex) {
            throw new BusinessException("PDF_RENDER_FAILED", "Could not render expense voucher: " + ex.getMessage(), ex);
        }
    }

    private String buildHtml(Expense e) {
        String status = e.isReversed() ? "REVERSED" : (e.isPaid() ? "PAID" : "UNPAID (in Trade Payables)");
        StringBuilder html = new StringBuilder();
        html.append("""
                <!DOCTYPE html>
                <html><head><style>
                  @page { size: A4; margin: 18mm 14mm; }
                  body { font-family: sans-serif; font-size: 12px; color: #1a1a1a; }
                  h1 { font-size: 18px; margin: 0; }
                  .header { border-bottom: 2px solid #333; padding-bottom: 8px; margin-bottom: 14px; }
                  table { width: 100%; border-collapse: collapse; margin: 6px 0; }
                  td { padding: 6px 4px; border-bottom: 1px solid #eee; }
                  .label { color: #555; width: 35%; }
                  .amount { font-size: 20px; font-weight: bold; }
                  .footnote { margin-top: 24px; font-size: 9px; color: #777; }
                  .sign { margin-top: 48px; }
                  .sign td { border: none; padding-top: 30px; }
                </style></head><body>
                """);
        html.append("<div class='header'><h1>EXPENSE VOUCHER</h1></div>");
        html.append("<table>")
                .append(row("Voucher No", e.getExpenseNumber()))
                .append(row("Date", String.valueOf(e.getExpenseDate())))
                .append(row("Category", e.getCategory().name()))
                .append(row("Paid To", e.getPayeeName()))
                .append(row("Payment Mode", e.getPaymentMode().name()))
                .append(row("Reference", e.getReference()))
                .append(row("Status", status))
                .append(row("Journal Entry", e.getJournalNumber()));
        if (e.isPaid() && e.getPaymentMode() == Expense.PaymentMode.CREDIT) {
            html.append(row("Paid On", String.valueOf(e.getPaidDate())))
                    .append(row("Paid Via", e.getPaidMode() == null ? "" : e.getPaidMode().name()))
                    .append(row("Payment Journal", e.getPaidJournalNumber()));
        }
        if (e.getNotes() != null && !e.getNotes().isBlank()) {
            html.append(row("Notes", e.getNotes()));
        }
        html.append("</table>");
        html.append("<table><tr><td class='label'>Amount</td><td class='amount'>")
                .append(money(e.getAmount())).append("</td></tr></table>");
        html.append("<table class='sign'><tr><td>Prepared by</td><td style='text-align:right'>Authorised Signatory</td></tr></table>");
        html.append("<div class='footnote'>Hospital operating expenses are booked inclusive of GST "
                + "(no input tax credit is available against GST-exempt healthcare supplies). "
                + "This is a computer-generated voucher.</div>");
        html.append("</body></html>");
        return html.toString();
    }

    private String row(String label, String value) {
        return "<tr><td class='label'>" + esc(label) + "</td><td>" + esc(value) + "</td></tr>";
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
