package com.katixo.hospital.notification;

import com.katixo.hospital.common.dto.ApiResponse;
import com.katixo.hospital.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
@Slf4j
public class NotificationController {

    private final NotificationService notificationService;

    @PostMapping("/send")
    @PreAuthorize("hasAnyRole('ADMIN', 'DOCTOR', 'NURSE')")
    public ResponseEntity<ApiResponse<NotificationResponse>> sendNotification(
            @RequestBody SendNotificationRequest request) {
        var response = notificationService.sendNotification(request);
        return respond(response, "Notification sent", HttpStatus.CREATED);
    }

    @GetMapping("/unread")
    @PreAuthorize("hasAnyRole('PATIENT', 'DOCTOR', 'ADMIN')")
    public ResponseEntity<ApiResponse<List<NotificationResponse>>> getUnreadNotifications() {
        var ctx = TenantContext.get();
        var notifications = notificationService.getUnreadNotifications(
                Long.parseLong(ctx.getUserId())
        );
        return respond(notifications, "Unread notifications", HttpStatus.OK);
    }

    @GetMapping("/history")
    @PreAuthorize("hasAnyRole('PATIENT', 'DOCTOR', 'ADMIN')")
    public ResponseEntity<ApiResponse<List<NotificationResponse>>> getNotificationHistory(
            @RequestParam(value = "limit", defaultValue = "20") int limit) {
        var ctx = TenantContext.get();
        var notifications = notificationService.getNotificationHistory(
                Long.parseLong(ctx.getUserId()),
                limit
        );
        return respond(notifications, "Notification history", HttpStatus.OK);
    }

    @PostMapping("/{id}/read")
    @PreAuthorize("hasAnyRole('PATIENT', 'DOCTOR', 'ADMIN')")
    public ResponseEntity<ApiResponse<Void>> markAsRead(@PathVariable Long id) {
        notificationService.markAsRead(id);
        return respond(null, "Marked as read", HttpStatus.OK);
    }

    @PostMapping("/read-all")
    @PreAuthorize("hasAnyRole('PATIENT', 'DOCTOR', 'ADMIN')")
    public ResponseEntity<ApiResponse<Void>> markAllAsRead() {
        var ctx = TenantContext.get();
        notificationService.markAllAsRead(Long.parseLong(ctx.getUserId()));
        return respond(null, "All marked as read", HttpStatus.OK);
    }

    private <T> ResponseEntity<ApiResponse<T>> respond(T data, String message, HttpStatus status) {
        return ResponseEntity.status(status).body(ApiResponse.<T>builder()
                .success(true)
                .status(status.value())
                .message(message)
                .correlationId(UUID.randomUUID())
                .data(data)
                .build());
    }
}
