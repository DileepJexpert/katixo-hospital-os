package com.katixo.hospital.discharge;

import com.katixo.hospital.common.exception.BusinessException;
import com.katixo.hospital.ipd.IPDAdmission;
import com.katixo.hospital.ipd.IPDAdmissionRepository;
import com.katixo.hospital.patient.Patient;
import com.katixo.hospital.patient.PatientRepository;
import com.katixo.hospital.tenant.TenantContext;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;

/**
 * Printable clinical discharge summary (A4 PDF).
 * DRAFT summaries carry a watermark banner. SIGNED summaries are clean.
 * Rendered via openhtmltopdf, following the same approach as {@code CertificatePdfService}.
 */
@Service
@RequiredArgsConstructor
public class DischargeSummaryPdfService {

    private final DischargeSummaryRepository repository;
    private final PatientRepository patientRepository;
    private final IPDAdmissionRepository admissionRepository;

    @Transactional(readOnly = true)
    public byte[] renderPdf(Long summaryId) {
        var ctx = TenantContext.get();
        Long branchId = Long.parseLong(ctx.getBranchId());

        DischargeSummary ds = repository.findByIdAndTenantIdAndBranchId(
                        summaryId, ctx.getTenantId(), branchId)
                .orElseThrow(() -> new BusinessException("DSUM_NOT_FOUND",
                        "Discharge summary not found: " + summaryId));

        IPDAdmission admission = admissionRepository.findByIdAndTenantIdAndBranchId(
                ds.getAdmissionId(), ctx.getTenantId(), branchId).orElse(null);

        Patient patient = null;
        if (admission != null) {
            patient = patientRepository.findByIdAndTenantIdAndBranchId(
                    admission.getPatientId(), ctx.getTenantId(), branchId).orElse(null);
        }

        String html = buildHtml(ds, admission, patient);
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            builder.withHtmlContent(html, null);
            builder.toStream(out);
            builder.run();
            return out.toByteArray();
        } catch (Exception ex) {
            throw new BusinessException("PDF_RENDER_FAILED",
                    "Could not render discharge summary: " + ex.getMessage(), ex);
        }
    }

    private String buildHtml(DischargeSummary ds, IPDAdmission admission, Patient patient) {
        boolean draft = ds.getSummaryStatus() == DischargeSummary.SummaryStatus.DRAFT;
        String patientName = patient == null ? ("Patient #" + (admission == null ? "?" : admission.getPatientId())) : patient.getFullName();
        String uhid = patient == null ? "" : patient.getUhid();
        String admissionNumber = admission == null ? ("Admission #" + ds.getAdmissionId()) : admission.getAdmissionNumber();
        String admittedAt = admission == null ? "—" : fmtDateTime(admission.getAdmittedAt());
        String dischargedAt = (admission == null || admission.getDischargedAt() == null) ? "—" : fmtDateTime(admission.getDischargedAt());

        StringBuilder html = new StringBuilder();
        html.append("""
                <!DOCTYPE html>
                <html><head><style>
                  @page { size: A4; margin: 22mm 18mm; }
                  body { font-family: serif; font-size: 12px; color: #1a1a1a; }
                  .header { text-align: center; border-bottom: 2px solid #333; padding-bottom: 10px; margin-bottom: 16px; }
                  h1 { font-size: 20px; margin: 0; letter-spacing: 1px; }
                  .sub { color: #555; font-size: 11px; margin-top: 4px; }
                  .draft-banner { color: #b00; border: 2px solid #b00; padding: 6px; text-align: center;
                                  font-weight: bold; letter-spacing: 2px; margin-bottom: 14px; font-size: 13px; }
                  .meta { width: 100%; border-collapse: collapse; margin-bottom: 14px; }
                  .meta td { padding: 4px 6px; font-size: 11px; vertical-align: top; }
                  .meta .label { color: #555; font-weight: bold; white-space: nowrap; }
                  .section-title { font-size: 13px; font-weight: bold; color: #1a1a1a;
                                   border-bottom: 1px solid #ccc; padding-bottom: 3px;
                                   margin-top: 14px; margin-bottom: 6px; }
                  .section-body { line-height: 1.65; white-space: pre-wrap; margin: 0 0 6px 0; }
                  .sign { margin-top: 50px; width: 100%; border-collapse: collapse; }
                  .sign td { padding-top: 26px; font-size: 12px; }
                  .footnote { margin-top: 24px; font-size: 9px; color: #777; border-top: 1px solid #eee; padding-top: 6px; }
                </style></head><body>
                """);

        if (draft) {
            html.append("<div class='draft-banner'>DRAFT — NOT SIGNED</div>");
        }

        html.append("<div class='header'><h1>DISCHARGE SUMMARY</h1>")
                .append("<div class='sub'>").append(esc(patientName))
                .append(" &nbsp;|&nbsp; UHID: ").append(esc(uhid)).append("</div></div>");

        // Meta table
        html.append("<table class='meta'>")
                .append("<tr>")
                .append("<td class='label'>Summary No</td><td>").append(esc(ds.getSummaryNumber())).append("</td>")
                .append("<td class='label'>Date Signed</td><td>")
                .append(ds.getSignedAt() == null ? "—" : esc(fmtDateTime(ds.getSignedAt()))).append("</td>")
                .append("</tr>")
                .append("<tr>")
                .append("<td class='label'>Admission No</td><td>").append(esc(admissionNumber)).append("</td>")
                .append("<td class='label'>Admitted</td><td>").append(esc(admittedAt)).append("</td>")
                .append("</tr>")
                .append("<tr>")
                .append("<td class='label'>Condition at Discharge</td><td>")
                .append(ds.getConditionAtDischarge() == null ? "—" : esc(ds.getConditionAtDischarge().name()))
                .append("</td>")
                .append("<td class='label'>Discharged</td><td>").append(esc(dischargedAt)).append("</td>")
                .append("</tr>")
                .append("</table>");

        // Clinical sections
        section(html, "Final Diagnosis", ds.getFinalDiagnosis());
        section(html, "Course in Hospital", ds.getCourseInHospital());
        section(html, "Procedures Performed", ds.getProceduresPerformed());
        section(html, "Follow-up Instructions", ds.getFollowUpInstructions());
        section(html, "Medications at Discharge", ds.getMedicationsAtDischarge());
        section(html, "Activity &amp; Diet Advice", mergeAdvice(ds.getActivityRestrictions(), ds.getDietAdvice()));

        // Signature block
        String doctor = (ds.getSignedByDoctorName() == null || ds.getSignedByDoctorName().isBlank())
                ? "Authorised Medical Officer" : ("Dr " + ds.getSignedByDoctorName());
        html.append("<table class='sign'><tr><td></td><td style='text-align:right'>")
                .append(esc(doctor)).append("<br/>Signature &amp; Seal</td></tr></table>");

        html.append("<div class='footnote'>This is a computer-generated discharge summary. "
                + "Verify authenticity with the issuing hospital quoting the summary number.</div>");
        html.append("</body></html>");
        return html.toString();
    }

    private void section(StringBuilder html, String title, String body) {
        if (body == null || body.isBlank()) return;
        html.append("<div class='section-title'>").append(title).append("</div>")
                .append("<div class='section-body'>").append(esc(body)).append("</div>");
    }

    private String mergeAdvice(String activity, String diet) {
        if ((activity == null || activity.isBlank()) && (diet == null || diet.isBlank())) return null;
        if (activity == null || activity.isBlank()) return diet;
        if (diet == null || diet.isBlank()) return activity;
        return activity + "\n\nDiet Advice:\n" + diet;
    }

    private String fmtDateTime(java.time.LocalDateTime dt) {
        if (dt == null) return "—";
        return dt.toLocalDate() + " " + dt.toLocalTime().withSecond(0).withNano(0);
    }

    private String esc(Object value) {
        if (value == null) return "";
        return String.valueOf(value).replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
