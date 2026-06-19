package com.katixo.hospital.prescription;

import com.katixo.hospital.auth.StaffUserRepository;
import com.katixo.hospital.common.exception.BusinessException;
import com.katixo.hospital.opd.OPDVisit;
import com.katixo.hospital.opd.OPDVisitRepository;
import com.katixo.hospital.patient.Patient;
import com.katixo.hospital.patient.PatientRepository;
import com.katixo.hospital.tenant.TenantContext;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.Period;
import java.time.format.DateTimeFormatter;

/**
 * Printable prescription (A4 PDF, "Rx"): patient + doctor header, diagnosis and
 * the medicine table (drug, dosage, frequency, duration, quantity,
 * instructions). Rendered via openhtmltopdf, same approach as
 * {@code BillPdfService} / {@code ExpenseVoucherPdfService}.
 */
@Service
@RequiredArgsConstructor
public class PrescriptionPdfService {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm");

    private final PrescriptionRepository prescriptionRepository;
    private final PatientRepository patientRepository;
    private final OPDVisitRepository visitRepository;
    private final StaffUserRepository staffUserRepository;

    @Transactional(readOnly = true)
    public byte[] renderPrescriptionPdf(Long prescriptionId) {
        var ctx = TenantContext.get();
        Long branchId = Long.parseLong(ctx.getBranchId());
        Prescription rx = prescriptionRepository
                .findByIdAndTenantIdAndBranchId(prescriptionId, ctx.getTenantId(), branchId)
                .orElseThrow(() -> new BusinessException("PRESCRIPTION_NOT_FOUND",
                        "Prescription not found: " + prescriptionId));

        Patient patient = rx.getPatientId() == null ? null
                : patientRepository.findByIdAndTenantIdAndBranchId(rx.getPatientId(), ctx.getTenantId(), branchId)
                .orElse(null);
        OPDVisit visit = rx.getVisitId() == null ? null
                : visitRepository.findByIdAndTenantIdAndBranchId(rx.getVisitId(), ctx.getTenantId(), branchId)
                .orElse(null);
        String doctorName = rx.getDoctorId() == null ? null
                : staffUserRepository.findById(rx.getDoctorId()).map(s -> s.getName()).orElse(null);

        String html = buildHtml(rx, patient, visit, doctorName);
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            builder.withHtmlContent(html, null);
            builder.toStream(out);
            builder.run();
            return out.toByteArray();
        } catch (Exception ex) {
            throw new BusinessException("PDF_RENDER_FAILED",
                    "Could not render prescription: " + ex.getMessage(), ex);
        }
    }

    private String buildHtml(Prescription rx, Patient patient, OPDVisit visit, String doctorName) {
        StringBuilder html = new StringBuilder();
        html.append("""
                <!DOCTYPE html>
                <html><head><style>
                  @page { size: A4; margin: 18mm 14mm; }
                  body { font-family: sans-serif; font-size: 12px; color: #1a1a1a; }
                  h1 { font-size: 20px; margin: 0; }
                  .header { border-bottom: 2px solid #333; padding-bottom: 8px; margin-bottom: 12px; }
                  .meta { width: 100%; border-collapse: collapse; margin-bottom: 10px; }
                  .meta td { padding: 3px 4px; vertical-align: top; }
                  .meta .label { color: #555; }
                  .rx { font-size: 22px; font-weight: bold; margin: 8px 0 4px; }
                  table.items { width: 100%; border-collapse: collapse; margin-top: 4px; }
                  table.items th { text-align: left; border-bottom: 1px solid #333; padding: 6px 4px; font-size: 11px; }
                  table.items td { padding: 6px 4px; border-bottom: 1px solid #eee; vertical-align: top; }
                  .notes { margin-top: 12px; }
                  .footnote { margin-top: 24px; font-size: 9px; color: #777; }
                  .sign { margin-top: 48px; width: 100%; }
                  .sign td { border: none; padding-top: 30px; }
                </style></head><body>
                """);

        html.append("<div class='header'><h1>PRESCRIPTION</h1></div>");

        // Patient + doctor + visit meta
        String patientName = patient == null ? ("Patient #" + rx.getPatientId())
                : ((nz(patient.getFirstName()) + " " + nz(patient.getLastName())).trim());
        String age = (patient == null || patient.getDateOfBirth() == null) ? ""
                : Period.between(patient.getDateOfBirth(), LocalDate.now()).getYears() + " yrs";
        html.append("<table class='meta'>");
        html.append("<tr>")
                .append("<td class='label'>Rx No</td><td>").append(esc(rx.getPrescriptionNumber())).append("</td>")
                .append("<td class='label'>Date</td><td>")
                .append(rx.getCreatedAt() == null ? "" : rx.getCreatedAt().format(DATE)).append("</td>")
                .append("</tr>");
        html.append("<tr>")
                .append("<td class='label'>Patient</td><td>").append(esc(patientName)).append("</td>")
                .append("<td class='label'>UHID</td><td>")
                .append(esc(patient == null ? "" : patient.getUhid())).append("</td>")
                .append("</tr>");
        html.append("<tr>")
                .append("<td class='label'>Age</td><td>").append(esc(age)).append("</td>")
                .append("<td class='label'>Mobile</td><td>")
                .append(esc(patient == null ? "" : patient.getMobile())).append("</td>")
                .append("</tr>");
        html.append("<tr>")
                .append("<td class='label'>Doctor</td><td>").append(esc(doctorName)).append("</td>")
                .append("<td class='label'>Visit</td><td>")
                .append(esc(visit == null ? "" : visit.getVisitNumber())).append("</td>")
                .append("</tr>");
        if (visit != null && visit.getDiagnosis() != null && !visit.getDiagnosis().isBlank()) {
            html.append("<tr><td class='label'>Diagnosis</td><td colspan='3'>")
                    .append(esc(visit.getDiagnosis())).append("</td></tr>");
        }
        html.append("</table>");

        // Rx items
        html.append("<div class='rx'>&#8478;</div>");
        html.append("<table class='items'><thead><tr>")
                .append("<th style='width:4%'>#</th>")
                .append("<th style='width:34%'>Medicine</th>")
                .append("<th style='width:14%'>Dosage</th>")
                .append("<th style='width:14%'>Frequency</th>")
                .append("<th style='width:12%'>Duration</th>")
                .append("<th style='width:8%'>Qty</th>")
                .append("<th style='width:14%'>Instructions</th>")
                .append("</tr></thead><tbody>");
        int i = 1;
        for (PrescriptionItem it : rx.getItems()) {
            String duration = it.getDurationDays() == null ? "" : it.getDurationDays() + " days";
            html.append("<tr>")
                    .append("<td>").append(i++).append("</td>")
                    .append("<td>").append(esc(it.getMedicineName()))
                    .append(it.getMedicineCode() == null ? "" : " <span style='color:#888'>(" + esc(it.getMedicineCode()) + ")</span>")
                    .append("</td>")
                    .append("<td>").append(esc(it.getDosage())).append("</td>")
                    .append("<td>").append(esc(it.getFrequency())).append("</td>")
                    .append("<td>").append(esc(duration)).append("</td>")
                    .append("<td>").append(it.getQuantity() == null ? "" : it.getQuantity()).append("</td>")
                    .append("<td>").append(esc(it.getInstructions())).append("</td>")
                    .append("</tr>");
        }
        html.append("</tbody></table>");

        if (rx.getNotes() != null && !rx.getNotes().isBlank()) {
            html.append("<div class='notes'><b>Notes:</b> ").append(esc(rx.getNotes())).append("</div>");
        }

        html.append("<table class='sign'><tr><td></td><td style='text-align:right'>")
                .append(doctorName == null ? "Doctor's Signature" : esc(doctorName) + "<br/>Doctor's Signature")
                .append("</td></tr></table>");
        html.append("<div class='footnote'>This is a computer-generated prescription.</div>");
        html.append("</body></html>");
        return html.toString();
    }

    private String nz(String s) {
        return s == null ? "" : s;
    }

    private String esc(Object value) {
        if (value == null) {
            return "";
        }
        return String.valueOf(value).replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
