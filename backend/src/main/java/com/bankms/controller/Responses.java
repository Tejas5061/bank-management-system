package com.bankms.controller;

import com.bankms.service.idempotency.Idempotent;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

final class Responses {

    static final String IDEMPOTENCY_KEY = "Idempotency-Key";

    private Responses() {
    }

    /** 201 with the stored body; a replay is flagged so clients can tell it apart from a new transfer. */
    static <T> ResponseEntity<T> created(Idempotent<T> result) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .header("Idempotent-Replayed", String.valueOf(result.replayed()))
                .body(result.body());
    }
}
