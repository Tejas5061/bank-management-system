package com.bankms.service.idempotency;

/** A response plus whether it was replayed from an earlier request with the same key. */
public record Idempotent<T>(T body, boolean replayed) {

    public static <T> Idempotent<T> fresh(T body) {
        return new Idempotent<>(body, false);
    }

    public static <T> Idempotent<T> replay(T body) {
        return new Idempotent<>(body, true);
    }
}
