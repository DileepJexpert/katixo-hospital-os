package com.katixo.hospital.ot;

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

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OtServiceTest {

    private static final String TENANT = "demo-tenant";

    @Mock OtRoomRepository roomRepository;
    @Mock OtBookingRepository bookingRepository;
    @Mock AuditService auditService;

    private OtService service;

    @BeforeEach
    void setUp() {
        service = new OtService(roomRepository, bookingRepository, auditService);
        TenantContext.set(new TenantContext(TENANT, "1", "1", "9", "doctor"));
        lenient().when(bookingRepository.nextBookingSequence()).thenReturn(1001L);
        lenient().when(roomRepository.save(any())).thenAnswer(inv -> {
            OtRoom r = inv.getArgument(0);
            if (r.getId() == null) ReflectionTestUtils.setField(r, "id", 3L);
            return r;
        });
        lenient().when(bookingRepository.save(any())).thenAnswer(inv -> {
            OtBooking b = inv.getArgument(0);
            if (b.getId() == null) ReflectionTestUtils.setField(b, "id", 8L);
            return b;
        });
        lenient().when(roomRepository.findByIdAndTenantIdAndBranchId(eq(3L), eq(TENANT), eq(1L)))
                .thenReturn(Optional.of(room()));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private OtRoom room() {
        OtRoom r = new OtRoom();
        r.setName("OT-1");
        ReflectionTestUtils.setField(r, "id", 3L);
        return r;
    }

    @Test
    void bookCreatesScheduledWhenNoOverlap() {
        when(bookingRepository.countOverlapping(eq(TENANT), eq(1L), eq(3L), any(), any(), any()))
                .thenReturn(0L);
        OtBooking b = service.book(3L, 100L, 200L, "Appendectomy",
                LocalDate.of(2026, 7, 1), LocalTime.of(9, 0), LocalTime.of(11, 0), null);
        assertEquals("OT-1001", b.getBookingNumber());
        assertEquals(OtBooking.OtStatus.SCHEDULED, b.getOtStatus());
    }

    @Test
    void bookRejectsOverlappingSlot() {
        when(bookingRepository.countOverlapping(eq(TENANT), eq(1L), eq(3L), any(), any(), any()))
                .thenReturn(1L);
        BusinessException ex = assertThrows(BusinessException.class, () -> service.book(3L, 100L, 200L,
                "Appendectomy", LocalDate.of(2026, 7, 1), LocalTime.of(9, 0), LocalTime.of(11, 0), null));
        assertEquals("OT_SLOT_CONFLICT", ex.getCode());
    }

    @Test
    void bookRejectsEndBeforeStart() {
        BusinessException ex = assertThrows(BusinessException.class, () -> service.book(3L, 100L, 200L,
                "X", LocalDate.of(2026, 7, 1), LocalTime.of(11, 0), LocalTime.of(9, 0), null));
        assertEquals("OT_SLOT_INVALID", ex.getCode());
    }

    @Test
    void lifecycleStartCompleteCapturesNotes() {
        OtBooking b = new OtBooking();
        b.setOtStatus(OtBooking.OtStatus.SCHEDULED);
        ReflectionTestUtils.setField(b, "id", 8L);
        when(bookingRepository.findByIdAndTenantIdAndBranchId(8L, TENANT, 1L)).thenReturn(Optional.of(b));

        assertEquals(OtBooking.OtStatus.IN_PROGRESS, service.start(8L).getOtStatus());
        OtBooking done = service.complete(8L, "Uneventful, blood loss minimal");
        assertEquals(OtBooking.OtStatus.COMPLETED, done.getOtStatus());
        assertEquals("Uneventful, blood loss minimal", done.getSurgeryNotes());
    }

    @Test
    void cannotCancelCompleted() {
        OtBooking b = new OtBooking();
        b.setOtStatus(OtBooking.OtStatus.COMPLETED);
        ReflectionTestUtils.setField(b, "id", 8L);
        when(bookingRepository.findByIdAndTenantIdAndBranchId(8L, TENANT, 1L)).thenReturn(Optional.of(b));
        BusinessException ex = assertThrows(BusinessException.class, () -> service.cancel(8L, "x"));
        assertEquals("INVALID_STATE", ex.getCode());
    }
}
