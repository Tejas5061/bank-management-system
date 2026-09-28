package com.bankms.service.notification;

import com.bankms.config.AppProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Sends emails only AFTER the business transaction commits, on a background thread.
 * <p>
 * If we emailed "₹5,000 debited" inside the transaction and it then rolled back, the customer
 * would be told about money that never moved. A slow SMTP server also must not hold row locks.
 * A mail failure is logged, never propagated: the money has already moved and the in-app
 * notification is already saved.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EmailDispatcher {

    private final JavaMailSender mailSender;
    private final AppProperties properties;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void send(EmailRequested email) {
        if (!properties.mail().enabled()) {
            log.debug("Mail disabled; skipping {}", email);
            return;
        }
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(properties.mail().from());
            message.setTo(email.to());
            message.setSubject(email.subject());
            message.setText(email.body());
            mailSender.send(message);
            log.info("Sent {}", email);
        } catch (MailException e) {
            log.warn("Could not send {}: {}", email, e.getMessage());
        }
    }
}
