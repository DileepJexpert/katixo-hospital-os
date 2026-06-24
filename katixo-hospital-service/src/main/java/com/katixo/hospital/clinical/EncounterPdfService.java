package com.katixo.hospital.clinical;

import com.katixo.hospital.common.exception.BusinessException;
import com.katixo.hospital.patient.Patient;
import com.katixo.hospital.patient.PatientRepository;
import com.katixo.hospital.tenant.TenantContext;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.util.List;

/**
 * Printable encounter summary (A4 PDF): patient + encounter header, the SOAP
 * clinical notes, and the CPOE orders. Rendered via openhtmltopdf (same approach
 * as the bill / lab-report PDFs).
 */
@Service
@RequiredArgsConstructor
public class EncounterPdfService {

    private final ClinicalService clinicalService;
    private final CpoeService cpoeService;
    private final PatientRepository patientRepository;

    @Transactional(readOnly = true)
    public byte[] renderSummaryPdf(Long encounterId) {
        Encounter enc = clinicalService.getEncounter(encounterId);
        List<ClinicalNote> notes = clinicalService.listNotes(encounterId);
        List<ClinicalOrder> orders = cpoeService.listOrders(encounterId);
        var ctx = TenantContext.get();
        Patient patient = patientRepository.findByIdAndTenantIdAndBranchId(
                enc.getPatientId(), ctx.getTenantId(), Long.parseLong(ctx.getBranchId())).orElse(null);

        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            builder.withHtmlContent(buildHtml(enc, notes, orders, patient), null);
            builder.toStream(out);
            builder.run();
            return out.toByteArray();
        } catch (Exception e) {
            throw new BusinessException("PDF_RENDER_FAILED", "Could not render encounter PDF: " + e.getMessage(), e);
        }
    }

    private String buildHtml(Encounter enc, List<ClinicalNote> notes, List<ClinicalOrder> orders, Patient patient) {
        StringBuilder html = new StringBuilder();
        html.append("""
                <!DOCTYPE html>
                <html><head><style>
                  @page { size: A4; margin: 18mm 14mm; }
                  body { font-family: sans-serif; font-size: 11px; color: #1a1a1a; }
                  h1 { font-size: 17px; margin: 0; }
                  h2 { font-size: 12px; margin: 14px 0 4px; text-transform: uppercase; color: #444;
                       border-bottom: 1px solid #ccc; padding-bottom: 2px; }
                  table { width: 100%; border-collapse: collapse; margin: 6px 0; }
                  th { text-align: left; border-bottom: 1px solid #999; padding: 5px 3px; font-size: 10px;
                       text-transform: uppercase; color: #555; }
                  td { padding: 5px 3px; border-bottom: 1px solid #eee; vertical-align: top; }
                  .header { border-bottom: 2px solid #333; padding-bottom: 8px; margin-bottom: 10px; }
                  .note { margin-bottom: 8px; }
                  .note .meta { color: #777; font-size: 9px; margin-bottom: 2px; }
                  .footnote { margin-top: 20px; font-size: 9px; color: #777; }
                </style></head><body>
                """);

        html.append("<div class='header'><h1>ENCOUNTER SUMMARY</h1><table><tr>")
                .append("<td><b>Encounter:</b> ").append(esc(enc.getId()))
                .append("<br/><b>Type:</b> ").append(esc(enc.getEncounterType()))
                .append("<br/><b>Status:</b> ").append(esc(enc.getEncounterStatus()))
                .append("<br/><b>Started:</b> ").append(esc(fmt(enc.getStartedAt())))
                .append(enc.getClosedAt() != null ? "<br/><b>Closed:</b> " + esc(fmt(enc.getClosedAt())) : "")
                .append("</td><td><b>Patient:</b> ").append(esc(patient == null ? enc.getPatientId() : patient.getFullName()))
                .append("<br/><b>UHID:</b> ").append(esc(patient == null ? "" : patient.getUhid()))
                .append(enc.getChiefComplaint() != null ? "<br/><b>Chief complaint:</b> " + esc(enc.getChiefComplaint()) : "")
                .append("</td></tr></table></div>");

        html.append("<h2>Clinical notes</h2>");
        if (notes.isEmpty()) {
            html.append("<p>No notes recorded.</p>");
        } else {
            for (ClinicalNote n : notes) {
                html.append("<div class='note'><div class='meta'>")
                        .append(esc(n.getNoteType())).append(" · v").append(esc(n.getVersion()))
                        .append(n.getAuthorName() != null ? " · " + esc(n.getAuthorName()) : "")
                        .append("</div>");
                appendLine(html, "S", n.getSubjective());
                appendLine(html, "O", n.getObjective());
                appendLine(html, "A", n.getAssessment());
                appendLine(html, "P", n.getPlan());
                html.append("</div>");
            }
        }

        html.append("<h2>Orders</h2>");
        if (orders.isEmpty()) {
            html.append("<p>No orders placed.</p>");
        } else {
            html.append("<table><tr><th>Type</th><th>Order</th><th>Priority</th><th>Status</th><th>Routed</th></tr>");
            for (ClinicalOrder o : orders) {
                html.append("<tr><td>").append(esc(o.getOrderType()))
                        .append("</td><td>").append(esc(o.getName()))
                        .append(o.getInstructions() != null && !o.getInstructions().isBlank()
                                ? "<br/><span style='color:#777'>" + esc(o.getInstructions()) + "</span>" : "")
                        .append("</td><td>").append(esc(o.getPriority()))
                        .append("</td><td>").append(esc(o.getOrderStatus()))
                        .append("</td><td>").append(esc(o.getLinkedRefType() == null ? "—" : o.getLinkedRefType()))
                        .append("</td></tr>");
            }
            html.append("</table>");
        }

        html.append("<div class='footnote'>Computer-generated encounter summary. "
                + "For the complete medical record refer to the EMR.</div>");
        html.append("</body></html>");
        return html.toString();
    }

    private void appendLine(StringBuilder html, String label, String value) {
        if (value != null && !value.isBlank()) {
            html.append("<div><b>").append(label).append(":</b> ").append(esc(value)).append("</div>");
        }
    }

    private String fmt(Object dt) {
        return dt == null ? "" : String.valueOf(dt).replace("T", " ");
    }

    private String esc(Object value) {
        if (value == null) {
            return "";
        }
        return String.valueOf(value).replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
