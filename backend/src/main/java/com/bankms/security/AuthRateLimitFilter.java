package com.bankms.security;

import com.bankms.config.AppProperties;
import com.bankms.exception.ApiErrorFactory;
import com.bankms.exception.ErrorCode;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Token-bucket throttling (Bucket4j) for credential endpoints, one bucket per endpoint + client IP.
 * <p>
 * Complements the per-user lockout: lockout stops guessing one account's password, this stops a
 * single client spraying many accounts. Buckets live in a size-bounded Caffeine cache so an attacker
 * rotating IPs cannot exhaust memory. For several instances, swap in Bucket4j's Redis/JDBC proxy.
 * <p>
 * The client IP is {@code getRemoteAddr()}, which the servlet container rewrites from
 * X-Forwarded-For only for trusted proxies (server.forward-headers-strategy); the raw header is
 * never read here because clients can forge it.
 */
public class AuthRateLimitFilter extends OncePerRequestFilter {

    static final Set<String> LIMITED_PATHS = Set.of(
            "/api/v1/auth/login",
            "/api/v1/auth/register",
            "/api/v1/auth/password/forgot",
            "/api/v1/auth/password/reset");

    private final ApiErrorFactory errors;
    private final int capacity;
    private final Duration refillPeriod;
    private final Cache<String, Bucket> buckets = Caffeine.newBuilder()
            .maximumSize(100_000)
            .expireAfterAccess(Duration.ofMinutes(30))
            .build();

    public AuthRateLimitFilter(AppProperties properties, ApiErrorFactory errors) {
        this.errors = errors;
        this.capacity = properties.rateLimit().authCapacity();
        this.refillPeriod = properties.rateLimit().authRefillPeriod();
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"POST".equalsIgnoreCase(request.getMethod()) || !LIMITED_PATHS.contains(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String key = request.getRequestURI() + '|' + request.getRemoteAddr();
        Bucket bucket = buckets.get(key, k -> newBucket());
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
        if (probe.isConsumed()) {
            response.setHeader("X-RateLimit-Remaining", String.valueOf(probe.getRemainingTokens()));
            chain.doFilter(request, response);
            return;
        }
        long retryAfterSeconds = Math.max(1, TimeUnit.NANOSECONDS.toSeconds(probe.getNanosToWaitForRefill()));
        response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfterSeconds));
        errors.write(response, request, ErrorCode.RATE_LIMITED,
                "Too many attempts. Try again in " + retryAfterSeconds + " seconds.");
    }

    private Bucket newBucket() {
        return Bucket.builder()
                .addLimit(Bandwidth.builder().capacity(capacity).refillGreedy(capacity, refillPeriod).build())
                .build();
    }
}
