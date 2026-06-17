package com.katixo.hospital.certificate;

import com.katixo.hospital.common.exception.BusinessException;
import com.katixo.hospital.patient.Patient;
import com.katixo.hospital.patient.PatientRepository;
import com.katixo.hospital.tenant.TenantContext;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;

/**
 * Printable medical certificate (A4 PDF): the issued document a patient receives.
 * Rendered via openhtmltopdf, same approach as {@code ExpenseVoucherPdfService}.
 * A REVOKED certificate is watermarked so a stale printout can't be passed off as valid.
 */
@Service
@RequiredArgsConstructor
public class CertificatePdfService {

    private final CertificateRepository certificateRepository;
    private final PatientRepository patientRepository;

    @Transactional(readOnly = true)
    public byte[] renderPdf(Long certificateId) {
        var ctx = TenantContext.get();
        Long branchId = Long.parseLong(ctx.getBranchId());
        Certificate c = certificateRepository.findByIdAndTenantIdAndBranchId(
                        certificateId, ctx.getTenantId(), branchId)
                .orElseThrow(() -> new BusinessException("CERT_NOT_FOUND", "Certificate not found: " + certificateId));
        Patient patient = patientRepository.findByIdAndTenantIdAndBranchId(
                c.getPatientId(), ctx.getTenantId(), branchId).orElse(null);

        String html = buildHtml(c, patient);
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            builder.withHtmlContent(html, null);
            builder.toStream(out);
            builder.run();
            return out.toByteArray();
        } catch (Exception ex) {
            throw new BusinessException("PDF_RENDER_FAILED", "Could not render certificate: " + ex.getMessage(), ex);
        }
    }

    private String buildHtml(Certificate c, Patient patient) {
        boolean revoked = c.getCertificateStatus() == Certificate.CertificateStatus.REVOKED;
        String patientName = patient == null ? ("Patient #" + c.getPatientId()) : patient.getFullName();
        String uhid = patient == null ? "" : patient.getUhid();

        StringBuilder html = new StringBuilder();
        html.append("""
                <!DOCTYPE html>
                <html><head><style>
                  @page { size: A4; margin: 22mm 18mm; }
                  body { font-family: serif; font-size: 13px; color: #1a1a1a; }
                  .header { text-align: center; border-bottom: 2px solid #333; padding-bottom: 10px; margin-bottom: 18px; }
                  h1 { font-size: 20px; margin: 0; letter-spacing: 1px; }
                  .sub { color: #555; font-size: 11px; margin-top: 4px; }
                  .meta { width: 100%; border-collapse: collapse; margin-bottom: 16px; }
                  .meta td { padding: 4px 4px; font-size: 12px; }
                  .label { color: #555; }
                  .body { line-height: 1.7; margin: 18px 0; white-space: pre-wrap; }
                  .validity { margin-top: 10px; font-size: 12px; }
                  .sign { margin-top: 60px; width: 100%; }
                  .sign td { padding-top: 28px; font-size: 12px; }
                  .footnote { margin-top: 30px; font-size: 9px; color: #777; }
                  .revoked { color: #b00; border: 2px solid #b00; padding: 6px; text-align: center;
                             font-weight: bold; letter-spacing: 2px; margin-bottom: 14px; }
                </style></head><body>
                """);
        if (revoked) {
            html.append("<div class='revoked'>REVOKED — NOT VALID</div>");
        }
        html.append("<div class='header'><h1>").append(esc(c.getTitle().toUpperCase())).append("</h1>")
                .append("<div class='sub'>").append(esc(c.getCertificateType().name())).append(" CERTIFICATE</div></div>");

        html.append("<table class='meta'>")
                .append("<tr><td class='label'>Certificate No</td><td>").append(esc(c.getCertificateNumber()))
                .append("</td><td class='label'>Date</td><td>").append(esc(c.getIssueDate())).append("</td></tr>")
                .append("<tr><td class='label'>Patient</td><td>").append(esc(patientName))
                .append("</td><td class='label'>UHID</td><td>").append(esc(uhid)).append("</td></tr>")
                .append("</table>");

        html.append("<div class='body'>").append(esc(c.getBodyText())).append("</div>");

        if (c.getValidFrom() != null || c.getValidTo() != null) {
            html.append("<div class='validity'><b>Validity:</b> ")
                    .append(c.getValidFrom() == null ? "—" : esc(c.getValidFrom()))
                    .append(" to ")
                    .append(c.getValidTo() == null ? "—" : esc(c.getValidTo()))
                    .append("</div>");
        }
        if (c.getRemarks() != null && !c.getRemarks().isBlank()) {
            html.append("<div class='validity'><b>Remarks:</b> ").append(esc(c.getRemarks())).append("</div>");
        }
        if (revoked && c.getRevokedReason() != null) {
            html.append("<div class='validity'><b>Revoked:</b> ").append(esc(c.getRevokedReason())).append("</div>");
        }

        String doctor = c.getIssuingDoctorName() == null || c.getIssuingDoctorName().isBlank()
                ? "Authorised Medical Officer" : ("Dr " + c.getIssuingDoctorName());
        html.append("<table class='sign'><tr><td></td><td style='text-align:right'>")
                .append(esc(doctor)).append("<br/>Signature &amp; Seal</td></tr></table>");

        html.append("<div class='footnote'>This is a computer-generated certificate. "
                + "Verify authenticity with the issuing hospital quoting the certificate number.</div>");
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
