package com.bankms.audit;

import com.bankms.exception.BankException;
import com.bankms.security.AuthUser;
import com.bankms.security.CurrentUser;
import com.bankms.service.AuditService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.context.expression.MethodBasedEvaluationContext;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.core.annotation.Order;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Records every {@link Audited} call, successful or not.
 * <p>
 * Ordered to run OUTSIDE the method's {@code @Transactional} boundary (the transaction advisor has
 * the lowest precedence), so by the time we write the audit row the business transaction has
 * already committed or rolled back and released its row locks. The row itself is written in a new
 * transaction, so a failed transfer still leaves a FAILURE audit record behind.
 */
@Slf4j
@Aspect
@Component
@Order(0)
@RequiredArgsConstructor
public class AuditAspect {

    private final AuditService auditService;
    private final ExpressionParser parser = new SpelExpressionParser();
    private final ParameterNameDiscoverer parameterNames = new DefaultParameterNameDiscoverer();
    private final Map<String, Expression> expressionCache = new ConcurrentHashMap<>();

    @Around("@annotation(audited)")
    public Object audit(ProceedingJoinPoint joinPoint, Audited audited) throws Throwable {
        Object result;
        try {
            result = joinPoint.proceed();
        } catch (Throwable ex) {
            record(joinPoint, audited, AuditOutcome.FAILURE, null, ex);
            throw ex;
        }
        record(joinPoint, audited, AuditOutcome.SUCCESS, result, null);
        return result;
    }

    private void record(ProceedingJoinPoint joinPoint, Audited audited, AuditOutcome outcome,
                        Object result, Throwable failure) {
        try {
            Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
            EvaluationContext context =
                    new MethodBasedEvaluationContext(joinPoint.getTarget(), method, joinPoint.getArgs(), parameterNames);
            context.setVariable("result", result);

            AuditEntry.AuditEntryBuilder entry = AuditEntry.builder()
                    .action(audited.action())
                    .outcome(outcome)
                    .entityType(StringUtils.hasText(audited.entityType()) ? audited.entityType() : null)
                    .entityId(evaluate(audited.entityId(), context))
                    .ipAddress(clientIp());

            AuthUser user = CurrentUser.find().orElse(null);
            if (user != null) {
                entry.actorId(user.id()).actorEmail(user.email()).actorRole(user.role().name());
            } else {
                String actor = evaluate(audited.actor(), context);
                entry.actorEmail(actor != null ? actor : (clientIp() == null ? "SYSTEM" : "anonymous"));
            }

            String details = evaluate(audited.details(), context);
            if (failure != null) {
                String reason = failure instanceof BankException be
                        ? be.getErrorCode() + ": " + be.getMessage()
                        : failure.getClass().getSimpleName();
                details = details == null ? reason : details + " | " + reason;
            }
            entry.details(details);
            auditService.record(entry.build());
        } catch (RuntimeException e) {
            // Never let auditing break (or mask the outcome of) the business call.
            log.error("Failed to write audit record for {}", audited.action(), e);
        }
    }

    private String evaluate(String expression, EvaluationContext context) {
        if (!StringUtils.hasText(expression)) {
            return null;
        }
        try {
            Object value = expressionCache.computeIfAbsent(expression, parser::parseExpression).getValue(context);
            return value == null ? null : String.valueOf(value);
        } catch (RuntimeException e) {
            // e.g. #result.id on a failed call where #result is null
            return null;
        }
    }

    private static String clientIp() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            HttpServletRequest request = attributes.getRequest();
            return request.getRemoteAddr();
        }
        return null;
    }
}
