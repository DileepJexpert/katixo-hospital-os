package com.katixo.hospital.notification;

/** What a notification is about — maps to a configured template per channel. */
public enum NotificationType {
    WALK_IN,
    APPOINTMENT,
    REPORT_READY,
    BILL,
    GENERIC
}
