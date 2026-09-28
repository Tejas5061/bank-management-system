package com.bankms.security;

import com.bankms.entity.Role;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

/** The authenticated principal, rebuilt from JWT claims on every request (no session, no DB hit). */
public record AuthUser(Long id, String email, Role role) {

    public List<GrantedAuthority> authorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    public boolean isCustomer() {
        return role == Role.CUSTOMER;
    }

    public boolean isStaff() {
        return role == Role.EMPLOYEE || role == Role.ADMIN;
    }
}
