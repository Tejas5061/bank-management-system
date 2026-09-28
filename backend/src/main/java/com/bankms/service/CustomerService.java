package com.bankms.service;

import com.bankms.audit.AuditAction;
import com.bankms.audit.Audited;
import com.bankms.dto.auth.RegisterRequest;
import com.bankms.dto.auth.RegistrationResponse;
import com.bankms.dto.common.PageResponse;
import com.bankms.dto.customer.CustomerDetailResponse;
import com.bankms.dto.customer.CustomerProfileResponse;
import com.bankms.dto.customer.CustomerSummaryResponse;
import com.bankms.dto.customer.KycDecisionRequest;
import com.bankms.dto.customer.OnboardCustomerRequest;
import com.bankms.dto.customer.UpdateProfileRequest;
import com.bankms.entity.Account;
import com.bankms.entity.AccountType;
import com.bankms.entity.Branch;
import com.bankms.entity.Customer;
import com.bankms.entity.Employee;
import com.bankms.entity.KycStatus;
import com.bankms.entity.NotificationType;
import com.bankms.entity.Role;
import com.bankms.entity.User;
import com.bankms.exception.BankException;
import com.bankms.mapper.CustomerMapper;
import com.bankms.repository.BranchRepository;
import com.bankms.repository.CustomerRepository;
import com.bankms.repository.EmployeeRepository;
import com.bankms.repository.UserRepository;
import com.bankms.service.auth.AuthService;
import com.bankms.service.notification.NotificationService;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CustomerService {

    private final CustomerRepository customers;
    private final UserRepository users;
    private final BranchRepository branches;
    private final EmployeeRepository employees;
    private final AccountService accountService;
    private final NumberGenerator numbers;
    private final PasswordEncoder passwordEncoder;
    private final TemporaryPasswordGenerator temporaryPasswords;
    private final NotificationService notifications;
    private final CustomerMapper mapper;
    private final Clock clock;

    @Audited(action = AuditAction.CUSTOMER_REGISTERED, entityType = "CUSTOMER",
            entityId = "#result.customerNumber", actor = "#request.email")
    @Transactional
    public RegistrationResponse register(RegisterRequest request) {
        Branch branch = activeBranch(request.branchCode());
        NewCustomer newCustomer = new NewCustomer(request.fullName(), request.email(), request.phone(),
                request.dateOfBirth(), request.panNumber(), request.aadhaarNumber(), request.addressLine(),
                request.city(), request.state(), request.pincode());
        Created created = create(newCustomer, passwordEncoder.encode(request.password()), branch, AccountType.SAVINGS);
        notifications.notifyAndEmail(created.customer().getUser(), NotificationType.GENERAL, "Welcome aboard",
                "Your customer ID is %s and your savings account number is %s. Your KYC is pending: visit your branch with your PAN and Aadhaar to activate the account."
                        .formatted(created.customer().getCustomerNumber(), created.account().getAccountNumber()));
        return new RegistrationResponse(created.customer().getCustomerNumber(), created.account().getAccountNumber(),
                "Registration successful. Your account will be activated once KYC is verified at the branch.");
    }

    @Audited(action = AuditAction.CUSTOMER_ONBOARDED, entityType = "CUSTOMER", entityId = "#result.customerNumber")
    @Transactional
    public RegistrationResponse onboard(OnboardCustomerRequest request, Long employeeUserId) {
        Branch branch = StringUtils.hasText(request.branchCode())
                ? activeBranch(request.branchCode())
                : employees.findByUserId(employeeUserId).map(Employee::getBranch)
                .orElseThrow(() -> BankException.rule("branchCode is required"));
        NewCustomer newCustomer = new NewCustomer(request.fullName(), request.email(), request.phone(),
                request.dateOfBirth(), request.panNumber(), request.aadhaarNumber(), request.addressLine(),
                request.city(), request.state(), request.pincode());
        String temporaryPassword = temporaryPasswords.generate();
        Created created = create(newCustomer, passwordEncoder.encode(temporaryPassword), branch, request.accountType());
        User user = created.customer().getUser();
        notifications.notify(user, NotificationType.GENERAL, "Welcome aboard",
                "Your customer ID is %s and your account number is %s.".formatted(
                        created.customer().getCustomerNumber(), created.account().getAccountNumber()));
        notifications.email(user.getEmail(), "Your new bank account",
                ("Dear %s,\n\nYour account %s has been opened at our %s branch.\nSign in with this email and the temporary password %s, then change it from your profile.\n\nYour KYC will be verified shortly.")
                        .formatted(user.getFullName(), created.account().getAccountNumber(), branch.getName(), temporaryPassword));
        return new RegistrationResponse(created.customer().getCustomerNumber(), created.account().getAccountNumber(),
                "Customer onboarded. Login details were emailed to the customer.");
    }

    @Transactional(readOnly = true)
    public Long customerIdForUser(Long userId) {
        return customers.findByUserId(userId).map(Customer::getId)
                .orElseThrow(() -> BankException.notFound("Customer profile for user", userId));
    }

    @Transactional(readOnly = true)
    public CustomerProfileResponse profileForUser(Long userId) {
        return customers.findByUserId(userId).map(mapper::toProfile)
                .orElseThrow(() -> BankException.notFound("Customer profile for user", userId));
    }

    @Audited(action = AuditAction.PROFILE_UPDATED, entityType = "CUSTOMER", entityId = "#result.customerNumber")
    @Transactional
    public CustomerProfileResponse updateProfile(Long userId, UpdateProfileRequest request) {
        Customer customer = customers.findByUserId(userId)
                .orElseThrow(() -> BankException.notFound("Customer profile for user", userId));
        customer.getUser().setPhone(request.phone());
        customer.setAddressLine(request.addressLine().trim());
        customer.setCity(request.city().trim());
        customer.setState(request.state().trim());
        customer.setPincode(request.pincode());
        return mapper.toProfile(customer);
    }

    @Transactional(readOnly = true)
    public PageResponse<CustomerSummaryResponse> search(String query, KycStatus kycStatus, Pageable pageable) {
        Specification<Customer> spec = (root, cq, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (kycStatus != null) {
                predicates.add(cb.equal(root.get("kycStatus"), kycStatus));
            }
            if (StringUtils.hasText(query)) {
                String like = "%" + query.trim().toLowerCase() + "%";
                Join<Customer, User> user = root.join("user");
                predicates.add(cb.or(
                        cb.like(cb.lower(user.get("fullName")), like),
                        cb.like(cb.lower(user.get("email")), like),
                        cb.like(user.get("phone"), like),
                        cb.like(cb.lower(root.get("customerNumber")), like)));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        return PageResponse.of(customers.findAll(spec, pageable), mapper::toSummary);
    }

    @Transactional(readOnly = true)
    public CustomerDetailResponse detail(Long customerId) {
        Customer customer = customers.findDetailedById(customerId)
                .orElseThrow(() -> BankException.notFound("Customer", customerId));
        return new CustomerDetailResponse(mapper.toProfile(customer), accountService.listForCustomer(customerId));
    }

    @Audited(action = AuditAction.KYC_STATUS_CHANGED, entityType = "CUSTOMER", entityId = "#customerId",
            details = "'status=' + #request.status + (#request.remarks != null ? ', remarks=' + #request.remarks : '')")
    @Transactional
    public CustomerProfileResponse reviewKyc(Long customerId, KycDecisionRequest request, Long reviewerUserId) {
        Customer customer = customers.findDetailedById(customerId)
                .orElseThrow(() -> BankException.notFound("Customer", customerId));
        if (customer.getKycStatus() == request.status()) {
            throw BankException.rule("KYC is already " + request.status().name().toLowerCase());
        }
        customer.reviewKyc(request.status(), request.remarks(), users.getReferenceById(reviewerUserId), Instant.now(clock));
        String message = request.status() == KycStatus.VERIFIED
                ? "Your KYC has been verified. Your accounts are now fully active."
                : "Your KYC could not be verified: %s. Please visit your branch with valid documents.".formatted(request.remarks());
        notifications.notifyAndEmail(customer.getUser(), NotificationType.KYC,
                request.status() == KycStatus.VERIFIED ? "KYC verified" : "KYC rejected", message);
        return mapper.toProfile(customer);
    }

    private Created create(NewCustomer data, String passwordHash, Branch branch, AccountType accountType) {
        String email = AuthService.normalizeEmail(data.email());
        if (users.existsByEmail(email)) {
            throw BankException.duplicate("A user with this email already exists");
        }
        if (customers.existsByPanNumber(data.pan())) {
            throw BankException.duplicate("A customer with this PAN already exists");
        }
        if (customers.existsByAadhaarNumber(data.aadhaar())) {
            throw BankException.duplicate("A customer with this Aadhaar number already exists");
        }
        User user = users.save(new User(email, passwordHash, data.fullName().trim(), data.phone(), Role.CUSTOMER));

        Customer customer = new Customer();
        customer.setUser(user);
        customer.setCustomerNumber(numbers.nextCustomerNumber());
        customer.setDateOfBirth(data.dateOfBirth());
        customer.setPanNumber(data.pan());
        customer.setAadhaarNumber(data.aadhaar());
        customer.setAddressLine(data.addressLine().trim());
        customer.setCity(data.city().trim());
        customer.setState(data.state().trim());
        customer.setPincode(data.pincode());
        customer.setHomeBranch(branch);
        customer.setKycStatus(KycStatus.PENDING);
        customers.save(customer);

        Account account = accountService.createAccount(customer, accountType, branch);
        return new Created(customer, account);
    }

    private Branch activeBranch(String code) {
        return branches.findByCode(code).filter(Branch::isActive)
                .orElseThrow(() -> BankException.notFound("Branch", code));
    }

    private record NewCustomer(String fullName, String email, String phone, LocalDate dateOfBirth, String pan,
                               String aadhaar, String addressLine, String city, String state, String pincode) {
    }

    private record Created(Customer customer, Account account) {
    }
}
