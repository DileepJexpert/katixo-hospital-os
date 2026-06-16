package com.katixo.hospital.payroll;

import com.katixo.hospital.common.dto.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** HR / payroll: employee master + monthly payroll runs (DRAFT → APPROVED → PAID). */
@RestController
@RequestMapping("/api/v1/payroll")
@RequiredArgsConstructor
public class PayrollController {

    private final PayrollService payrollService;
    private final PayslipPdfService payslipPdfService;

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EmployeeRequest {
        @NotBlank
        private String name;
        private String designation;
        private String department;
        private LocalDate joiningDate;
        private BigDecimal basicSalary;
        private BigDecimal hra;
        private BigDecimal otherAllowances;
        private Boolean pfApplicable;
        private Boolean esiApplicable;
        private BigDecimal professionalTax;
        private BigDecimal monthlyTds;
        private String bankAccount;
    }

    @PostMapping("/employees")
    @PreAuthorize("hasAnyRole('ADMIN')")
    public ResponseEntity<ApiResponse<Object>> createEmployee(@Valid @RequestBody EmployeeRequest req) {
        Employee e = new Employee();
        e.setName(req.getName());
        e.setDesignation(req.getDesignation());
        e.setDepartment(req.getDepartment());
        e.setJoiningDate(req.getJoiningDate());
        e.setBasicSalary(nz(req.getBasicSalary()));
        e.setHra(nz(req.getHra()));
        e.setOtherAllowances(nz(req.getOtherAllowances()));
        e.setPfApplicable(req.getPfApplicable() == null || req.getPfApplicable());
        e.setEsiApplicable(req.getEsiApplicable() == null || req.getEsiApplicable());
        e.setProfessionalTax(nz(req.getProfessionalTax()));
        e.setMonthlyTds(nz(req.getMonthlyTds()));
        e.setBankAccount(req.getBankAccount());
        return respond(employeeView(payrollService.createEmployee(e)), "Employee created", HttpStatus.CREATED);
    }

    @GetMapping("/employees")
    @PreAuthorize("hasAnyRole('ADMIN')")
    public ResponseEntity<ApiResponse<Object>> listEmployees() {
        return respond(payrollService.listEmployees().stream().map(this::employeeView).toList(),
                "Employees", HttpStatus.OK);
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateRunRequest {
        @NotNull
        private Integer year;
        @NotNull
        private Integer month;
    }

    @PostMapping("/runs")
    @PreAuthorize("hasAnyRole('ADMIN')")
    public ResponseEntity<ApiResponse<Object>> createRun(@Valid @RequestBody CreateRunRequest req) {
        return respond(runView(payrollService.createRun(req.getYear(), req.getMonth())),
                "Payroll run created", HttpStatus.CREATED);
    }

    @PostMapping("/runs/{id}/approve")
    @PreAuthorize("hasAnyRole('ADMIN')")
    public ResponseEntity<ApiResponse<Object>> approveRun(@PathVariable Long id) {
        return respond(runView(payrollService.approveRun(id)), "Payroll approved & posted", HttpStatus.OK);
    }

    @PostMapping("/runs/{id}/pay")
    @PreAuthorize("hasAnyRole('ADMIN')")
    public ResponseEntity<ApiResponse<Object>> payRun(@PathVariable Long id) {
        return respond(runView(payrollService.payRun(id)), "Payroll paid", HttpStatus.OK);
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PayStatutoryRequest {
        private Boolean fromCash;
        private LocalDate paidDate;
    }

    @PostMapping("/runs/{id}/pay-statutory")
    @PreAuthorize("hasAnyRole('ADMIN')")
    public ResponseEntity<ApiResponse<Object>> payStatutory(@PathVariable Long id,
                                                            @RequestBody(required = false) PayStatutoryRequest req) {
        boolean fromCash = req != null && Boolean.TRUE.equals(req.getFromCash());
        LocalDate paidDate = req == null ? null : req.getPaidDate();
        return respond(runView(payrollService.payStatutory(id, fromCash, paidDate)),
                "Statutory dues remitted", HttpStatus.OK);
    }

    @GetMapping("/runs/{id}/payslips/{employeeId}.pdf")
    @PreAuthorize("hasAnyRole('ADMIN')")
    public ResponseEntity<byte[]> payslipPdf(@PathVariable Long id, @PathVariable Long employeeId) {
        byte[] pdf = payslipPdfService.renderPayslipPdf(id, employeeId);
        return ResponseEntity.ok()
                .header("Content-Type", "application/pdf")
                .header("Content-Disposition", "inline; filename=payslip-" + id + "-" + employeeId + ".pdf")
                .body(pdf);
    }

    @GetMapping("/runs")
    @PreAuthorize("hasAnyRole('ADMIN')")
    public ResponseEntity<ApiResponse<Object>> listRuns() {
        return respond(payrollService.listRuns().stream().map(this::runView).toList(), "Payroll runs", HttpStatus.OK);
    }

    @GetMapping("/runs/{id}")
    @PreAuthorize("hasAnyRole('ADMIN')")
    public ResponseEntity<ApiResponse<Object>> getRun(@PathVariable Long id) {
        Map<String, Object> view = runView(payrollService.getRun(id));
        view.put("payslips", payrollService.getPayslips(id).stream().map(this::slipView).toList());
        return respond(view, "Payroll run", HttpStatus.OK);
    }

    private Map<String, Object> employeeView(Employee e) {
        Map<String, Object> v = new java.util.LinkedHashMap<>();
        v.put("id", e.getId());
        v.put("employeeCode", e.getEmployeeCode());
        v.put("name", e.getName());
        v.put("designation", e.getDesignation());
        v.put("department", e.getDepartment());
        v.put("basicSalary", e.getBasicSalary());
        v.put("hra", e.getHra());
        v.put("otherAllowances", e.getOtherAllowances());
        v.put("pfApplicable", e.isPfApplicable());
        v.put("esiApplicable", e.isEsiApplicable());
        v.put("active", e.isActive());
        return v;
    }

    private Map<String, Object> runView(PayrollRun r) {
        Map<String, Object> v = new java.util.LinkedHashMap<>();
        v.put("id", r.getId());
        v.put("period", r.getPeriodYear() + "-" + String.format("%02d", r.getPeriodMonth()));
        v.put("status", r.getRunStatus().name());
        v.put("employeeCount", r.getEmployeeCount());
        v.put("totalGross", r.getTotalGross());
        v.put("totalDeductions", r.getTotalDeductions());
        v.put("totalNet", r.getTotalNet());
        v.put("journalEntryId", r.getJournalEntryId());
        v.put("paymentJournalEntryId", r.getPaymentJournalEntryId());
        v.put("statutoryPaid", r.isStatutoryPaid());
        v.put("statutoryPaidDate", r.getStatutoryPaidDate() == null ? null : r.getStatutoryPaidDate().toString());
        v.put("statutoryJournalEntryId", r.getStatutoryJournalEntryId());
        return v;
    }

    private Map<String, Object> slipView(Payslip p) {
        Map<String, Object> v = new java.util.LinkedHashMap<>();
        v.put("employeeId", p.getEmployeeId());
        v.put("employeeName", p.getEmployeeName());
        v.put("gross", p.getGross());
        v.put("pf", p.getPfEmployee());
        v.put("esi", p.getEsiEmployee());
        v.put("professionalTax", p.getProfessionalTax());
        v.put("tds", p.getTds());
        v.put("totalDeductions", p.getTotalDeductions());
        v.put("netPay", p.getNetPay());
        return v;
    }

    private BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private <T> ResponseEntity<ApiResponse<T>> respond(T data, String message, HttpStatus status) {
        return ResponseEntity.status(status).body(ApiResponse.<T>builder()
                .success(true).status(status.value()).message(message)
                .correlationId(UUID.randomUUID()).data(data).build());
    }
}
