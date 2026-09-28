package com.bankms.service;

import com.bankms.audit.AuditAction;
import com.bankms.audit.Audited;
import com.bankms.dto.admin.EmployeeRequest;
import com.bankms.dto.admin.EmployeeResponse;
import com.bankms.dto.admin.EmployeeUpdateRequest;
import com.bankms.dto.common.PageResponse;
import com.bankms.entity.Branch;
import com.bankms.entity.Employee;
import com.bankms.entity.NotificationType;
import com.bankms.entity.Role;
import com.bankms.entity.User;
import com.bankms.exception.BankException;
import com.bankms.mapper.AdminMapper;
import com.bankms.repository.BranchRepository;
import com.bankms.repository.EmployeeRepository;
import com.bankms.repository.UserRepository;
import com.bankms.service.auth.AuthService;
import com.bankms.service.auth.RefreshTokenService;
import com.bankms.service.notification.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class EmployeeService {

    private final EmployeeRepository employees;
    private final UserRepository users;
    private final BranchRepository branches;
    private final NumberGenerator numbers;
    private final PasswordEncoder passwordEncoder;
    private final TemporaryPasswordGenerator temporaryPasswords;
    private final RefreshTokenService refreshTokens;
    private final NotificationService notifications;
    private final AdminMapper mapper;
    private final Clock clock;

    @Transactional(readOnly = true)
    public PageResponse<EmployeeResponse> search(String query, Pageable pageable) {
        Instant now = Instant.now(clock);
        return PageResponse.of(employees.search(StringUtils.hasText(query) ? query.trim() : null, pageable),
                e -> mapper.toEmployee(e, now));
    }

    @Transactional(readOnly = true)
    public EmployeeResponse get(Long employeeId) {
        return employees.findDetailedById(employeeId).map(e -> mapper.toEmployee(e, Instant.now(clock)))
                .orElseThrow(() -> BankException.notFound("Employee", employeeId));
    }

    @Audited(action = AuditAction.EMPLOYEE_CREATED, entityType = "EMPLOYEE", entityId = "#result.employeeCode",
            details = "#request.email + ' at branch ' + #request.branchCode")
    @Transactional
    public EmployeeResponse create(EmployeeRequest request) {
        String email = AuthService.normalizeEmail(request.email());
        if (users.existsByEmail(email)) {
            throw BankException.duplicate("A user with this email already exists");
        }
        Branch branch = branch(request.branchCode());
        String temporaryPassword = temporaryPasswords.generate();
        User user = users.save(new User(email, passwordEncoder.encode(temporaryPassword), request.fullName().trim(),
                request.phone(), Role.EMPLOYEE));

        Employee employee = new Employee();
        employee.setUser(user);
        employee.setEmployeeCode(numbers.nextEmployeeCode());
        employee.setBranch(branch);
        employee.setDesignation(request.designation().trim());
        employees.save(employee);

        notifications.email(user.getEmail(), "Your staff account",
                "Dear %s,\n\nA staff account (%s) has been created for you at the %s branch.\nSign in with this email and the temporary password %s, then change it from your profile."
                        .formatted(user.getFullName(), employee.getEmployeeCode(), branch.getName(), temporaryPassword));
        return mapper.toEmployee(employee, Instant.now(clock));
    }

    @Audited(action = AuditAction.EMPLOYEE_UPDATED, entityType = "EMPLOYEE", entityId = "#employeeId",
            details = "'branch=' + #request.branchCode + ', enabled=' + #request.enabled")
    @Transactional
    public EmployeeResponse update(Long employeeId, EmployeeUpdateRequest request, Long adminUserId) {
        Employee employee = employees.findDetailedById(employeeId)
                .orElseThrow(() -> BankException.notFound("Employee", employeeId));
        User user = employee.getUser();
        if (user.getId().equals(adminUserId) && !request.enabled()) {
            throw BankException.rule("You cannot disable your own account");
        }
        user.setPhone(request.phone());
        employee.setBranch(branch(request.branchCode()));
        employee.setDesignation(request.designation().trim());
        if (user.isEnabled() && !request.enabled()) {
            refreshTokens.revokeAll(user.getId()); // sign them out everywhere; the access token dies within minutes
        }
        user.setEnabled(request.enabled());
        return mapper.toEmployee(employee, Instant.now(clock));
    }

    @Audited(action = AuditAction.USER_UNLOCKED, entityType = "USER", entityId = "#userId")
    @Transactional
    public void unlockUser(Long userId) {
        User user = users.findById(userId).orElseThrow(() -> BankException.notFound("User", userId));
        user.unlock();
        notifications.notify(user, NotificationType.SECURITY, "Account unlocked",
                "An administrator unlocked your account. You can sign in again.");
    }

    private Branch branch(String code) {
        return branches.findByCode(code).filter(Branch::isActive)
                .orElseThrow(() -> BankException.notFound("Branch", code));
    }
}
