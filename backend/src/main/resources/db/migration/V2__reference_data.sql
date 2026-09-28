-- Reference data every environment needs (demo users/accounts live in db/seed).

INSERT INTO account_policies (account_type, interest_rate, minimum_balance, daily_transfer_limit, updated_at)
VALUES ('SAVINGS', 3.50, 1000.00, 200000.00, UTC_TIMESTAMP(6)),
       ('CURRENT', 0.00, 5000.00, 1000000.00, UTC_TIMESTAMP(6)),
       -- For FDs, minimum_balance is the minimum principal; FDs cannot send transfers.
       ('FIXED_DEPOSIT', 6.75, 10000.00, 0.00, UTC_TIMESTAMP(6));

INSERT INTO loan_products (loan_type, interest_rate, min_amount, max_amount, min_tenure_months, max_tenure_months, updated_at)
VALUES ('PERSONAL', 11.50, 50000.00, 2500000.00, 12, 60, UTC_TIMESTAMP(6)),
       ('HOME', 8.50, 500000.00, 50000000.00, 60, 360, UTC_TIMESTAMP(6)),
       ('EDUCATION', 9.25, 100000.00, 5000000.00, 12, 120, UTC_TIMESTAMP(6));

INSERT INTO number_sequences (name, next_value)
VALUES ('ACCOUNT', 1000001),
       ('CUSTOMER', 10000001),
       ('LOAN', 10000001),
       ('EMPLOYEE', 1001);

INSERT INTO branches (code, name, ifsc, address_line, city, state, pincode, phone, active, created_at, updated_at)
VALUES ('000001', 'Head Office - Fort', 'KOSH0000001', '14 Horniman Circle, Fort', 'Mumbai', 'Maharashtra', '400001',
        '02240000001', TRUE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6));
