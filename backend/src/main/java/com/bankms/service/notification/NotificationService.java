package com.bankms.service.notification;

import com.bankms.config.AppProperties;
import com.bankms.dto.common.PageResponse;
import com.bankms.dto.notification.NotificationResponse;
import com.bankms.entity.Notification;
import com.bankms.entity.NotificationType;
import com.bankms.entity.User;
import com.bankms.exception.BankException;
import com.bankms.mapper.AdminMapper;
import com.bankms.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * In-app notifications are written in the caller's transaction, so they appear if and only if the
 * business change commits. Email is published as an event and sent after commit (EmailDispatcher).
 */
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notifications;
    private final ApplicationEventPublisher events;
    private final AdminMapper mapper;
    private final AppProperties properties;

    @Transactional
    public void notify(User user, NotificationType type, String title, String message) {
        notifications.save(new Notification(user.getId(), type, title, message));
    }

    @Transactional
    public void notifyAndEmail(User user, NotificationType type, String title, String message) {
        notify(user, type, title, message);
        email(user.getEmail(), title, greeting(user) + message);
    }

    /** Email only, e.g. an OTP, which must never be shown in the app. */
    public void email(String to, String subject, String body) {
        events.publishEvent(new EmailRequested(to, "[" + properties.bank().name() + "] " + subject,
                body + "\n\n- " + properties.bank().name() + "\nThis is an automated message; please do not reply."));
    }

    @Transactional(readOnly = true)
    public PageResponse<NotificationResponse> list(Long userId, boolean unreadOnly, Pageable pageable) {
        Page<Notification> page = unreadOnly
                ? notifications.findByUserIdAndReadFalse(userId, pageable)
                : notifications.findByUserId(userId, pageable);
        return PageResponse.of(page, mapper::toNotification);
    }

    @Transactional(readOnly = true)
    public long unreadCount(Long userId) {
        return notifications.countByUserIdAndReadFalse(userId);
    }

    @Transactional
    public NotificationResponse markRead(Long userId, Long notificationId) {
        Notification notification = notifications.findByIdAndUserId(notificationId, userId)
                .orElseThrow(() -> BankException.notFound("Notification", notificationId));
        notification.setRead(true);
        return mapper.toNotification(notification);
    }

    @Transactional
    public int markAllRead(Long userId) {
        return notifications.markAllRead(userId);
    }

    private static String greeting(User user) {
        return "Dear " + user.getFullName() + ",\n\n";
    }
}
