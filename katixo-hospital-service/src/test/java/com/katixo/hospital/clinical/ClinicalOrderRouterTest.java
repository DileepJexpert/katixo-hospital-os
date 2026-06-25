package com.katixo.hospital.clinical;

import com.katixo.hospital.billing.HospitalCharge;
import com.katixo.hospital.lab.LabOrder;
import com.katixo.hospital.lab.LabService;
import com.katixo.hospital.prescription.Prescription;
import com.katixo.hospital.prescription.PrescriptionService;
import com.katixo.hospital.radiology.RadiologyOrder;
import com.katixo.hospital.radiology.RadiologyService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClinicalOrderRouterTest {

    @Mock LabService labService;
    @Mock RadiologyService radiologyService;
    @Mock PrescriptionService prescriptionService;

    private ClinicalOrderRouter router() {
        return new ClinicalOrderRouter(labService, radiologyService, prescriptionService);
    }

    private Encounter enc(Encounter.SourceType st, Long sid) {
        Encounter e = new Encounter();
        e.setPatientId(55L);
        e.setSourceType(st);
        e.setSourceId(sid);
        return e;
    }

    private ClinicalOrder order(ClinicalOrder.OrderType t, String code, String name) {
        ClinicalOrder o = new ClinicalOrder();
        o.setId(1L);
        o.setOrderType(t);
        o.setCode(code);
        o.setName(name);
        o.setPlacedByDoctorId(3L);
        return o;
    }

    @Test
    void radiologyRoutesForAnyEncounter() {
        RadiologyOrder ro = new RadiologyOrder();
        ro.setId(77L);
        when(radiologyService.order(eq(55L), eq(3L), eq(RadiologyOrder.Modality.CT), eq("CT Head"), any()))
                .thenReturn(ro);

        ClinicalOrderRouter.Ref ref = router().route(
                order(ClinicalOrder.OrderType.RADIOLOGY, "CTHEAD", "CT Head"),
                enc(Encounter.SourceType.STANDALONE, null));

        assertEquals("RADIOLOGY_ORDER", ref.type());
        assertEquals(77L, ref.id());
    }

    @Test
    void labNotRoutedForStandaloneEncounter() {
        ClinicalOrderRouter.Ref ref = router().route(
                order(ClinicalOrder.OrderType.LAB, "CBC", "Complete Blood Count"),
                enc(Encounter.SourceType.STANDALONE, null));

        assertNull(ref);
        verify(labService, never()).createOrder(any(), any(), any(), any());
    }

    @Test
    void labRoutesForVisitSourcedEncounter() {
        LabOrder lo = new LabOrder();
        lo.setId(88L);
        when(labService.createOrder(eq(HospitalCharge.SourceType.OPD_VISIT), eq(200L), any(), any()))
                .thenReturn(lo);

        ClinicalOrderRouter.Ref ref = router().route(
                order(ClinicalOrder.OrderType.LAB, "CBC", "Complete Blood Count"),
                enc(Encounter.SourceType.OPD_VISIT, 200L));

        assertEquals("LAB_ORDER", ref.type());
        assertEquals(88L, ref.id());
    }

    @Test
    void pharmacyRoutesToPrescriptionForVisit() {
        Prescription rx = new Prescription();
        rx.setId(66L);
        when(prescriptionService.create(eq(200L), any(), any(), anyBoolean(), any())).thenReturn(rx);

        ClinicalOrderRouter.Ref ref = router().route(
                order(ClinicalOrder.OrderType.PHARMACY, "AMOX", "Amoxicillin"),
                enc(Encounter.SourceType.OPD_VISIT, 200L));

        assertEquals("PRESCRIPTION", ref.type());
        assertEquals(66L, ref.id());
    }

    @Test
    void pharmacyNotRoutedForStandaloneEncounter() {
        ClinicalOrderRouter.Ref ref = router().route(
                order(ClinicalOrder.OrderType.PHARMACY, "AMOX", "Amoxicillin"),
                enc(Encounter.SourceType.STANDALONE, null));

        assertNull(ref);
        verify(prescriptionService, never()).create(any(), any(), any(), anyBoolean(), any());
    }
}
