package com.bankms.support;

import com.bankms.config.AppProperties;

import java.time.Duration;
import java.time.ZoneId;
import java.util.List;

/** AppProperties for plain unit tests (no Spring context). */
public final class TestProperties {

    public static final String JWT_SECRET = "dGVzdC1vbmx5LWtleS1kby1ub3QtdXNlLWluLXByb2R1Y3Rpb24tMDEyMzQ1Njc4OQ==";

    private TestProperties() {
    }

    public static AppProperties create() {
        return withRateLimit(10, Duration.ofMinutes(1));
    }

    public static AppProperties withRateLimit(int capacity, Duration refill) {
        return new AppProperties(
                new AppProperties.Bank("Kosh Bank", "KOSH", ZoneId.of("Asia/Kolkata"), "INR"),
                new AppProperties.Security(
                        new AppProperties.Jwt(JWT_SECRET, "bank-management-system", Duration.ofMinutes(15), Duration.ofDays(7)),
                        new AppProperties.RefreshCookie("bms_refresh", "/api/v1/auth", false, "Strict"),
                        new AppProperties.Lockout(5, Duration.ofMinutes(15)),
                        new AppProperties.PasswordReset(Duration.ofMinutes(10), 5),
                        4),
                new AppProperties.RateLimit(capacity, refill),
                new AppProperties.Cors(List.of("http://localhost:5173")),
                new AppProperties.Transfer(Duration.ofMinutes(30), Duration.ofHours(24)),
                new AppProperties.Mail(false, "no-reply@koshbank.test"),
                new AppProperties.Bootstrap(null, null, null));
    }
}
