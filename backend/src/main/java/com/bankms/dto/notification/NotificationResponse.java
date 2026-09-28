package com.bankms.dto.notification;

import com.bankms.entity.NotificationType;

import java.time.Instant;

public record NotificationResponse(Long id, NotificationType type, String title, String message, boolean read,
                                   Instant createdAt) {
}
