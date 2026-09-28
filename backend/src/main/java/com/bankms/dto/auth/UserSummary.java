package com.bankms.dto.auth;

import com.bankms.entity.Role;
import com.bankms.entity.User;

public record UserSummary(Long id, String email, String fullName, Role role) {

    public static UserSummary of(User user) {
        return new UserSummary(user.getId(), user.getEmail(), user.getFullName(), user.getRole());
    }
}
