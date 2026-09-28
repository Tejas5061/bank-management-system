-- =====================================================================
-- Bank Management System - core schema
-- Money is DECIMAL(19,2) everywhere (never FLOAT/DOUBLE).
-- Timestamps are DATETIME(6) stored in UTC; business dates are DATE in
-- the bank's time zone (see app.bank.timezone).
-- =====================================================================

CREATE TABLE branches (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    code         VARCHAR(6)   NOT NULL,
    name         VARCHAR(100) NOT NULL,
    ifsc         VARCHAR(11)  NOT NULL,
    address_line VARCHAR(255) NOT NULL,
    city         VARCHAR(60)  NOT NULL,
    state        VARCHAR(60)  NOT NULL,
    pincode      VARCHAR(6)   NOT NULL,
    phone        VARCHAR(15)  NULL,
    active       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at   DATETIME(6)  NOT NULL,
    updated_at   DATETIME(6)  NOT NULL,
    CONSTRAINT pk_branches PRIMARY KEY (id),
    CONSTRAINT uk_branches_code UNIQUE (code),
    CONSTRAINT uk_branches_ifsc UNIQUE (ifsc)
) ENGINE = InnoDB;

CREATE TABLE users (
    id                    BIGINT       NOT NULL AUTO_INCREMENT,
    email                 VARCHAR(120) NOT NULL,
    password_hash         VARCHAR(100) NOT NULL,
    full_name             VARCHAR(100) NOT NULL,
    phone                 VARCHAR(15)  NOT NULL,
    role                  VARCHAR(20)  NOT NULL,
    enabled               BOOLEAN      NOT NULL DEFAULT TRUE,
    failed_login_attempts INT          NOT NULL DEFAULT 0,
    locked_until          DATETIME(6)  NULL,
    last_login_at         DATETIME(6)  NULL,
    password_changed_at   DATETIME(6)  NULL,
    created_at            DATETIME(6)  NOT NULL,
    updated_at            DATETIME(6)  NOT NULL,
    CONSTRAINT pk_users PRIMARY KEY (id),
    CONSTRAINT uk_users_email UNIQUE (email),
    CONSTRAINT chk_users_role CHECK (role IN ('CUSTOMER', 'EMPLOYEE', 'ADMIN'))
) ENGINE = InnoDB;

CREATE TABLE employees (
    id            BIGINT      NOT NULL AUTO_INCREMENT,
    user_id       BIGINT      NOT NULL,
    employee_code VARCHAR(20) NOT NULL,
    branch_id     BIGINT      NOT NULL,
    designation   VARCHAR(60) NOT NULL,
    created_at    DATETIME(6) NOT NULL,
    updated_at    DATETIME(6) NOT NULL,
    CONSTRAINT pk_employees PRIMARY KEY (id),
    CONSTRAINT uk_employees_user UNIQUE (user_id),
    CONSTRAINT uk_employees_code UNIQUE (employee_code),
    CONSTRAINT fk_employees_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_employees_branch FOREIGN KEY (branch_id) REFERENCES branches (id)
) ENGINE = InnoDB;

CREATE TABLE customers (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    user_id         BIGINT       NOT NULL,
    customer_number VARCHAR(20)  NOT NULL,
    date_of_birth   DATE         NOT NULL,
    pan_number      VARCHAR(10)  NOT NULL,
    aadhaar_number  VARCHAR(12)  NOT NULL,
    address_line    VARCHAR(255) NOT NULL,
    city            VARCHAR(60)  NOT NULL,
    state           VARCHAR(60)  NOT NULL,
    pincode         VARCHAR(6)   NOT NULL,
    home_branch_id  BIGINT       NOT NULL,
    kyc_status      VARCHAR(20)  NOT NULL,
    kyc_remarks     VARCHAR(500) NULL,
    kyc_reviewed_by BIGINT       NULL,
    kyc_reviewed_at DATETIME(6)  NULL,
    created_at      DATETIME(6)  NOT NULL,
    updated_at      DATETIME(6)  NOT NULL,
    CONSTRAINT pk_customers PRIMARY KEY (id),
    CONSTRAINT uk_customers_user UNIQUE (user_id),
    CONSTRAINT uk_customers_number UNIQUE (customer_number),
    CONSTRAINT uk_customers_pan UNIQUE (pan_number),
    CONSTRAINT uk_customers_aadhaar UNIQUE (aadhaar_number),
    CONSTRAINT fk_customers_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_customers_branch FOREIGN KEY (home_branch_id) REFERENCES branches (id),
    CONSTRAINT fk_customers_kyc_reviewer FOREIGN KEY (kyc_reviewed_by) REFERENCES users (id),
    CONSTRAINT chk_customers_kyc CHECK (kyc_status IN ('PENDING', 'VERIFIED', 'REJECTED')),
    INDEX idx_customers_kyc_status (kyc_status, created_at)
) ENGINE = InnoDB;

-- Per-account-type rules the admin can change at runtime.
CREATE TABLE account_policies (
    account_type         VARCHAR(20)   NOT NULL,
    interest_rate        DECIMAL(5, 2) NOT NULL,
    minimum_balance      DECIMAL(19, 2) NOT NULL,
    daily_transfer_limit DECIMAL(19, 2) NOT NULL,
    updated_by           BIGINT        NULL,
    updated_at           DATETIME(6)   NOT NULL,
    CONSTRAINT pk_account_policies PRIMARY KEY (account_type),
    CONSTRAINT chk_account_policies_rate CHECK (interest_rate >= 0 AND interest_rate <= 30)
) ENGINE = InnoDB;

CREATE TABLE loan_products (
    loan_type         VARCHAR(20)    NOT NULL,
    interest_rate     DECIMAL(5, 2)  NOT NULL,
    min_amount        DECIMAL(19, 2) NOT NULL,
    max_amount        DECIMAL(19, 2) NOT NULL,
    min_tenure_months INT            NOT NULL,
    max_tenure_months INT            NOT NULL,
    updated_by        BIGINT         NULL,
    updated_at        DATETIME(6)    NOT NULL,
    CONSTRAINT pk_loan_products PRIMARY KEY (loan_type),
    CONSTRAINT chk_loan_products_rate CHECK (interest_rate > 0 AND interest_rate <= 40)
) ENGINE = InnoDB;

CREATE TABLE accounts (
    id             BIGINT         NOT NULL AUTO_INCREMENT,
    account_number VARCHAR(20)    NOT NULL,
    customer_id    BIGINT         NOT NULL,
    branch_id      BIGINT         NOT NULL,
    account_type   VARCHAR(20)    NOT NULL,
    status         VARCHAR(20)    NOT NULL,
    status_reason  VARCHAR(255)   NULL,
    balance        DECIMAL(19, 2) NOT NULL DEFAULT 0.00,
    currency       CHAR(3)        NOT NULL DEFAULT 'INR',
    opened_at      DATETIME(6)    NOT NULL,
    closed_at      DATETIME(6)    NULL,
    version        BIGINT         NOT NULL DEFAULT 0,
    created_at     DATETIME(6)    NOT NULL,
    updated_at     DATETIME(6)    NOT NULL,
    CONSTRAINT pk_accounts PRIMARY KEY (id),
    CONSTRAINT uk_accounts_number UNIQUE (account_number),
    CONSTRAINT fk_accounts_customer FOREIGN KEY (customer_id) REFERENCES customers (id),
    CONSTRAINT fk_accounts_branch FOREIGN KEY (branch_id) REFERENCES branches (id),
    -- Last line of defence: even a buggy code path cannot overdraw an account.
    CONSTRAINT chk_accounts_balance CHECK (balance >= 0),
    CONSTRAINT chk_accounts_type CHECK (account_type IN ('SAVINGS', 'CURRENT', 'FIXED_DEPOSIT')),
    CONSTRAINT chk_accounts_status CHECK (status IN ('ACTIVE', 'FROZEN', 'CLOSED')),
    INDEX idx_accounts_customer (customer_id),
    INDEX idx_accounts_type_status (account_type, status)
) ENGINE = InnoDB;

CREATE TABLE fixed_deposits (
    id                BIGINT         NOT NULL AUTO_INCREMENT,
    account_id        BIGINT         NOT NULL,
    payout_account_id BIGINT         NOT NULL,
    principal         DECIMAL(19, 2) NOT NULL,
    interest_rate     DECIMAL(5, 2)  NOT NULL,
    tenure_months     INT            NOT NULL,
    start_date        DATE           NOT NULL,
    maturity_date     DATE           NOT NULL,
    maturity_amount   DECIMAL(19, 2) NOT NULL,
    status            VARCHAR(20)    NOT NULL,
    matured_at        DATETIME(6)    NULL,
    created_at        DATETIME(6)    NOT NULL,
    updated_at        DATETIME(6)    NOT NULL,
    CONSTRAINT pk_fixed_deposits PRIMARY KEY (id),
    CONSTRAINT uk_fixed_deposits_account UNIQUE (account_id),
    CONSTRAINT fk_fixed_deposits_account FOREIGN KEY (account_id) REFERENCES accounts (id),
    CONSTRAINT fk_fixed_deposits_payout FOREIGN KEY (payout_account_id) REFERENCES accounts (id),
    INDEX idx_fixed_deposits_maturity (status, maturity_date)
) ENGINE = InnoDB;

-- Ledger: one row per account movement. A transfer writes two rows (debit + credit)
-- that share a reference number. Rows are never updated.
CREATE TABLE transactions (
    id                   BIGINT         NOT NULL AUTO_INCREMENT,
    reference_number     VARCHAR(24)    NOT NULL,
    account_id           BIGINT         NOT NULL,
    type                 VARCHAR(30)    NOT NULL,
    direction            VARCHAR(6)     NOT NULL,
    amount               DECIMAL(19, 2) NOT NULL,
    balance_after        DECIMAL(19, 2) NOT NULL,
    status               VARCHAR(10)    NOT NULL,
    channel              VARCHAR(10)    NOT NULL,
    description          VARCHAR(255)   NULL,
    counterparty_account VARCHAR(20)    NULL,
    counterparty_name    VARCHAR(100)   NULL,
    counterparty_ifsc    VARCHAR(11)    NULL,
    failure_reason       VARCHAR(255)   NULL,
    initiated_by         BIGINT         NULL,
    value_date           DATE           NOT NULL,
    created_at           DATETIME(6)    NOT NULL,
    CONSTRAINT pk_transactions PRIMARY KEY (id),
    CONSTRAINT uk_transactions_ref_account UNIQUE (reference_number, account_id),
    CONSTRAINT fk_transactions_account FOREIGN KEY (account_id) REFERENCES accounts (id),
    CONSTRAINT chk_transactions_amount CHECK (amount > 0),
    CONSTRAINT chk_transactions_direction CHECK (direction IN ('CREDIT', 'DEBIT')),
    CONSTRAINT chk_transactions_status CHECK (status IN ('SUCCESS', 'FAILED')),
    INDEX idx_transactions_account_date (account_id, value_date, id),
    INDEX idx_transactions_value_date (value_date, status)
) ENGINE = InnoDB;

-- Idempotency-Key bookkeeping. The unique key is the real guard against
-- duplicate transfers; it is inserted inside the transfer's own DB transaction.
CREATE TABLE idempotency_records (
    id              BIGINT      NOT NULL AUTO_INCREMENT,
    user_id         BIGINT      NOT NULL,
    idempotency_key VARCHAR(80) NOT NULL,
    endpoint        VARCHAR(80) NOT NULL,
    request_hash    CHAR(64)    NOT NULL,
    response_status INT         NULL,
    response_body   TEXT        NULL,
    created_at      DATETIME(6) NOT NULL,
    CONSTRAINT pk_idempotency_records PRIMARY KEY (id),
    CONSTRAINT uk_idempotency_user_key UNIQUE (user_id, idempotency_key),
    INDEX idx_idempotency_created (created_at)
) ENGINE = InnoDB;

CREATE TABLE beneficiaries (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    customer_id    BIGINT       NOT NULL,
    name           VARCHAR(100) NOT NULL,
    nickname       VARCHAR(50)  NULL,
    account_number VARCHAR(20)  NOT NULL,
    ifsc           VARCHAR(11)  NOT NULL,
    bank_name      VARCHAR(100) NOT NULL,
    internal       BOOLEAN      NOT NULL,
    activated_at   DATETIME(6)  NOT NULL,
    created_at     DATETIME(6)  NOT NULL,
    updated_at     DATETIME(6)  NOT NULL,
    CONSTRAINT pk_beneficiaries PRIMARY KEY (id),
    CONSTRAINT uk_beneficiaries_customer_account UNIQUE (customer_id, account_number, ifsc),
    CONSTRAINT fk_beneficiaries_customer FOREIGN KEY (customer_id) REFERENCES customers (id)
) ENGINE = InnoDB;

CREATE TABLE loans (
    id                    BIGINT         NOT NULL AUTO_INCREMENT,
    loan_number           VARCHAR(20)    NOT NULL,
    customer_id           BIGINT         NOT NULL,
    account_id            BIGINT         NOT NULL,
    loan_type             VARCHAR(20)    NOT NULL,
    principal             DECIMAL(19, 2) NOT NULL,
    interest_rate         DECIMAL(5, 2)  NOT NULL,
    tenure_months         INT            NOT NULL,
    emi_amount            DECIMAL(19, 2) NOT NULL,
    outstanding_principal DECIMAL(19, 2) NOT NULL,
    purpose               VARCHAR(255)   NOT NULL,
    status                VARCHAR(20)    NOT NULL,
    applied_at            DATETIME(6)    NOT NULL,
    reviewed_by           BIGINT         NULL,
    reviewed_at           DATETIME(6)    NULL,
    review_remarks        VARCHAR(500)   NULL,
    disbursed_at          DATETIME(6)    NULL,
    closed_at             DATETIME(6)    NULL,
    version               BIGINT         NOT NULL DEFAULT 0,
    created_at            DATETIME(6)    NOT NULL,
    updated_at            DATETIME(6)    NOT NULL,
    CONSTRAINT pk_loans PRIMARY KEY (id),
    CONSTRAINT uk_loans_number UNIQUE (loan_number),
    CONSTRAINT fk_loans_customer FOREIGN KEY (customer_id) REFERENCES customers (id),
    CONSTRAINT fk_loans_account FOREIGN KEY (account_id) REFERENCES accounts (id),
    CONSTRAINT fk_loans_reviewer FOREIGN KEY (reviewed_by) REFERENCES users (id),
    CONSTRAINT chk_loans_status CHECK (status IN ('PENDING', 'REJECTED', 'ACTIVE', 'CLOSED')),
    INDEX idx_loans_status (status, applied_at),
    INDEX idx_loans_customer (customer_id)
) ENGINE = InnoDB;

CREATE TABLE loan_installments (
    id                  BIGINT         NOT NULL AUTO_INCREMENT,
    loan_id             BIGINT         NOT NULL,
    installment_number  INT            NOT NULL,
    due_date            DATE           NOT NULL,
    emi_amount          DECIMAL(19, 2) NOT NULL,
    principal_component DECIMAL(19, 2) NOT NULL,
    interest_component  DECIMAL(19, 2) NOT NULL,
    closing_principal   DECIMAL(19, 2) NOT NULL,
    status              VARCHAR(20)    NOT NULL,
    paid_at             DATETIME(6)    NULL,
    transaction_ref     VARCHAR(24)    NULL,
    attempts            INT            NOT NULL DEFAULT 0,
    last_failure_reason VARCHAR(255)   NULL,
    created_at          DATETIME(6)    NOT NULL,
    updated_at          DATETIME(6)    NOT NULL,
    CONSTRAINT pk_loan_installments PRIMARY KEY (id),
    CONSTRAINT uk_loan_installments_number UNIQUE (loan_id, installment_number),
    CONSTRAINT fk_loan_installments_loan FOREIGN KEY (loan_id) REFERENCES loans (id),
    CONSTRAINT chk_loan_installments_status CHECK (status IN ('PENDING', 'PAID', 'OVERDUE')),
    INDEX idx_loan_installments_due (status, due_date)
) ENGINE = InnoDB;

-- Guarantees each savings account is credited interest at most once per month,
-- even if the scheduler fires twice or runs on several instances.
CREATE TABLE interest_postings (
    id              BIGINT         NOT NULL AUTO_INCREMENT,
    account_id      BIGINT         NOT NULL,
    period          CHAR(7)        NOT NULL,
    average_balance DECIMAL(19, 2) NOT NULL,
    interest_rate   DECIMAL(5, 2)  NOT NULL,
    interest_amount DECIMAL(19, 2) NOT NULL,
    transaction_ref VARCHAR(24)    NULL,
    created_at      DATETIME(6)    NOT NULL,
    CONSTRAINT pk_interest_postings PRIMARY KEY (id),
    CONSTRAINT uk_interest_postings_period UNIQUE (account_id, period),
    CONSTRAINT fk_interest_postings_account FOREIGN KEY (account_id) REFERENCES accounts (id)
) ENGINE = InnoDB;

CREATE TABLE notifications (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    user_id    BIGINT       NOT NULL,
    type       VARCHAR(20)  NOT NULL,
    title      VARCHAR(120) NOT NULL,
    message    VARCHAR(500) NOT NULL,
    is_read    BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at DATETIME(6)  NOT NULL,
    CONSTRAINT pk_notifications PRIMARY KEY (id),
    CONSTRAINT fk_notifications_user FOREIGN KEY (user_id) REFERENCES users (id),
    INDEX idx_notifications_user (user_id, is_read, created_at)
) ENGINE = InnoDB;

-- Append-only. Deliberately no foreign keys: audit rows must outlive the rows they
-- describe, and they are written in their own transaction (REQUIRES_NEW), where an
-- FK check could block on a row the caller's transaction still holds locked.
CREATE TABLE audit_logs (
    id          BIGINT        NOT NULL AUTO_INCREMENT,
    actor_id    BIGINT        NULL,
    actor_email VARCHAR(120)  NULL,
    actor_role  VARCHAR(20)   NULL,
    action      VARCHAR(40)   NOT NULL,
    entity_type VARCHAR(40)   NULL,
    entity_id   VARCHAR(120)  NULL,
    outcome     VARCHAR(10)   NOT NULL,
    details     VARCHAR(1000) NULL,
    ip_address  VARCHAR(45)   NULL,
    created_at  DATETIME(6)   NOT NULL,
    CONSTRAINT pk_audit_logs PRIMARY KEY (id),
    INDEX idx_audit_logs_created (created_at),
    INDEX idx_audit_logs_action (action, created_at),
    INDEX idx_audit_logs_actor (actor_id, created_at)
) ENGINE = InnoDB;

-- Opaque refresh tokens, stored as SHA-256 hashes, rotated on every use.
-- family_id links a chain of rotations so reuse of an old token revokes the chain.
CREATE TABLE refresh_tokens (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    user_id     BIGINT      NOT NULL,
    token_hash  CHAR(64)    NOT NULL,
    family_id   CHAR(36)    NOT NULL,
    expires_at  DATETIME(6) NOT NULL,
    revoked_at  DATETIME(6) NULL,
    created_ip  VARCHAR(45) NULL,
    created_at  DATETIME(6) NOT NULL,
    CONSTRAINT pk_refresh_tokens PRIMARY KEY (id),
    CONSTRAINT uk_refresh_tokens_hash UNIQUE (token_hash),
    CONSTRAINT fk_refresh_tokens_user FOREIGN KEY (user_id) REFERENCES users (id),
    INDEX idx_refresh_tokens_user (user_id),
    INDEX idx_refresh_tokens_family (family_id),
    INDEX idx_refresh_tokens_expiry (expires_at)
) ENGINE = InnoDB;

CREATE TABLE password_reset_otps (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    user_id     BIGINT       NOT NULL,
    otp_hash    VARCHAR(100) NOT NULL,
    expires_at  DATETIME(6)  NOT NULL,
    attempts    INT          NOT NULL DEFAULT 0,
    consumed_at DATETIME(6)  NULL,
    created_at  DATETIME(6)  NOT NULL,
    CONSTRAINT pk_password_reset_otps PRIMARY KEY (id),
    CONSTRAINT fk_password_reset_otps_user FOREIGN KEY (user_id) REFERENCES users (id),
    INDEX idx_password_reset_otps_user (user_id, created_at)
) ENGINE = InnoDB;

-- Human-friendly number series (account numbers, customer IDs, loan numbers).
CREATE TABLE number_sequences (
    name       VARCHAR(30) NOT NULL,
    next_value BIGINT      NOT NULL,
    CONSTRAINT pk_number_sequences PRIMARY KEY (name)
) ENGINE = InnoDB;
