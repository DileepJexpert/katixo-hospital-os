package com.katixo.hospital.tpa;

import com.katixo.hospital.accounting.JournalEntry;
import com.katixo.hospital.accounting.JournalService;
import com.katixo.hospital.audit.AuditService;
import com.katixo.hospital.common.exception.BusinessException;
import com.katixo.hospital.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TpaServiceTest {

    private static final String TENANT = "demo-tenant";

    @Mock TpaPayerRepository payerRepository;
    @Mock TpaCaseRepository caseRepository;
    @Mock TpaCaseEventRepository eventRepository;
    @Mock JournalService journalService;
    @Mock AuditService auditService;

    private TpaService service;

    @BeforeEach
    void setUp() {
        service = new TpaService(payerRepository, caseRepository, eventRepository,
                journalService, auditService);
        TenantContext.set(new TenantContext(TENANT, "1", "1", "9", "billing"));
        lenient().when(caseRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(eventRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        JournalEntry je = new JournalEntry();
        ReflectionTestUtils.setField(je, "id", 900L);
        ReflectionTestUtils.setField(je, "entryNumber", "JE-900");
        lenient().when(journalService.post(any(), anyString(), eq("TPA"), anyString(), any()))
                .thenReturn(je);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private TpaCase caseInState(long id, TpaCase.CaseStatus status, String approved) {
        TpaCase c = new TpaCase();
        c.setBranchId(1L);
        c.setCaseNumber("TPA-100001");
        c.setPayerId(1L);
        c.setPatientId(2L);
        c.setClaimedAmount(new BigDecimal("60000.00"));
        c.setApprovedAmount(new BigDecimal(approved));
        c.setCaseStatus(status);
        ReflectionTestUtils.setField(c, "id", id);
        when(caseRepository.findByIdAndTenantIdAndBranchId(id, TENANT, 1L)).thenReturn(Optional.of(c));
        return c;
    }

    @Test
    @SuppressWarnings("unchecked")
    void approveReclassifiesReceivableFromPatient() {
        caseInState(10L, TpaCase.CaseStatus.PREAUTH_REQUESTED, "0");

        TpaCase result = service.approve(10L, new BigDecimal("50000.00"));

        ArgumentCaptor<List<JournalService.Line>> captor = ArgumentCaptor.forClass(List.class);
        verify(journalService).post(any(), anyString(), eq("TPA"), anyString(), captor.capture());
        List<JournalService.Line> lines = captor.getValue();
        assertEquals("1110", lines.get(0).accountCode());                 // Insurance Receivable (DR)
        assertEquals(new BigDecimal("50000.00"), lines.get(0).debit());
        assertEquals("1100", lines.get(1).accountCode());                 // Patient AR (CR)
        assertEquals(new BigDecimal("50000.00"), lines.get(1).credit());
        BigDecimal dr = lines.stream().map(JournalService.Line::debit).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal cr = lines.stream().map(JournalService.Line::credit).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(0, dr.compareTo(cr), "approval journal must balance");
        assertEquals(TpaCase.CaseStatus.APPROVED, result.getCaseStatus());
        assertEquals(0, new BigDecimal("50000.00").compareTo(result.getApprovedAmount()));
    }

    @Test
    @SuppressWarnings("unchecked")
    void settleClearsReceivableWithDisallowedWriteOff() {
        caseInState(11L, TpaCase.CaseStatus.CLAIM_SUBMITTED, "50000.00");

        TpaCase result = service.settle(11L, new BigDecimal("45000.00"),
                new BigDecimal("5000.00"), false);

        ArgumentCaptor<List<JournalService.Line>> captor = ArgumentCaptor.forClass(List.class);
        verify(journalService).post(any(), anyString(), eq("TPA"), anyString(), captor.capture());
        List<JournalService.Line> lines = captor.getValue();
        assertEquals("1020", lines.get(0).accountCode());                 // Bank (DR received)
        assertEquals(new BigDecimal("45000.00"), lines.get(0).debit());
        assertEquals("5300", lines.get(1).accountCode());                 // Write-off (DR disallowed)
        assertEquals(new BigDecimal("5000.00"), lines.get(1).debit());
        assertEquals("1110", lines.get(2).accountCode());                 // Insurance Receivable (CR total)
        assertEquals(new BigDecimal("50000.00"), lines.get(2).credit());
        BigDecimal dr = lines.stream().map(JournalService.Line::debit).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal cr = lines.stream().map(JournalService.Line::credit).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(0, dr.compareTo(cr), "settlement journal must balance");
        assertEquals(TpaCase.CaseStatus.SETTLED, result.getCaseStatus());
        assertEquals(0, new BigDecimal("45000.00").compareTo(result.getSettledAmount()));
        assertEquals(0, new BigDecimal("5000.00").compareTo(result.getDisallowedAmount()));
    }

    @Test
    void partialSettlementKeepsCaseOpen() {
        caseInState(12L, TpaCase.CaseStatus.CLAIM_SUBMITTED, "50000.00");
        TpaCase result = service.settle(12L, new BigDecimal("20000.00"), BigDecimal.ZERO, false);
        assertEquals(TpaCase.CaseStatus.PARTIALLY_SETTLED, result.getCaseStatus());
        assertEquals(0, new BigDecimal("20000.00").compareTo(result.getSettledAmount()));
    }

    @Test
    void cannotSettleMoreThanOutstanding() {
        caseInState(13L, TpaCase.CaseStatus.APPROVED, "50000.00");
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.settle(13L, new BigDecimal("60000.00"), BigDecimal.ZERO, false));
        assertEquals("SETTLE_EXCEEDS_RECEIVABLE", ex.getCode());
    }

    @Test
    void cannotApproveAlreadySettledCase() {
        caseInState(14L, TpaCase.CaseStatus.SETTLED, "50000.00");
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.approve(14L, new BigDecimal("1000.00")));
        assertEquals("INVALID_STATE", ex.getCode());
    }
}
