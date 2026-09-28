package com.bankms.service.auth;

import com.bankms.dto.auth.AuthResponse;

/** The JSON body plus the raw refresh token, which the controller moves into an HttpOnly cookie. */
public record LoginResult(AuthResponse response, String refreshToken) {

    @Override
    public String toString() {
        return "LoginResult[user=" + response.user().email() + "]";
    }
}
