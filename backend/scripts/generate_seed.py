#!/usr/bin/env python3
"""
Generates src/main/resources/db/seed/V2_1__demo_data.sql - the demo data loaded in the dev profile
(1 admin, 2 employees, 5 customers, their accounts, ~6 months of transactions, FDs, loans).

Why a generator instead of hand-written SQL: every ledger row stores balance_after, and those
running balances, the EMI schedule and the FD maturity amounts must match what the Java code
computes. Doing that arithmetic by hand is error-prone; Python's Decimal does it exactly.

All dates are relative to the moment the migration runs (@today), so the dashboard always shows
"recent" activity. Run from backend/:  python scripts/generate_seed.py
"""
from decimal import Decimal as D, getcontext, ROUND_HALF_EVEN, ROUND_HALF_UP
from pathlib import Path
import random

getcontext().prec = 34            # same as Java's MathContext.DECIMAL128
getcontext().rounding = ROUND_HALF_EVEN
random.seed(20260928)             # deterministic output

OUT = Path(__file__).resolve().parent.parent / "src/main/resources/db/seed/V2_1__demo_data.sql"

HASH = {  # BCrypt(12) of the demo passwords documented in the README
    "ADMIN": "$2a$12$Y9VA3zQmM0GS7LCPzhiaB.A7tsTWwnrXWNmnZhSIlIVxrNPCezhra",       # Admin@123
    "EMPLOYEE": "$2a$12$7lDFTSrfDKUcEltIkbDaweVhqiM52t/7t/pIr/TRNQLMg4DWtTZaG",    # Employee@123
    "CUSTOMER": "$2a$12$MfaLjd92tUGaC3sYeq83lO2A9K61NkBKoJbTYNo0.zxyNvBC.SxmO",    # Customer@123
}
REF_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
TWO = D("0.01")


def q(value):
    if value is None:
        return "NULL"
    if isinstance(value, (int, D)):
        return str(value)
    return "'" + str(value).replace("\\", "\\\\").replace("'", "''") + "'"


def days_ago(n):
    return f"DATE_SUB(@today, INTERVAL {n} DAY)"


def at(date_sql, time="05:00:00"):
    """UTC timestamp on a business date (05:00 UTC = 10:30 IST)."""
    return f"TIMESTAMP({date_sql}, '{time}')"


def luhn(body):
    total, double = 0, True
    for ch in reversed(body):
        d = int(ch)
        if double:
            d *= 2
            if d > 9:
                d -= 9
        total += d
        double = not double
    return (10 - total % 10) % 10


def account_number(branch_code, serial):
    body = branch_code[-4:] + f"{serial:07d}"
    return body + str(luhn(body))


def money(value):
    return D(value).quantize(TWO)


# ---------------------------------------------------------------- maths (mirrors the Java calculators)

def emi(principal, rate, months):
    r = D(rate) / D(1200)
    growth = (1 + r) ** months
    return (D(principal) * r * growth / (growth - 1)).quantize(TWO, rounding=ROUND_HALF_UP)


def schedule(principal, rate, months):
    payment = emi(principal, rate, months)
    r = D(rate) / D(1200)
    outstanding = D(principal)
    rows = []
    for k in range(1, months + 1):
        interest = (outstanding * r).quantize(TWO, rounding=ROUND_HALF_UP)
        last = k == months
        principal_part = outstanding if last else min(payment - interest, outstanding)
        amount = principal_part + interest if last else payment
        outstanding -= principal_part
        rows.append((k, amount, principal_part, interest, outstanding))
    return rows


def fd_maturity(principal, rate, months):
    quarters, broken = divmod(months, 3)
    amount = D(principal) * (1 + D(rate) / D(400)) ** quarters
    if broken:
        amount += amount * D(rate) * broken / D(1200)
    return amount.quantize(TWO, rounding=ROUND_HALF_EVEN)


# ---------------------------------------------------------------- reference data

BRANCH_CODE = {1: "000001", 2: "000002", 3: "000003"}
IFSC = {1: "KOSH0000001", 2: "KOSH0000002", 3: "KOSH0000003"}

USERS = [  # id, email, role, name, phone, created days ago, last login days ago
    (1, "admin@koshbank.test", "ADMIN", "Aditi Rao", "9876500001", 400, 1),
    (2, "priya.nair@koshbank.test", "EMPLOYEE", "Priya Nair", "9876500002", 380, 1),
    (3, "rahul.verma@koshbank.test", "EMPLOYEE", "Rahul Verma", "9876500003", 360, 2),
    (4, "ananya.sharma@example.com", "CUSTOMER", "Ananya Sharma", "9820012345", 166, 1),
    (5, "vikram.singh@example.com", "CUSTOMER", "Vikram Singh", "9810023456", 159, 5),
    (6, "meera.iyer@example.com", "CUSTOMER", "Meera Iyer", "9845034567", 153, 4),
    (7, "arjun.reddy@example.com", "CUSTOMER", "Arjun Reddy", "9886045678", 143, 2),
    (8, "kavya.patel@example.com", "CUSTOMER", "Kavya Patel", "9833056789", 3, None),
]
NAME = {u[0]: u[3] for u in USERS}

# id, user, CIF, dob, PAN, Aadhaar, address, city, state, pincode, branch, KYC, reviewer, reviewed days ago
CUSTOMERS = [
    (1, 4, "CIF10000001", "1994-03-12", "ABKPS4821M", "482915736024", "12 Sea Breeze Apartments, Bandra West", "Mumbai", "Maharashtra", "400050", 1, "VERIFIED", 2, 165),
    (2, 5, "CIF10000002", "1988-11-02", "BVRPS7314L", "593816247105", "44 Lodhi Estate", "New Delhi", "Delhi", "110003", 3, "VERIFIED", 2, 158),
    (3, 6, "CIF10000003", "1991-07-25", "CMIPI2257Q", "614927358216", "7 Indiranagar 2nd Stage", "Bengaluru", "Karnataka", "560038", 2, "VERIFIED", 3, 152),
    (4, 7, "CIF10000004", "1985-01-19", "DARPR9086T", "725038469327", "221 Whitefield Main Road", "Bengaluru", "Karnataka", "560066", 2, "VERIFIED", 3, 142),
    (5, 8, "CIF10000005", "1999-09-30", "EKVPP3349D", "836149570438", "5 Powai Lake View, Hiranandani Gardens", "Mumbai", "Maharashtra", "400076", 1, "PENDING", None, None),
]
CUSTOMER_USER = {c[0]: c[1] for c in CUSTOMERS}

VIKRAM_FD_START = "@vikram_fd_start"
# id: (customer, branch, type, opened date sql)
ACCOUNTS = {
    1: (1, 1, "SAVINGS", days_ago(165)),
    2: (2, 3, "SAVINGS", days_ago(158)),
    3: (2, 3, "CURRENT", days_ago(152)),
    4: (3, 2, "SAVINGS", days_ago(152)),
    5: (4, 2, "SAVINGS", days_ago(142)),
    6: (5, 1, "SAVINGS", days_ago(3)),
    7: (1, 1, "FIXED_DEPOSIT", days_ago(75)),
    8: (2, 3, "FIXED_DEPOSIT", VIKRAM_FD_START),
}
NUMBER = {aid: account_number(BRANCH_CODE[branch], 1000000 + aid) for aid, (_, branch, _, _) in ACCOUNTS.items()}
HOLDER = {aid: NAME[CUSTOMER_USER[cust]] for aid, (cust, _, _, _) in ACCOUNTS.items()}
ACCT_IFSC = {aid: IFSC[branch] for aid, (_, branch, _, _) in ACCOUNTS.items()}
OWNER_USER = {aid: CUSTOMER_USER[cust] for aid, (cust, _, _, _) in ACCOUNTS.items()}

EXTERNAL = {  # name: (account, ifsc, bank)
    "Rohan Kapoor": ("918273645501", "NOVA0004521", "Nova Bank"),
    "Swift Broadband Ltd": ("30211457789", "ZETA0001188", "Zeta Bank"),
    "Green Basket Grocers": ("30277120045", "ZETA0001188", "Zeta Bank"),
    "Sharma Traders": ("30211990012", "ZETA0001188", "Zeta Bank"),
    "BuildRight Interiors": ("30299887766", "ZETA0002207", "Zeta Bank"),
    "Sneha Reddy": ("918200334411", "NOVA0004521", "Nova Bank"),
    "Nimbus Technologies Pvt Ltd": ("50200011223344", "NOVA0000001", "Nova Bank"),
    "Orbit Analytics LLP": ("50200055667788", "NOVA0000001", "Nova Bank"),
    "Quantum Infra Ltd": ("50200099887766", "ZETA0000001", "Zeta Bank"),
}

# ---------------------------------------------------------------- ledger events

events = []   # (sort_day, seq, legs); a leg is a dict describing one transactions row
_seq = 0


def ref(date_sql):
    return "CONCAT('TXN', DATE_FORMAT(%s, '%%y%%m%%d'), '%s')" % (
        date_sql, "".join(random.choice(REF_ALPHABET) for _ in range(10)))


def add(day, legs, date_sql=None, time="05:00:00"):
    global _seq
    _seq += 1
    date_sql = date_sql or days_ago(day)
    reference = ref(date_sql)
    for leg in legs:
        leg.setdefault("reference", reference)
        leg["date"] = date_sql
        leg["created"] = at(date_sql, time)
    events.append((-day, _seq, legs))


def leg(account, ttype, direction, amount, description, channel="ONLINE", cp=None, initiated_by=None,
        status="SUCCESS", failure=None):
    cp_account, cp_name, cp_ifsc = cp if cp else (None, None, None)
    return dict(account=account, type=ttype, direction=direction, amount=money(amount), description=description,
                channel=channel, cp_account=cp_account, cp_name=cp_name, cp_ifsc=cp_ifsc,
                initiated_by=initiated_by, status=status, failure=failure)


def own(aid):
    return NUMBER[aid], HOLDER[aid], ACCT_IFSC[aid]


def ext(name):
    account, ifsc, _ = EXTERNAL[name]
    return account, name, ifsc


def cash_in(day, aid, amount, description="Cash deposit", teller=2, time="05:30:00"):
    add(day, [leg(aid, "DEPOSIT", "CREDIT", amount, description, "BRANCH", initiated_by=teller)], time=time)


def cash_out(day, aid, amount, description="Cash withdrawal", teller=2, time="07:00:00"):
    add(day, [leg(aid, "WITHDRAWAL", "DEBIT", amount, description, "BRANCH", initiated_by=teller)], time=time)


def pay(day, src, dst, amount, description, time="06:00:00"):
    """Beneficiary transfer inside the bank: two legs, one reference."""
    user = OWNER_USER[src]
    add(day, [
        leg(src, "TRANSFER_OUT", "DEBIT", amount, description, cp=own(dst), initiated_by=user),
        leg(dst, "TRANSFER_IN", "CREDIT", amount, "Transfer from " + HOLDER[src], cp=own(src), initiated_by=user),
    ], time=time)


def self_transfer(day, src, dst, amount, time="06:30:00"):
    user = OWNER_USER[src]
    holder = HOLDER[src]
    add(day, [
        leg(src, "TRANSFER_OUT", "DEBIT", amount, "Own account transfer", cp=(NUMBER[dst], holder, ACCT_IFSC[dst]), initiated_by=user),
        leg(dst, "TRANSFER_IN", "CREDIT", amount, "Own account transfer", cp=(NUMBER[src], holder, ACCT_IFSC[src]), initiated_by=user),
    ], time=time)


def neft_out(day, src, payee, amount, description=None, time="08:00:00"):
    add(day, [leg(src, "TRANSFER_OUT", "DEBIT", amount, description or "NEFT to " + payee, cp=ext(payee),
                  initiated_by=OWNER_USER[src])], time=time)


def neft_in(day, dst, payer, amount, description, time="04:30:00"):
    add(day, [leg(dst, "TRANSFER_IN", "CREDIT", amount, description, cp=ext(payer))], time=time)


# Ananya Sharma: salaried, pays rent to Vikram, broadband bill, a friend at another bank.
cash_in(165, 1, 25000, "Account opening deposit")
for d in (150, 120, 90, 60, 30):
    neft_in(d, 1, "Nimbus Technologies Pvt Ltd", 85000, "Salary - Nimbus Technologies")
for d in (145, 115, 85, 55, 25):
    pay(d, 1, 2, 18000, "House rent")
for d in (132, 110, 80, 50, 20):
    neft_out(d, 1, "Swift Broadband Ltd", 1199, "Broadband bill - Swift Broadband")
cash_out(140, 1, 5000)
neft_out(100, 1, "Rohan Kapoor", 6500, "Goa trip share")
cash_out(70, 1, 4000)
pay(45, 1, 4, 12000, "Birthday gift for Meera")
cash_out(12, 1, 3000)
neft_out(8, 1, "Rohan Kapoor", 7850, "Concert tickets")
neft_out(3, 1, "Green Basket Grocers", 3420, "Monthly groceries")

# Vikram Singh: landlord (savings) with a trading business (current account).
cash_in(158, 2, 40000, "Account opening deposit", teller=2)
self_transfer(140, 2, 3, 30000)
cash_out(95, 2, 10000)
self_transfer(40, 2, 3, 25000)
cash_out(10, 2, 8000)
cash_in(150, 3, 150000, "Business capital", teller=2)
neft_out(130, 3, "Sharma Traders", 42000, "Stock purchase - invoice ST/0412")
cash_in(100, 3, 65000, "Counter sales deposit")
neft_out(70, 3, "Sharma Traders", 38500, "Stock purchase - invoice ST/0519")
cash_in(35, 3, 72000, "Counter sales deposit")
neft_out(15, 3, "Sharma Traders", 44750, "Stock purchase - invoice ST/0631")
cash_in(5, 3, 23000, "Counter sales deposit")

# Meera Iyer: salaried, took a personal loan for home renovation.
LOAN_PRINCIPAL, LOAN_RATE, LOAN_MONTHS = 200000, "11.50", 12
cash_in(152, 4, 30000, "Account opening deposit", teller=3)
for d in (148, 120, 90, 60, 30):
    neft_in(d, 4, "Orbit Analytics LLP", 72000, "Salary - Orbit Analytics")
cash_out(110, 4, 8000, teller=3)
add(95, [leg(4, "LOAN_DISBURSEMENT", "CREDIT", LOAN_PRINCIPAL, "Loan LN10000001 disbursed", "SYSTEM")],
    date_sql="@disbursal", time="06:00:00")
neft_out(93, 4, "BuildRight Interiors", 180000, "Home renovation - BuildRight Interiors")
cash_out(18, 4, 6000, teller=3)
MEERA_SCHEDULE = schedule(LOAN_PRINCIPAL, LOAN_RATE, LOAN_MONTHS)
PAID_EMIS = 3
EMI_REFS = {}
for k, amount, _, _, _ in MEERA_SCHEDULE[:PAID_EMIS]:
    date_sql = f"DATE_ADD(@disbursal, INTERVAL {k} MONTH)"
    add(95 - round(30.4 * k), [leg(4, "EMI_DEBIT", "DEBIT", amount,
                                   f"EMI {k}/{LOAN_MONTHS} for loan LN10000001", "SYSTEM")],
        date_sql=date_sql, time="00:30:00")
    EMI_REFS[k] = events[-1][2][0]["reference"]

# Arjun Reddy: senior engineer, applying for a home loan.
cash_in(142, 5, 60000, "Account opening deposit", teller=3)
for d in (120, 90, 60, 30):
    neft_in(d, 5, "Quantum Infra Ltd", 110000, "Salary - Quantum Infra")
neft_out(100, 5, "Sneha Reddy", 35000, "Family support")
cash_out(70, 5, 15000, teller=3)
neft_out(20, 5, "Sneha Reddy", 22000, "Family support")
cash_out(9, 5, 12000, teller=3)

# Fixed deposits: Ananya's 12-month FD; Vikram's 3-month FD that matured yesterday, so the
# maturity job has something to pay out in a demo.
FD_RATE = "6.75"
ANANYA_FD = (7, 1, 50000, 12, days_ago(75), f"DATE_ADD({days_ago(75)}, INTERVAL 12 MONTH)")
VIKRAM_FD = (8, 3, 100000, 3, VIKRAM_FD_START, "DATE_SUB(@today, INTERVAL 1 DAY)")
for fd_account, source, principal, _, start, _ in (ANANYA_FD, VIKRAM_FD):
    user = OWNER_USER[source]
    holder = HOLDER[source]
    day = 75 if fd_account == 7 else 92
    add(day, [
        leg(source, "FD_BOOKING", "DEBIT", principal, "Fixed deposit booked",
            cp=(NUMBER[fd_account], holder, ACCT_IFSC[fd_account]), initiated_by=user),
        leg(fd_account, "FD_BOOKING", "CREDIT", principal, "Fixed deposit opened",
            cp=(NUMBER[source], holder, ACCT_IFSC[source]), initiated_by=user),
    ], date_sql=start, time="09:00:00")

# ---------------------------------------------------------------- post the ledger

balances = {aid: D("0.00") for aid in ACCOUNTS}
rows = []
MINIMUM = {"SAVINGS": D(1000), "CURRENT": D(5000), "FIXED_DEPOSIT": D(0)}
for _, _, legs in sorted(events, key=lambda e: (e[0], e[1])):
    for item in legs:
        aid = item["account"]
        if item["status"] == "SUCCESS":
            delta = item["amount"] if item["direction"] == "CREDIT" else -item["amount"]
            balances[aid] += delta
            if balances[aid] < 0:
                raise SystemExit(f"Account {aid} would go negative at {item['description']}")
            if item["type"] not in ("EMI_DEBIT", "FD_MATURITY") and item["direction"] == "DEBIT" \
                    and balances[aid] < MINIMUM[ACCOUNTS[aid][2]]:
                raise SystemExit(f"Account {aid} breaches its minimum balance at {item['description']}")
        item["balance_after"] = balances[aid]
        rows.append(item)
    # A declined transfer (daily limit) recorded right after Ananya's concert-ticket payment.
    if legs[0]["description"] == "Concert tickets":
        rows.append(dict(account=1, type="TRANSFER_OUT", direction="DEBIT", amount=money(250000),
                         description="Beneficiary transfer (declined)", channel="ONLINE",
                         cp_account=None, cp_name=None, cp_ifsc=None, initiated_by=4, status="FAILED",
                         failure="Daily transfer limit of ₹2,00,000.00 exceeded; ₹2,00,000.00 remaining today",
                         balance_after=balances[1], reference=ref(days_ago(7)),
                         date=days_ago(7), created=at(days_ago(7), "11:15:00")))

# ---------------------------------------------------------------- emit SQL

out = []
w = out.append
w("-- GENERATED by backend/scripts/generate_seed.py - edit the script, not this file.")
w("-- Demo data for the dev profile only (never on the prod Flyway path).")
w("-- Passwords: Admin@123 (admin), Employee@123 (employees), Customer@123 (customers).")
w("")
w("SET @now = UTC_TIMESTAMP(6);")
w("SET @today = DATE(CONVERT_TZ(UTC_TIMESTAMP(), '+00:00', '+05:30'));")
w("SET @disbursal = DATE_SUB(@today, INTERVAL 95 DAY);")
w(f"SET {VIKRAM_FD_START} = DATE_SUB(DATE_SUB(@today, INTERVAL 1 DAY), INTERVAL 3 MONTH);")
w("")
w("INSERT INTO branches (id, code, name, ifsc, address_line, city, state, pincode, phone, active, created_at, updated_at) VALUES")
w(",\n".join([
    f"    (2, '000002', 'Koramangala', 'KOSH0000002', '80 Feet Road, 4th Block, Koramangala', 'Bengaluru', 'Karnataka', '560034', '08040000002', TRUE, {at(days_ago(420))}, {at(days_ago(420))})",
    f"    (3, '000003', 'Connaught Place', 'KOSH0000003', 'Block B, Inner Circle, Connaught Place', 'New Delhi', 'Delhi', '110001', '01140000003', TRUE, {at(days_ago(410))}, {at(days_ago(410))})",
]) + ";")
w("")
w("INSERT INTO users (id, email, password_hash, full_name, phone, role, enabled, failed_login_attempts, last_login_at, password_changed_at, created_at, updated_at) VALUES")
w(",\n".join(
    f"    ({uid}, {q(email)}, {q(HASH[role])}, {q(name)}, {q(phone)}, {q(role)}, TRUE, 0, "
    f"{at(days_ago(login), '04:00:00') if login else 'NULL'}, {at(days_ago(created))}, {at(days_ago(created))}, {at(days_ago(created))})"
    for uid, email, role, name, phone, created, login in USERS) + ";")
w("")
w("INSERT INTO employees (id, user_id, employee_code, branch_id, designation, created_at, updated_at) VALUES")
w(f"    (1, 2, 'EMP1001', 1, 'Teller', {at(days_ago(380))}, {at(days_ago(380))}),")
w(f"    (2, 3, 'EMP1002', 2, 'Loan Officer', {at(days_ago(360))}, {at(days_ago(360))});")
w("")
w("INSERT INTO customers (id, user_id, customer_number, date_of_birth, pan_number, aadhaar_number, address_line, city, state, pincode, home_branch_id, kyc_status, kyc_remarks, kyc_reviewed_by, kyc_reviewed_at, created_at, updated_at) VALUES")
cust_rows = []
for cid, uid, cif, dob, pan, aadhaar, address, city, state, pin, branch, kyc, reviewer, reviewed in CUSTOMERS:
    created = [u for u in USERS if u[0] == uid][0][5]
    remarks = "PAN and Aadhaar verified in person" if kyc == "VERIFIED" else None
    cust_rows.append(
        f"    ({cid}, {uid}, {q(cif)}, {q(dob)}, {q(pan)}, {q(aadhaar)}, {q(address)}, {q(city)}, {q(state)}, {q(pin)}, "
        f"{branch}, {q(kyc)}, {q(remarks)}, {q(reviewer)}, {at(days_ago(reviewed), '09:30:00') if reviewed else 'NULL'}, "
        f"{at(days_ago(created))}, {at(days_ago(created))})")
w(",\n".join(cust_rows) + ";")
w("")
w("INSERT INTO accounts (id, account_number, customer_id, branch_id, account_type, status, balance, currency, opened_at, created_at, updated_at, version) VALUES")
w(",\n".join(
    f"    ({aid}, {q(NUMBER[aid])}, {cust}, {branch}, {q(atype)}, 'ACTIVE', {balances[aid]}, 'INR', "
    f"{at(opened, '04:45:00')}, {at(opened, '04:45:00')}, @now, 0)"
    for aid, (cust, branch, atype, opened) in ACCOUNTS.items()) + ";")
w("")
w("INSERT INTO fixed_deposits (id, account_id, payout_account_id, principal, interest_rate, tenure_months, start_date, maturity_date, maturity_amount, status, created_at, updated_at) VALUES")
fd_rows = []
for fid, (fd_account, source, principal, months, start, maturity) in enumerate((ANANYA_FD, VIKRAM_FD), start=1):
    fd_rows.append(
        f"    ({fid}, {fd_account}, {source}, {money(principal)}, {FD_RATE}, {months}, {start}, {maturity}, "
        f"{fd_maturity(principal, FD_RATE, months)}, 'ACTIVE', {at(start, '09:00:00')}, {at(start, '09:00:00')})")
w(",\n".join(fd_rows) + ";")
w("")
w("INSERT INTO transactions (id, reference_number, account_id, type, direction, amount, balance_after, status, channel, description, counterparty_account, counterparty_name, counterparty_ifsc, failure_reason, initiated_by, value_date, created_at) VALUES")
txn_rows = []
for tid, r in enumerate(rows, start=1):
    txn_rows.append(
        f"    ({tid}, {r['reference']}, {r['account']}, {q(r['type'])}, {q(r['direction'])}, {r['amount']}, "
        f"{r['balance_after']}, {q(r['status'])}, {q(r['channel'])}, {q(r['description'])}, {q(r['cp_account'])}, "
        f"{q(r['cp_name'])}, {q(r['cp_ifsc'])}, {q(r['failure'])}, {q(r['initiated_by'])}, {r['date']}, {r['created']})")
w(",\n".join(txn_rows) + ";")
w("")
w("INSERT INTO beneficiaries (id, customer_id, name, nickname, account_number, ifsc, bank_name, internal, activated_at, created_at, updated_at) VALUES")
bens = [
    (1, 1, "Vikram Singh", "Landlord", NUMBER[2], ACCT_IFSC[2], "Kosh Bank", True, 160),
    (2, 1, "Meera Iyer", None, NUMBER[4], ACCT_IFSC[4], "Kosh Bank", True, 60),
    (3, 1, "Rohan Kapoor", "Rohan", *EXTERNAL["Rohan Kapoor"], False, 120),
    (4, 1, "Swift Broadband Ltd", "Broadband", *EXTERNAL["Swift Broadband Ltd"], False, 150),
    (5, 1, "Green Basket Grocers", None, *EXTERNAL["Green Basket Grocers"], False, 30),
    (6, 2, "Sharma Traders", "Supplier", *EXTERNAL["Sharma Traders"], False, 140),
    (7, 3, "BuildRight Interiors", None, *EXTERNAL["BuildRight Interiors"], False, 100),
    (8, 4, "Sneha Reddy", "Sister", *EXTERNAL["Sneha Reddy"], False, 110),
]
w(",\n".join(
    f"    ({bid}, {cid}, {q(name)}, {q(nick)}, {q(acct)}, {q(ifsc)}, {q(bank)}, {'TRUE' if internal else 'FALSE'}, "
    f"DATE_ADD({at(days_ago(d))}, INTERVAL 30 MINUTE), {at(days_ago(d))}, {at(days_ago(d))})"
    for bid, cid, name, nick, acct, ifsc, bank, internal, d in bens) + ";")
w("")

# Loans
paid_closing = MEERA_SCHEDULE[PAID_EMIS - 1][4]
loans = [
    # id, number, customer, account, type, principal, rate, months, status, applied, reviewer, reviewed, remarks, disbursed, outstanding, purpose
    (1, "LN10000001", 3, 4, "PERSONAL", 200000, LOAN_RATE, LOAN_MONTHS, "ACTIVE", days_ago(97), 3, "@disbursal",
     "Salaried applicant with stable income; approved", "@disbursal", paid_closing, "Home renovation"),
    (2, "LN10000002", 2, 2, "EDUCATION", 800000, "9.25", 96, "REJECTED", days_ago(25), 3, days_ago(20),
     "Admission letter and co-applicant income proof missing", None, 800000, "MBA programme fees"),
    (3, "LN10000003", 4, 5, "HOME", 3500000, "8.50", 240, "PENDING", days_ago(2), None, None, None, None, 3500000,
     "Purchase of a 2BHK apartment in Whitefield"),
    (4, "LN10000004", 1, 1, "PERSONAL", 150000, "11.50", 24, "PENDING", days_ago(1), None, None, None, None, 150000,
     "Home appliances and furnishing"),
]
w("INSERT INTO loans (id, loan_number, customer_id, account_id, loan_type, principal, interest_rate, tenure_months, emi_amount, outstanding_principal, purpose, status, applied_at, reviewed_by, reviewed_at, review_remarks, disbursed_at, created_at, updated_at, version) VALUES")
w(",\n".join(
    f"    ({lid}, {q(num)}, {cid}, {aid}, {q(ltype)}, {money(p)}, {rate}, {n}, {emi(p, rate, n)}, {money(out)}, {q(purpose)}, "
    f"{q(status)}, {at(applied, '08:00:00')}, {q(reviewer)}, {at(reviewed, '06:00:00') if reviewed else 'NULL'}, {q(remarks)}, "
    f"{at(disbursed, '06:00:00') if disbursed else 'NULL'}, {at(applied, '08:00:00')}, @now, 0)"
    for lid, num, cid, aid, ltype, p, rate, n, status, applied, reviewer, reviewed, remarks, disbursed, out, purpose in loans) + ";")
w("")
w("INSERT INTO loan_installments (loan_id, installment_number, due_date, emi_amount, principal_component, interest_component, closing_principal, status, paid_at, transaction_ref, attempts, created_at, updated_at) VALUES")
inst_rows = []
for k, amount, principal_part, interest, closing in MEERA_SCHEDULE:
    due = f"DATE_ADD(@disbursal, INTERVAL {k} MONTH)"
    paid = k <= PAID_EMIS
    inst_rows.append(
        f"    (1, {k}, {due}, {amount}, {principal_part}, {interest}, {closing}, {q('PAID' if paid else 'PENDING')}, "
        f"{at(due, '00:30:00') if paid else 'NULL'}, {EMI_REFS[k] if paid else 'NULL'}, {1 if paid else 0}, "
        f"{at('@disbursal', '06:00:00')}, {at(due, '00:30:00') if paid else at('@disbursal', '06:00:00')})")
w(",\n".join(inst_rows) + ";")
w("")
w("INSERT INTO notifications (user_id, type, title, message, is_read, created_at) VALUES")
notes = [
    (4, "GENERAL", "Welcome aboard", f"Your customer ID is CIF10000001 and your savings account number is {NUMBER[1]}.", True, 165),
    (4, "KYC", "KYC verified", "Your KYC has been verified. Your accounts are now fully active.", True, 165),
    (4, "ACCOUNT", "Fixed deposit booked", f"Your FD {NUMBER[7]} of ₹50,000.00 at 6.75% p.a. is active.", True, 75),
    (4, "TRANSACTION", "Debit alert", "₹7,850.00 debited to Rohan Kapoor (XXXXXXXX5501).", False, 8),
    (4, "TRANSACTION", "Debit alert", "₹3,420.00 debited to Green Basket Grocers (XXXXXXX0045).", False, 3),
    (4, "LOAN", "Loan application received", "Your personal loan application LN10000004 for ₹1,50,000.00 is under review.", False, 1),
    (5, "LOAN", "Loan application declined", "Your loan application LN10000002 was not approved. Remarks: Admission letter and co-applicant income proof missing", False, 20),
    (6, "LOAN", "Loan approved and disbursed", "Your loan LN10000001 of ₹2,00,000.00 has been credited to your savings account.", True, 95),
    (6, "LOAN", "EMI paid", "EMI 3 for loan LN10000001 was debited from your savings account.", False, 4),
    (7, "LOAN", "Loan application received", "Your home loan application LN10000003 for ₹35,00,000.00 is under review.", False, 2),
    (8, "GENERAL", "Welcome aboard", f"Your customer ID is CIF10000005 and your savings account number is {NUMBER[6]}. Visit your branch to complete KYC.", False, 3),
]
w(",\n".join(
    f"    ({uid}, {q(t)}, {q(title)}, {q(msg)}, {'TRUE' if read else 'FALSE'}, {at(days_ago(d), '06:00:00')})"
    for uid, t, title, msg, read, d in notes) + ";")
w("")
w("INSERT INTO audit_logs (actor_id, actor_email, actor_role, action, entity_type, entity_id, outcome, details, ip_address, created_at) VALUES")
audits = [
    (2, "priya.nair@koshbank.test", "EMPLOYEE", "KYC_STATUS_CHANGED", "CUSTOMER", "1", "SUCCESS", "status=VERIFIED", 165),
    (2, "priya.nair@koshbank.test", "EMPLOYEE", "KYC_STATUS_CHANGED", "CUSTOMER", "2", "SUCCESS", "status=VERIFIED", 158),
    (3, "rahul.verma@koshbank.test", "EMPLOYEE", "KYC_STATUS_CHANGED", "CUSTOMER", "3", "SUCCESS", "status=VERIFIED", 152),
    (3, "rahul.verma@koshbank.test", "EMPLOYEE", "LOAN_APPROVED", "LOAN", "1", "SUCCESS", "Salaried applicant with stable income; approved", 95),
    (3, "rahul.verma@koshbank.test", "EMPLOYEE", "LOAN_REJECTED", "LOAN", "2", "SUCCESS", "Admission letter and co-applicant income proof missing", 20),
    (4, "ananya.sharma@example.com", "CUSTOMER", "TRANSFER", "ACCOUNT", "1", "FAILURE", "beneficiary 3, amount=250000.00 | DAILY_LIMIT_EXCEEDED: Daily transfer limit exceeded", 7),
    (None, "arjun.reddy@example.com", None, "LOGIN", "USER", None, "FAILURE", "INVALID_CREDENTIALS: Invalid email or password", 6),
    (None, "kavya.patel@example.com", None, "CUSTOMER_REGISTERED", "CUSTOMER", "CIF10000005", "SUCCESS", None, 3),
    (1, "admin@koshbank.test", "ADMIN", "LOGIN", "USER", None, "SUCCESS", None, 1),
]
w(",\n".join(
    f"    ({q(actor)}, {q(email)}, {q(role)}, {q(action)}, {q(etype)}, {q(eid)}, {q(outcome)}, {q(details)}, '127.0.0.1', {at(days_ago(d), '07:30:00')})"
    for actor, email, role, action, etype, eid, outcome, details, d in audits) + ";")
w("")
w("UPDATE number_sequences SET next_value = 1000009 WHERE name = 'ACCOUNT';")
w("UPDATE number_sequences SET next_value = 10000006 WHERE name = 'CUSTOMER';")
w("UPDATE number_sequences SET next_value = 10000005 WHERE name = 'LOAN';")
w("UPDATE number_sequences SET next_value = 1003 WHERE name = 'EMPLOYEE';")
w("")

OUT.parent.mkdir(parents=True, exist_ok=True)
OUT.write_text("\n".join(out), encoding="utf-8")
print(f"Wrote {OUT} ({len(rows)} ledger rows)")
for aid in ACCOUNTS:
    print(f"  account {aid} {NUMBER[aid]} {ACCOUNTS[aid][2]:<13} {HOLDER[aid]:<14} balance {balances[aid]}")
print("  Meera EMI:", MEERA_SCHEDULE[0][1], " FD maturity:", fd_maturity(50000, FD_RATE, 12), fd_maturity(100000, FD_RATE, 3))
