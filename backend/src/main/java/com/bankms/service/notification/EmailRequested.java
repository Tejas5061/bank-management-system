package com.bankms.service.notification;

public record EmailRequested(String to, String subject, String body) {

    @Override
    public String toString() {
        return "EmailRequested[to=" + to + ", subject=" + subject + "]";
    }
}
