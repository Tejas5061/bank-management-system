package com.bankms.config;

import com.bankms.entity.Role;
import com.bankms.entity.User;
import com.bankms.repository.UserRepository;
import com.bankms.service.auth.AuthService;
import com.bankms.util.ValidationPatterns;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Creates the first administrator from BOOTSTRAP_ADMIN_EMAIL / BOOTSTRAP_ADMIN_PASSWORD when the
 * database has none (production has no demo seed data). Does nothing once any admin exists.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminBootstrap implements ApplicationRunner {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final AppProperties properties;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        AppProperties.Bootstrap bootstrap = properties.bootstrap();
        if (!StringUtils.hasText(bootstrap.adminEmail()) || users.existsByRole(Role.ADMIN)) {
            return;
        }
        if (bootstrap.adminPassword() == null || !bootstrap.adminPassword().matches(ValidationPatterns.STRONG_PASSWORD)) {
            throw new IllegalStateException("BOOTSTRAP_ADMIN_PASSWORD " + ValidationPatterns.STRONG_PASSWORD_MESSAGE);
        }
        String name = StringUtils.hasText(bootstrap.adminName()) ? bootstrap.adminName() : "Administrator";
        users.save(new User(AuthService.normalizeEmail(bootstrap.adminEmail()),
                passwordEncoder.encode(bootstrap.adminPassword()), name, "9000000000", Role.ADMIN));
        log.info("Bootstrapped first administrator {}", bootstrap.adminEmail());
    }
}
