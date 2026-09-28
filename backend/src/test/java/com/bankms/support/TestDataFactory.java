package com.bankms.support;

import com.bankms.entity.Account;
import com.bankms.entity.AccountType;
import com.bankms.entity.Branch;
import com.bankms.entity.Customer;
import com.bankms.entity.Employee;
import com.bankms.entity.KycStatus;
import com.bankms.entity.Role;
import com.bankms.entity.TransactionDirection;
import com.bankms.entity.TransactionType;
import com.bankms.entity.User;
import com.bankms.repository.BranchRepository;
import com.bankms.repository.CustomerRepository;
import com.bankms.repository.EmployeeRepository;
import com.bankms.repository.UserRepository;
import com.bankms.security.AuthUser;
import com.bankms.service.AccountService;
import com.bankms.service.NumberGenerator;
import com.bankms.service.ledger.LedgerService;
import com.bankms.service.ledger.PostingDetails;
import com.bankms.util.ReferenceGenerator;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

/** Creates users, customers and funded accounts directly through repositories (fast, no HTTP). */
@TestComponent
public class TestDataFactory {

    public static final String PASSWORD = "Passw0rd!";
    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    private final UserRepository users;
    private final CustomerRepository customers;
    private final EmployeeRepository employees;
    private final BranchRepository branches;
    private final AccountService accountService;
    private final LedgerService ledger;
    private final NumberGenerator numbers;
    private final ReferenceGenerator references;
    private final PasswordEncoder passwordEncoder;
    private final TransactionTemplate tx;

    public TestDataFactory(UserRepository users, CustomerRepository customers, EmployeeRepository employees,
                           BranchRepository branches, AccountService accountService, LedgerService ledger,
                           NumberGenerator numbers, ReferenceGenerator references, PasswordEncoder passwordEncoder,
                           TransactionTemplate tx) {
        this.users = users;
        this.customers = customers;
        this.employees = employees;
        this.branches = branches;
        this.accountService = accountService;
        this.ledger = ledger;
        this.numbers = numbers;
        this.references = references;
        this.passwordEncoder = passwordEncoder;
        this.tx = tx;
    }

    public record TestCustomer(Long userId, Long customerId, String email, List<Long> accountIds) {
        public AuthUser principal() {
            return new AuthUser(userId, email, Role.CUSTOMER);
        }

        public Long account(int index) {
            return accountIds.get(index);
        }
    }

    /** A customer with one savings account per opening balance (0 means an empty account). */
    public TestCustomer customer(KycStatus kyc, String... openingBalances) {
        return tx.execute(status -> {
            User user = users.save(new User(uniqueEmail("customer"), passwordEncoder.encode(PASSWORD),
                    "Test Customer " + SEQUENCE.get(), "9876543210", Role.CUSTOMER));
            Customer customer = new Customer();
            customer.setUser(user);
            customer.setCustomerNumber(numbers.nextCustomerNumber());
            customer.setDateOfBirth(LocalDate.of(1990, 1, 1));
            customer.setPanNumber(uniquePan());
            customer.setAadhaarNumber(uniqueAadhaar());
            customer.setAddressLine("1 Test Street");
            customer.setCity("Mumbai");
            customer.setState("Maharashtra");
            customer.setPincode("400001");
            customer.setHomeBranch(headOffice());
            customer.setKycStatus(kyc);
            customers.save(customer);

            List<Long> accountIds = new ArrayList<>();
            for (String balance : openingBalances) {
                Account account = accountService.createAccount(customer, AccountType.SAVINGS, headOffice());
                BigDecimal amount = new BigDecimal(balance);
                if (amount.signum() > 0) {
                    ledger.post(ledger.lock(account.getId()), TransactionDirection.CREDIT, amount, TransactionType.DEPOSIT,
                            references.next(), PostingDetails.system("Opening balance"));
                }
                accountIds.add(account.getId());
            }
            return new TestCustomer(user.getId(), customer.getId(), user.getEmail(), accountIds);
        });
    }

    public AuthUser employee() {
        return tx.execute(status -> {
            User user = users.save(new User(uniqueEmail("teller"), passwordEncoder.encode(PASSWORD),
                    "Test Teller", "9876500000", Role.EMPLOYEE));
            Employee employee = new Employee();
            employee.setUser(user);
            employee.setEmployeeCode(numbers.nextEmployeeCode());
            employee.setBranch(headOffice());
            employee.setDesignation("Teller");
            employees.save(employee);
            return new AuthUser(user.getId(), user.getEmail(), Role.EMPLOYEE);
        });
    }

    public AuthUser admin() {
        User user = users.save(new User(uniqueEmail("admin"), passwordEncoder.encode(PASSWORD),
                "Test Admin", "9876500009", Role.ADMIN));
        return new AuthUser(user.getId(), user.getEmail(), Role.ADMIN);
    }

    public static String key() {
        return UUID.randomUUID().toString();
    }

    private Branch headOffice() {
        return branches.findByCode("000001").orElseThrow();
    }

    private static String uniqueEmail(String prefix) {
        return prefix + "." + SEQUENCE.incrementAndGet() + "." + UUID.randomUUID().toString().substring(0, 8) + "@test.example";
    }

    private static String uniquePan() {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        StringBuilder pan = new StringBuilder();
        for (int i = 0; i < 5; i++) {
            pan.append((char) ('A' + r.nextInt(26)));
        }
        return pan.append(String.format("%04d", r.nextInt(10_000))).append((char) ('A' + r.nextInt(26))).toString();
    }

    private static String uniqueAadhaar() {
        return String.valueOf(200_000_000_000L + ThreadLocalRandom.current().nextLong(799_999_999_999L));
    }
}
