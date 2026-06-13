package com.katixo.hospital.lab;

import com.katixo.hospital.common.exception.BusinessException;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;

/**
 * Printable lab report (A4 PDF): patient header + each ordered test with its
 * result, unit, reference range and an abnormal flag. Rendered via openhtmltopdf
 * (same approach as the bill PDF).
 */
@Service
@RequiredArgsConstructor
public class LabReportPdfService {

    private final LabService labService;

    @Transactional(readOnly = true)
    public byte[] renderReportPdf(Long orderId) {
        Map<String, Object> data = labService.getReportData(orderId);
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            builder.withHtmlContent(buildHtml(data), null);
            builder.toStream(out);
            builder.run();
            return out.toByteArray();
        } catch (Exception e) {
            throw new BusinessException("PDF_RENDER_FAILED", "Could not render lab report PDF: " + e.getMessage(), e);
        }
    }

    @SuppressWarnings("unchecked")
    private String buildHtml(Map<String, Object> data) {
        List<Map<String, Object>> results = (List<Map<String, Object>>) data.get("results");

        StringBuilder html = new StringBuilder();
        html.append("""
                <!DOCTYPE html>
                <html><head><style>
                  @page { size: A4; margin: 18mm 14mm; }
                  body { font-family: sans-serif; font-size: 11px; color: #1a1a1a; }
                  h1 { font-size: 17px; margin: 0; }
                  table { width: 100%; border-collapse: collapse; margin: 8px 0; }
                  th { text-align: left; border-bottom: 1px solid #999; padding: 5px 3px; font-size: 10px;
                       text-transform: uppercase; color: #555; }
                  td { padding: 5px 3px; border-bottom: 1px solid #eee; }
                  .abn { color: #b00020; font-weight: bold; }
                  .header { border-bottom: 2px solid #333; padding-bottom: 8px; margin-bottom: 10px; }
                  .footnote { margin-top: 20px; font-size: 9px; color: #777; }
                </style></head><body>
                """);

        html.append("<div class='header'><h1>LABORATORY REPORT</h1>")
                .append("<table><tr><td><b>Report No:</b> ").append(esc(data.get("orderNumber")))
                .append("<br/><b>Date:</b> ").append(esc(String.valueOf(data.get("orderDate")).split("T")[0]))
                .append("<br/><b>Status:</b> ").append(esc(data.get("orderStatus")))
                .append("</td><td><b>Patient:</b> ").append(esc(data.get("patientName")))
                .append("<br/><b>UHID:</b> ").append(esc(data.get("uhid")))
                .append("</td></tr></table></div>");

        html.append("<table><tr><th>Test</th><th>Result</th><th>Unit</th>")
                .append("<th>Reference Range</th><th>Flag</th></tr>");
        for (Map<String, Object> r : results) {
            boolean abnormal = Boolean.TRUE.equals(r.get("isAbnormal"));
            html.append("<tr><td>").append(esc(r.get("testName")))
                    .append("</td><td").append(abnormal ? " class='abn'" : "").append(">")
                    .append(esc(r.get("result")))
                    .append("</td><td>").append(esc(r.get("unit")))
                    .append("</td><td>").append(esc(r.get("referenceRange")))
                    .append("</td><td").append(abnormal ? " class='abn'" : "").append(">")
                    .append(abnormal ? "ABNORMAL" : "")
                    .append("</td></tr>");
        }
        html.append("</table>");

        html.append("<div class='footnote'>This is a computer-generated laboratory report. "
                + "Results should be interpreted by a qualified physician in clinical context.</div>");
        html.append("</body></html>");
        return html.toString();
    }

    private String esc(Object value) {
        if (value == null) {
            return "";
        }
        return String.valueOf(value).replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
