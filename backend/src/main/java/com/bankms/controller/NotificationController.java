package com.bankms.controller;

import com.bankms.dto.common.MessageResponse;
import com.bankms.dto.common.PageResponse;
import com.bankms.dto.notification.NotificationResponse;
import com.bankms.dto.notification.UnreadCountResponse;
import com.bankms.security.AuthUser;
import com.bankms.service.notification.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Available to every role: staff get notifications too (e.g. account unlocked). */
@Tag(name = "Notifications")
@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @Operation(summary = "My notifications, newest first")
    @GetMapping
    public PageResponse<NotificationResponse> list(@AuthenticationPrincipal AuthUser user,
                                                   @RequestParam(defaultValue = "false") boolean unreadOnly,
                                                   @ParameterObject @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return notificationService.list(user.id(), unreadOnly, pageable);
    }

    @Operation(summary = "Unread count (for the bell badge)")
    @GetMapping("/unread-count")
    public UnreadCountResponse unreadCount(@AuthenticationPrincipal AuthUser user) {
        return new UnreadCountResponse(notificationService.unreadCount(user.id()));
    }

    @Operation(summary = "Mark one notification as read")
    @PatchMapping("/{notificationId}/read")
    public NotificationResponse markRead(@AuthenticationPrincipal AuthUser user, @PathVariable Long notificationId) {
        return notificationService.markRead(user.id(), notificationId);
    }

    @Operation(summary = "Mark all as read")
    @PostMapping("/read-all")
    public MessageResponse markAllRead(@AuthenticationPrincipal AuthUser user) {
        int updated = notificationService.markAllRead(user.id());
        return new MessageResponse(updated + " notification(s) marked as read");
    }
}
