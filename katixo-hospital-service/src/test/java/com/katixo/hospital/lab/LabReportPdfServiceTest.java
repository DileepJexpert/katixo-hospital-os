package com.katixo.hospital.lab;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LabReportPdfServiceTest {

    @Mock
    private LabService labService;

    @InjectMocks
    private LabReportPdfService pdfService;

    @Test
    void rendersValidPdfWithResults() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("orderNumber", "LAB-2026-000001");
        data.put("orderDate", "2026-06-13T10:00:00");
        data.put("orderStatus", "COMPLETED");
        data.put("patientName", "Asha Rao");
        data.put("uhid", "HOS-1-100001");
        data.put("results", List.of(
                Map.of("testName", "Hemoglobin", "result", "9.1", "unit", "g/dL",
                        "referenceRange", "12-15", "isAbnormal", true, "status", "RELEASED"),
                Map.of("testName", "WBC", "result", "7000", "unit", "/uL",
                        "referenceRange", "4000-11000", "isAbnormal", false, "status", "RELEASED")));

        when(labService.getReportData(5L)).thenReturn(data);

        byte[] pdf = pdfService.renderReportPdf(5L);

        assertTrue(pdf.length > 800, "PDF should not be empty");
        assertEquals("%PDF", new String(pdf, 0, 4));
    }
}
