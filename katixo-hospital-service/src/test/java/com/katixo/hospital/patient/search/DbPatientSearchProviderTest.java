package com.katixo.hospital.patient.search;

import com.katixo.hospital.patient.Patient;
import com.katixo.hospital.patient.PatientRepository;
import com.katixo.hospital.patient.PatientSearchIndex;
import com.katixo.hospital.patient.PatientSearchIndexRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DbPatientSearchProviderTest {

    @Mock PatientRepository patientRepository;
    @Mock PatientSearchIndexRepository searchIndexRepository;

    @InjectMocks DbPatientSearchProvider provider;

    private Patient patient(Long id) {
        Patient p = new Patient();
        ReflectionTestUtils.setField(p, "id", id);
        p.setTenantId("demo");
        p.setHospitalGroupId(1L);
        p.setBranchId(1L);
        p.setFirstName("Asha");
        p.setMobile("9876543210");
        p.setUhid("UH-1");
        return p;
    }

    @Test
    void searchDelegatesToRepository() {
        when(patientRepository.search("demo", 1L, "asha")).thenReturn(List.of(patient(1L)));
        List<Patient> out = provider.search("demo", 1L, "asha");
        assertEquals(1, out.size());
    }

    @Test
    void indexCreatesWhenAbsent() {
        when(searchIndexRepository.findByTenantIdAndPatientId("demo", 1L)).thenReturn(Optional.empty());
        provider.index(patient(1L));
        ArgumentCaptor<PatientSearchIndex> cap = ArgumentCaptor.forClass(PatientSearchIndex.class);
        verify(searchIndexRepository).save(cap.capture());
        assertEquals(1L, cap.getValue().getPatientId());
        assertEquals("UH-1", cap.getValue().getUhid());
    }

    @Test
    void indexUpdatesWhenPresent() {
        PatientSearchIndex existing = PatientSearchIndex.builder()
                .tenantId("demo").hospitalGroupId(1L).branchId(1L).patientId(1L)
                .fullName("Old").mobile("0").uhid("UH-0").identifiersText("").build();
        when(searchIndexRepository.findByTenantIdAndPatientId("demo", 1L)).thenReturn(Optional.of(existing));
        provider.index(patient(1L));
        verify(searchIndexRepository).save(existing);
        assertEquals("UH-1", existing.getUhid());
        assertEquals("9876543210", existing.getMobile());
    }

    @Test
    void removeDeletesIndexRowWhenPresent() {
        PatientSearchIndex existing = PatientSearchIndex.builder()
                .tenantId("demo").patientId(1L).build();
        when(searchIndexRepository.findByTenantIdAndPatientId("demo", 1L)).thenReturn(Optional.of(existing));
        provider.remove("demo", 1L);
        verify(searchIndexRepository).delete(existing);
    }
}
