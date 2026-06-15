package com.katixo.hospital.notification;

import com.katixo.hospital.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    private static final String TENANT = "demo-tenant";

    @Mock NotificationSettingsRepository settingsRepository;
    @Mock NotificationTemplateRepository templateRepository;
    @Mock NotificationLogRepository logRepository;

    private final FakeSms fakeSms = new FakeSms();

    private NotificationService service;

    @BeforeEach
    void setUp() {
        service = new NotificationService(settingsRepository, templateRepository, logRepository,
                List.of(fakeSms), List.of());
        TenantContext.set(new TenantContext(TENANT, "1", "1", "9", "admin"));
        lenient().when(logRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private NotificationSettings smsEnabledSettings() {
        NotificationSettings s = new NotificationSettings();
        s.setSmsEnabled(true);
        s.setSmsProvider("FAKE");
        s.setWhatsappEnabled(false);
        return s;
    }

    private NotificationTemplate smsTemplate() {
        NotificationTemplate t = new NotificationTemplate();
        t.setNotificationType(NotificationType.WALK_IN);
        t.setChannel(NotificationChannel.SMS);
        t.setProviderRef("DLT-123");
        t.setBody("Hi {name}, token {token}");
        t.setActive(true);
        return t;
    }

    @Test
    void rendersPlaceholders() {
        assertEquals("Hi Asha, token 5",
                NotificationService.render("Hi {name}, token {token}", Map.of("name", "Asha", "token", "5")));
    }

    @Test
    void skippedWithoutConsent() {
        when(settingsRepository.findByTenantIdAndBranchId(TENANT, 1L))
                .thenReturn(Optional.of(smsEnabledSettings()));
        List<NotificationLog> logs = service.notify(NotificationType.WALK_IN, "9876543210", false,
                Map.of(), "OPDVisit", 1L);
        assertEquals(1, logs.size());
        assertEquals(NotificationLog.SendStatus.SKIPPED, logs.get(0).getSendStatus());
    }

    @Test
    void skippedWhenNoTemplate() {
        when(settingsRepository.findByTenantIdAndBranchId(TENANT, 1L))
                .thenReturn(Optional.of(smsEnabledSettings()));
        when(templateRepository.findByTenantIdAndBranchIdAndNotificationTypeAndChannel(
                TENANT, 1L, NotificationType.WALK_IN, NotificationChannel.SMS)).thenReturn(Optional.empty());
        List<NotificationLog> logs = service.notify(NotificationType.WALK_IN, "9876543210", true,
                Map.of("name", "Asha"), "OPDVisit", 1L);
        assertEquals(NotificationLog.SendStatus.SKIPPED, logs.get(0).getSendStatus());
    }

    @Test
    void sentWhenConfiguredAndProviderOk() {
        when(settingsRepository.findByTenantIdAndBranchId(TENANT, 1L))
                .thenReturn(Optional.of(smsEnabledSettings()));
        when(templateRepository.findByTenantIdAndBranchIdAndNotificationTypeAndChannel(
                TENANT, 1L, NotificationType.WALK_IN, NotificationChannel.SMS))
                .thenReturn(Optional.of(smsTemplate()));
        fakeSms.result = SendResult.ok("fake-1");

        List<NotificationLog> logs = service.notify(NotificationType.WALK_IN, "9876543210", true,
                Map.of("name", "Asha", "token", "5"), "OPDVisit", 1L);

        assertEquals(1, logs.size());
        assertEquals(NotificationLog.SendStatus.SENT, logs.get(0).getSendStatus());
        assertEquals("Hi Asha, token 5", fakeSms.lastBody); // placeholders rendered + passed to provider
    }

    @Test
    void failureRecordedNotThrown() {
        when(settingsRepository.findByTenantIdAndBranchId(TENANT, 1L))
                .thenReturn(Optional.of(smsEnabledSettings()));
        when(templateRepository.findByTenantIdAndBranchIdAndNotificationTypeAndChannel(
                TENANT, 1L, NotificationType.WALK_IN, NotificationChannel.SMS))
                .thenReturn(Optional.of(smsTemplate()));
        fakeSms.result = SendResult.failed("gateway down");

        List<NotificationLog> logs = service.notify(NotificationType.WALK_IN, "9876543210", true,
                Map.of("name", "Asha"), "OPDVisit", 1L);

        assertEquals(NotificationLog.SendStatus.FAILED, logs.get(0).getSendStatus());
        assertEquals("gateway down", logs.get(0).getErrorText());
    }

    /** Test double for an SMS gateway. */
    static class FakeSms implements SmsProvider {
        SendResult result = SendResult.ok("fake");
        String lastBody;

        @Override
        public boolean supports(String provider) {
            return "FAKE".equalsIgnoreCase(provider);
        }

        @Override
        public SendResult send(NotificationSettings cfg, String mobile, String body, String dltTemplateId) {
            this.lastBody = body;
            return result;
        }
    }
}
