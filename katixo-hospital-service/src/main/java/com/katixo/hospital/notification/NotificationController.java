package com.katixo.hospital.notification;

import com.katixo.hospital.common.ApiResponse;
import com.katixo.hospital.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
@Slf4j
public class NotificationController {

    private final NotificationService notificationService;
    private final TenantContext tenantContext;

    @PostMapping("/send")
    @PreAuthorize("hasAnyRole('ADMIN', 'DOCTOR', 'NURSE')")
    public ResponseEntity<ApiResponse<NotificationResponse>> sendNotification(
            @RequestBody SendNotificationRequest request) {
        var response = notificationService.sendNotification(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Notification sent"));
    }

    @GetMapping("/unread")
    @PreAuthorize("hasAnyRole('PATIENT', 'DOCTOR', 'ADMIN')")
    public ResponseEntity<ApiResponse<List<NotificationResponse>>> getUnreadNotifications() {
        var ctx = tenantContext.current();
        var notifications = notificationService.getUnreadNotifications(
                Long.parseLong(ctx.getCurrentUserId())
        );
        return ResponseEntity.ok(ApiResponse.success(notifications, "Unread notifications"));
    }

    @GetMapping("/history")
    @PreAuthorize("hasAnyRole('PATIENT', 'DOCTOR', 'ADMIN')")
    public ResponseEntity<ApiResponse<List<NotificationResponse>>> getNotificationHistory(
            @RequestParam(value = "limit", defaultValue = "20") int limit) {
        var ctx = tenantContext.current();
        var notifications = notificationService.getNotificationHistory(
                Long.parseLong(ctx.getCurrentUserId()),
                limit
        );
        return ResponseEntity.ok(ApiResponse.success(notifications, "Notification history"));
    }

    @PostMapping("/{id}/read")
    @PreAuthorize("hasAnyRole('PATIENT', 'DOCTOR', 'ADMIN')")
    public ResponseEntity<ApiResponse<Void>> markAsRead(@PathVariable Long id) {
        notificationService.markAsRead(id);
        return ResponseEntity.ok(ApiResponse.success(null, "Marked as read"));
    }

    @PostMapping("/read-all")
    @PreAuthorize("hasAnyRole('PATIENT', 'DOCTOR', 'ADMIN')")
    public ResponseEntity<ApiResponse<Void>> markAllAsRead() {
        var ctx = tenantContext.current();
        notificationService.markAllAsRead(Long.parseLong(ctx.getCurrentUserId()));
        return ResponseEntity.ok(ApiResponse.success(null, "All marked as read"));
    }
}
