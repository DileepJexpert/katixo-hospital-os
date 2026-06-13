package com.katixo.hospital.accounting;

import com.katixo.hospital.audit.AuditService;
import com.katixo.hospital.common.exception.BusinessException;
import com.katixo.hospital.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JournalServiceTest {

    private static final String TENANT = "demo-tenant";

    @Mock
    private JournalEntryRepository entryRepository;
    @Mock
    private JournalLineRepository lineRepository;
    @Mock
    private AccountRepository accountRepository;
    @Mock
    private AuditService auditService;

    private JournalService service;
    private long seq = 100001L;

    @BeforeEach
    void setUp() {
        service = new JournalService(entryRepository, lineRepository, accountRepository, auditService);
        TenantContext.set(new TenantContext(TENANT, "1", "1", "9", "accountant"));

        lenient().when(accountRepository.findByTenantIdAndBranchIdAndCode(eq(TENANT), eq(1L), anyString()))
                .thenAnswer(inv -> {
                    Account a = new Account();
                    a.setCode(inv.getArgument(2));
                    return Optional.of(a);
                });
        lenient().when(entryRepository.nextEntrySequence()).thenAnswer(inv -> seq++);
        lenient().when(entryRepository.save(any())).thenAnswer(inv -> {
            JournalEntry e = inv.getArgument(0);
            if (e.getId() == null) {
                ReflectionTestUtils.setField(e, "id", 1L);
            }
            return e;
        });
        lenient().when(lineRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void postsBalancedEntry() {
        JournalEntry entry = service.post(LocalDate.of(2026, 6, 13), "Pharmacy sale", "PHARMACY", "RECEIPT-1",
                List.of(
                        JournalService.Line.debit("1010", new BigDecimal("112.00"), "cash"),
                        JournalService.Line.credit("4010", new BigDecimal("100.00"), "sales"),
                        JournalService.Line.credit("2110", new BigDecimal("6.00"), "cgst"),
                        JournalService.Line.credit("2120", new BigDecimal("6.00"), "sgst")));

        assertEquals(JournalEntry.EntryStatus.POSTED, entry.getEntryStatus());
        assertEquals("JE-100001", entry.getEntryNumber());
    }

    @Test
    void rejectsUnbalancedEntry() {
        BusinessException ex = assertThrows(BusinessException.class, () -> service.post(
                LocalDate.now(), "bad", "MANUAL", null,
                List.of(JournalService.Line.debit("1010", new BigDecimal("100.00"), "x"),
                        JournalService.Line.credit("4010", new BigDecimal("90.00"), "y"))));
        assertEquals("JOURNAL_UNBALANCED", ex.getCode());
    }

    @Test
    void rejectsSingleLineEntry() {
        BusinessException ex = assertThrows(BusinessException.class, () -> service.post(
                LocalDate.now(), "bad", "MANUAL", null,
                List.of(JournalService.Line.debit("1010", new BigDecimal("100.00"), "x"))));
        assertEquals("JOURNAL_TOO_FEW_LINES", ex.getCode());
    }

    @Test
    void rejectsLineWithBothDebitAndCredit() {
        BusinessException ex = assertThrows(BusinessException.class, () -> service.post(
                LocalDate.now(), "bad", "MANUAL", null,
                List.of(new JournalService.Line("1010", new BigDecimal("10"), new BigDecimal("10"), "x"),
                        JournalService.Line.credit("4010", new BigDecimal("10"), "y"))));
        assertEquals("JOURNAL_LINE_BOTH_SIDES", ex.getCode());
    }

    @Test
    void rejectsUnknownAccount() {
        when(accountRepository.findByTenantIdAndBranchIdAndCode(TENANT, 1L, "9999"))
                .thenReturn(Optional.empty());
        BusinessException ex = assertThrows(BusinessException.class, () -> service.post(
                LocalDate.now(), "bad", "MANUAL", null,
                List.of(JournalService.Line.debit("9999", new BigDecimal("10"), "x"),
                        JournalService.Line.credit("4010", new BigDecimal("10"), "y"))));
        assertEquals("ACCOUNT_NOT_FOUND", ex.getCode());
    }

    @Test
    void reversePostsMirrorAndMarksOriginalReversed() {
        JournalEntry original = new JournalEntry();
        original.setTenantId(TENANT);
        original.setBranchId(1L);
        original.setEntryNumber("JE-100001");
        original.setSourceModule("PHARMACY");
        original.setEntryStatus(JournalEntry.EntryStatus.POSTED);
        ReflectionTestUtils.setField(original, "id", 5L);
        when(entryRepository.findByIdAndTenantIdAndBranchId(5L, TENANT, 1L)).thenReturn(Optional.of(original));

        JournalLine d = new JournalLine();
        d.setAccountCode("1010");
        d.setDebit(new BigDecimal("112.00"));
        d.setCredit(BigDecimal.ZERO);
        JournalLine c = new JournalLine();
        c.setAccountCode("4010");
        c.setDebit(BigDecimal.ZERO);
        c.setCredit(new BigDecimal("112.00"));
        when(lineRepository.findByTenantIdAndJournalEntryIdOrderById(TENANT, 5L)).thenReturn(List.of(d, c));

        service.reverse(5L, "mistake");

        assertEquals(JournalEntry.EntryStatus.REVERSED, original.getEntryStatus());
    }

    @Test
    void cannotReverseTwice() {
        JournalEntry original = new JournalEntry();
        original.setTenantId(TENANT);
        original.setBranchId(1L);
        original.setEntryStatus(JournalEntry.EntryStatus.REVERSED);
        ReflectionTestUtils.setField(original, "id", 5L);
        when(entryRepository.findByIdAndTenantIdAndBranchId(5L, TENANT, 1L)).thenReturn(Optional.of(original));

        BusinessException ex = assertThrows(BusinessException.class, () -> service.reverse(5L, "again"));
        assertEquals("JOURNAL_ALREADY_REVERSED", ex.getCode());
    }
}
