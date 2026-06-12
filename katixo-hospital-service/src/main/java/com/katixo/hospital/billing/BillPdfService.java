package com.katixo.hospital.billing;

import com.katixo.hospital.common.exception.BusinessException;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Printable consolidated bill (A4 PDF): hospital charges grouped by service
 * category (GST-exempt healthcare services), attached ERP pharmacy invoices,
 * discount, payments and the grand total. Rendered from an HTML template via
 * openhtmltopdf — same approach as Katasticho's invoice PDFs.
 */
@Service
@RequiredArgsConstructor
public class BillPdfService {

    private final BillingService billingService;

    @Transactional(readOnly = true)
    public byte[] renderBillPdf(Long billId) {
        Map<String, Object> receipt = billingService.getReceipt(billId);
        List<PatientBillPayment> payments = billingService.listPayments(billId);
        String html = buildHtml(receipt, payments);
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            builder.withHtmlContent(html, null);
            builder.toStream(out);
            builder.run();
            return out.toByteArray();
        } catch (Exception e) {
            throw new BusinessException("PDF_RENDER_FAILED", "Could not render bill PDF: " + e.getMessage(), e);
        }
    }

    @SuppressWarnings("unchecked")
    private String buildHtml(Map<String, Object> receipt, List<PatientBillPayment> payments) {
        Map<String, List<Map<String, Object>>> chargesByCategory =
                (Map<String, List<Map<String, Object>>>) receipt.get("chargesByCategory");
        Map<String, BigDecimal> categoryTotals = (Map<String, BigDecimal>) receipt.get("categoryTotals");
        List<Map<String, Object>> erpInvoices = (List<Map<String, Object>>) receipt.get("erpInvoices");

        StringBuilder html = new StringBuilder();
        html.append("""
                <!DOCTYPE html>
                <html><head><style>
                  @page { size: A4; margin: 18mm 14mm; }
                  body { font-family: sans-serif; font-size: 11px; color: #1a1a1a; }
                  h1 { font-size: 17px; margin: 0; }
                  .muted { color: #666; }
                  table { width: 100%; border-collapse: collapse; margin: 6px 0; }
                  th { text-align: left; border-bottom: 1px solid #999; padding: 4px 2px; font-size: 10px;
                       text-transform: uppercase; color: #555; }
                  td { padding: 4px 2px; border-bottom: 1px solid #eee; }
                  .num { text-align: right; white-space: nowrap; }
                  .section { margin-top: 14px; font-weight: bold; font-size: 12px;
                             border-bottom: 2px solid #333; padding-bottom: 2px; }
                  .totals td { border: none; padding: 2px; }
                  .grand { font-size: 14px; font-weight: bold; border-top: 2px solid #333; }
                  .header { border-bottom: 2px solid #333; padding-bottom: 8px; margin-bottom: 10px; }
                  .footnote { margin-top: 16px; font-size: 9px; color: #777; }
                </style></head><body>
                """);

        html.append("<div class='header'><h1>HOSPITAL BILL</h1>")
                .append("<table><tr><td><b>Bill No:</b> ").append(esc(receipt.get("billNumber")))
                .append("<br/><b>Date:</b> ").append(esc(String.valueOf(receipt.get("billDate")).split("T")[0]))
                .append("<br/><b>Type:</b> ").append(esc(receipt.get("sourceType")))
                .append("</td><td class='num'><b>Patient:</b> ").append(esc(receipt.get("patientName")))
                .append("<br/><b>UHID:</b> ").append(esc(receipt.get("uhid")))
                .append("</td></tr></table></div>");

        html.append("<div class='section'>Hospital Charges (GST-exempt healthcare services)</div>")
                .append("<table><tr><th>Service</th><th class='num'>Qty</th>")
                .append("<th class='num'>Rate</th><th class='num'>Amount</th></tr>");
        if (chargesByCategory != null) {
            chargesByCategory.forEach((category, lines) -> {
                html.append("<tr><td colspan='4'><b>").append(esc(category)).append("</b></td></tr>");
                for (Map<String, Object> line : lines) {
                    html.append("<tr><td>").append(esc(line.get("serviceName")))
                            .append("</td><td class='num'>").append(esc(line.get("quantity")))
                            .append("</td><td class='num'>").append(money(line.get("rate")))
                            .append("</td><td class='num'>").append(money(line.get("amount")))
                            .append("</td></tr>");
                }
                html.append("<tr><td colspan='3' class='muted'>Subtotal — ").append(esc(category))
                        .append("</td><td class='num'>")
                        .append(money(categoryTotals == null ? null : categoryTotals.get(category)))
                        .append("</td></tr>");
            });
        }
        html.append("</table>");

        if (erpInvoices != null && !erpInvoices.isEmpty()) {
            html.append("<div class='section'>Pharmacy (billed by pharmacy with GST)</div>")
                    .append("<table><tr><th>Invoice / Receipt</th><th>Type</th><th class='num'>Amount</th></tr>");
            for (Map<String, Object> invoice : erpInvoices) {
                html.append("<tr><td>").append(esc(invoice.get("invoiceNumber")))
                        .append("</td><td>").append(esc(invoice.get("type")))
                        .append("</td><td class='num'>").append(money(invoice.get("amount")))
                        .append("</td></tr>");
            }
            html.append("</table>");
        }

        html.append("<table class='totals'>")
                .append(totalRow("Hospital Charges", receipt.get("chargesTotal"), false));
        BigDecimal discount = toBigDecimal(receipt.get("discountAmount"));
        if (discount.signum() > 0) {
            html.append(totalRow("Discount", "- " + money(discount), false));
        }
        html.append(totalRow("Hospital Net", receipt.get("hospitalNetAmount"), false))
                .append(totalRow("Pharmacy Total", receipt.get("erpInvoicesTotal"), false))
                .append(totalRow("GRAND TOTAL", receipt.get("grandTotal"), true));

        BigDecimal paid = payments.stream()
                .map(PatientBillPayment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (paid.signum() > 0) {
            html.append(totalRow("Paid", paid, false))
                    .append(totalRow("Balance", toBigDecimal(receipt.get("grandTotal")).subtract(paid), false));
        }
        html.append("</table>");

        if (!payments.isEmpty()) {
            html.append("<div class='section'>Payments</div>")
                    .append("<table><tr><th>Date</th><th>Mode</th><th>Reference</th><th class='num'>Amount</th></tr>");
            for (PatientBillPayment payment : payments) {
                html.append("<tr><td>")
                        .append(payment.getCreatedAt() == null ? "" : payment.getCreatedAt().toLocalDate())
                        .append("</td><td>").append(payment.getPaymentMode().name())
                        .append("</td><td>").append(esc(payment.getReference()))
                        .append("</td><td class='num'>").append(money(payment.getAmount()))
                        .append("</td></tr>");
            }
            html.append("</table>");
        }

        html.append("<div class='footnote'>Hospital services are exempt from GST. Pharmacy items are "
                + "invoiced separately by the pharmacy with applicable GST; pharmacy invoice copies are "
                + "available on request. This is a computer-generated bill.</div>");
        html.append("</body></html>");
        return html.toString();
    }

    private String totalRow(String label, Object value, boolean grand) {
        String labelCls = grand ? "grand" : "";
        String valueCls = grand ? "num grand" : "num";
        String rendered = value instanceof String s ? s : money(value);
        return "<tr><td class='" + labelCls + "' style='width:75%; text-align:right'>" + esc(label)
                + "</td><td class='" + valueCls + "'>" + rendered + "</td></tr>";
    }

    private String money(Object value) {
        return "Rs. " + toBigDecimal(value).setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        if (value instanceof BigDecimal bd) {
            return bd;
        }
        return new BigDecimal(String.valueOf(value));
    }

    private String esc(Object value) {
        if (value == null) {
            return "";
        }
        return String.valueOf(value)
                .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
