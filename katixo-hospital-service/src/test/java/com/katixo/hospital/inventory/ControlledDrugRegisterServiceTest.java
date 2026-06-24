package com.katixo.hospital.inventory;

import com.katixo.hospital.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ControlledDrugRegisterServiceTest {

    @Mock ControlledDrugRegisterRepository repository;

    private ControlledDrugRegisterService service;

    @BeforeEach
    void setUp() {
        service = new ControlledDrugRegisterService(repository);
        TenantContext.set(new TenantContext("demo-tenant", "1", "1", "3", "pharmacist"));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void recordsControlledSupplyEntry() {
        Item item = new Item();
        item.setId(5L);
        item.setCode("MORPH10");
        item.setName("Morphine 10mg");
        item.setDrugSchedule(Item.DrugSchedule.NDPS);

        PharmacySale sale = new PharmacySale();
        sale.setId(9L);
        sale.setSaleNumber("PS-1");
        sale.setPatientId(55L);
        sale.setSaleDate(LocalDate.now());

        PharmacySaleLine line = new PharmacySaleLine();
        line.setQuantity(new BigDecimal("2"));

        service.record(sale, line, item, "BATCH-1");

        ArgumentCaptor<ControlledDrugRegisterEntry> cap = ArgumentCaptor.forClass(ControlledDrugRegisterEntry.class);
        verify(repository).save(cap.capture());
        ControlledDrugRegisterEntry e = cap.getValue();
        assertEquals(Item.DrugSchedule.NDPS, e.getDrugSchedule());
        assertEquals("Morphine 10mg", e.getItemName());
        assertEquals("MORPH10", e.getItemCode());
        assertEquals(0, new BigDecimal("2").compareTo(e.getQuantity()));
        assertEquals(55L, e.getPatientId());
        assertEquals("PS-1", e.getSaleNumber());
        assertEquals("BATCH-1", e.getBatchNumber());
        assertEquals("demo-tenant", e.getTenantId());
    }

    @Test
    void itemControlledClassification() {
        Item h1 = new Item();
        h1.setDrugSchedule(Item.DrugSchedule.H1);
        Item plain = new Item();
        plain.setDrugSchedule(Item.DrugSchedule.H);
        assertEquals(true, h1.isControlled());
        assertEquals(false, plain.isControlled());
    }
}
