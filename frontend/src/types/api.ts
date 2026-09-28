// Mirrors the backend DTOs (com.bankms.dto). Money arrives as JSON numbers with two decimals.

export type Role = 'CUSTOMER' | 'EMPLOYEE' | 'ADMIN';
export type KycStatus = 'PENDING' | 'VERIFIED' | 'REJECTED';
export type AccountType = 'SAVINGS' | 'CURRENT' | 'FIXED_DEPOSIT';
export type AccountStatus = 'ACTIVE' | 'FROZEN' | 'CLOSED';
export type TransactionType =
  | 'DEPOSIT'
  | 'WITHDRAWAL'
  | 'TRANSFER_IN'
  | 'TRANSFER_OUT'
  | 'INTEREST_CREDIT'
  | 'FD_BOOKING'
  | 'FD_MATURITY'
  | 'LOAN_DISBURSEMENT'
  | 'EMI_DEBIT';
export type Direction = 'CREDIT' | 'DEBIT';
export type TransactionStatus = 'SUCCESS' | 'FAILED';
export type Channel = 'ONLINE' | 'BRANCH' | 'SYSTEM';
export type LoanType = 'PERSONAL' | 'HOME' | 'EDUCATION';
export type LoanStatus = 'PENDING' | 'REJECTED' | 'ACTIVE' | 'CLOSED';
export type InstallmentStatus = 'PENDING' | 'PAID' | 'OVERDUE';
export type FixedDepositStatus = 'ACTIVE' | 'MATURED';
export type NotificationType = 'TRANSACTION' | 'SECURITY' | 'KYC' | 'LOAN' | 'ACCOUNT' | 'GENERAL';
export type AuditOutcome = 'SUCCESS' | 'FAILURE';

export interface ApiError {
  timestamp: string;
  status: number;
  errorCode: string;
  message: string;
  path: string;
  fieldErrors?: { field: string; message: string }[];
}

export interface Page<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
}

export interface UserSummary {
  id: number;
  email: string;
  fullName: string;
  role: Role;
}

export interface AuthResponse {
  accessToken: string;
  tokenType: string;
  expiresIn: number;
  user: UserSummary;
}

export interface MessageResponse {
  message: string;
}

export interface RegistrationResponse {
  customerNumber: string;
  accountNumber: string;
  message: string;
}

export interface BranchSummary {
  code: string;
  name: string;
  ifsc: string;
  city: string;
}

export interface Branch extends BranchSummary {
  id: number;
  addressLine: string;
  state: string;
  pincode: string;
  phone: string | null;
  active: boolean;
  createdAt: string;
}

export interface Account {
  id: number;
  accountNumber: string;
  accountType: AccountType;
  status: AccountStatus;
  statusReason: string | null;
  balance: number;
  availableBalance: number;
  minimumBalance: number;
  interestRate: number;
  currency: string;
  branchCode: string;
  branchName: string;
  ifsc: string;
  openedAt: string;
  closedAt: string | null;
}

export interface AccountLookup {
  accountId: number;
  accountNumber: string;
  accountType: AccountType;
  status: AccountStatus;
  customerId: number;
  customerNumber: string;
  holderName: string;
  kycStatus: KycStatus;
  balance: number;
  branchCode: string;
}

export interface Transaction {
  id: number;
  referenceNumber: string;
  accountId: number;
  accountNumber: string;
  type: TransactionType;
  direction: Direction;
  amount: number;
  balanceAfter: number;
  status: TransactionStatus;
  channel: Channel;
  description: string | null;
  counterpartyAccount: string | null;
  counterpartyName: string | null;
  counterpartyIfsc: string | null;
  failureReason: string | null;
  valueDate: string;
  createdAt: string;
}

export interface TransferResponse {
  referenceNumber: string;
  status: TransactionStatus;
  amount: number;
  fromAccountNumber: string;
  toAccountNumber: string;
  toName: string;
  toIfsc: string;
  balanceAfter: number;
  valueDate: string;
  completedAt: string;
}

export interface CashResponse {
  referenceNumber: string;
  accountNumber: string;
  holderName: string;
  type: TransactionType;
  amount: number;
  balanceAfter: number;
  completedAt: string;
}

export interface Beneficiary {
  id: number;
  name: string;
  nickname: string | null;
  maskedAccountNumber: string;
  ifsc: string;
  bankName: string;
  internal: boolean;
  active: boolean;
  activatedAt: string;
  createdAt: string;
}

export interface FixedDeposit {
  id: number;
  accountId: number;
  accountNumber: string;
  payoutAccountNumber: string;
  principal: number;
  interestRate: number;
  tenureMonths: number;
  startDate: string;
  maturityDate: string;
  maturityAmount: number;
  interestEarned: number;
  status: FixedDepositStatus;
}

export interface FixedDepositQuote {
  principal: number;
  interestRate: number;
  tenureMonths: number;
  maturityDate: string;
  maturityAmount: number;
  interestEarned: number;
}

export interface Loan {
  id: number;
  loanNumber: string;
  loanType: LoanType;
  principal: number;
  interestRate: number;
  tenureMonths: number;
  emiAmount: number;
  outstandingPrincipal: number;
  purpose: string;
  status: LoanStatus;
  accountId: number;
  accountNumber: string;
  customerId: number;
  customerNumber: string;
  customerName: string;
  appliedAt: string;
  reviewedAt: string | null;
  reviewRemarks: string | null;
  disbursedAt: string | null;
  closedAt: string | null;
}

export interface Installment {
  installmentNumber: number;
  dueDate: string;
  emiAmount: number;
  principalComponent: number;
  interestComponent: number;
  closingPrincipal: number;
  status: InstallmentStatus;
  paidAt: string | null;
  transactionRef: string | null;
  lastFailureReason: string | null;
}

export interface LoanDetail {
  loan: Loan;
  schedule: Installment[];
  paidInstallments: number;
}

export interface EmiCalculation {
  principal: number;
  annualRate: number;
  tenureMonths: number;
  emi: number;
  totalInterest: number;
  totalPayment: number;
  schedule: { month: number; emi: number; principal: number; interest: number; balance: number }[];
}

export interface CustomerProfile {
  id: number;
  customerNumber: string;
  fullName: string;
  email: string;
  phone: string;
  dateOfBirth: string;
  maskedPan: string;
  maskedAadhaar: string;
  addressLine: string;
  city: string;
  state: string;
  pincode: string;
  homeBranch: BranchSummary;
  kycStatus: KycStatus;
  kycRemarks: string | null;
  kycReviewedAt: string | null;
  createdAt: string;
}

export interface CustomerSummary {
  id: number;
  customerNumber: string;
  fullName: string;
  email: string;
  phone: string;
  kycStatus: KycStatus;
  homeBranchCode: string;
  createdAt: string;
}

export interface CustomerDetail {
  profile: CustomerProfile;
  accounts: Account[];
}

export interface CustomerDashboard {
  fullName: string;
  customerNumber: string;
  kycStatus: KycStatus;
  totalBalance: number;
  fixedDepositTotal: number;
  loanOutstanding: number;
  activeLoans: number;
  unreadNotifications: number;
  accounts: Account[];
  recentTransactions: Transaction[];
  monthlyCashflow: { month: string; income: number; spending: number }[];
  spendingByCategory: { type: TransactionType; count: number; amount: number }[];
}

export interface StaffDashboard {
  pendingKyc: number;
  pendingLoans: number;
  overdueInstallments: number;
  frozenAccounts: number;
  todayDepositCount: number;
  todayDepositAmount: number;
  todayWithdrawalCount: number;
  todayWithdrawalAmount: number;
}

export interface NotificationItem {
  id: number;
  type: NotificationType;
  title: string;
  message: string;
  read: boolean;
  createdAt: string;
}

export interface Employee {
  id: number;
  userId: number;
  employeeCode: string;
  fullName: string;
  email: string;
  phone: string;
  designation: string;
  branchCode: string;
  branchName: string;
  enabled: boolean;
  locked: boolean;
  lastLoginAt: string | null;
  createdAt: string;
}

export interface AccountPolicy {
  accountType: AccountType;
  interestRate: number;
  minimumBalance: number;
  dailyTransferLimit: number;
  updatedAt: string;
}

export interface LoanProduct {
  loanType: LoanType;
  interestRate: number;
  minAmount: number;
  maxAmount: number;
  minTenureMonths: number;
  maxTenureMonths: number;
  updatedAt: string;
}

export interface RateCard {
  accounts: AccountPolicy[];
  loans: LoanProduct[];
}

export interface AuditLog {
  id: number;
  actorId: number | null;
  actorEmail: string | null;
  actorRole: Role | null;
  action: string;
  entityType: string | null;
  entityId: string | null;
  outcome: AuditOutcome;
  details: string | null;
  ipAddress: string | null;
  createdAt: string;
}

export interface AnalyticsOverview {
  totalDeposits: number;
  depositsByType: { accountType: AccountType; accounts: number; balance: number }[];
  activeAccounts: number;
  frozenAccounts: number;
  totalCustomers: number;
  pendingKyc: number;
  todayTransactionCount: number;
  todayTransactionVolume: number;
  loanPortfolio: {
    activeLoans: number;
    pendingApplications: number;
    closedLoans: number;
    overdueInstallments: number;
    totalDisbursed: number;
    totalOutstanding: number;
    byType: { loanType: LoanType; activeLoans: number; outstanding: number; pending: number }[];
  };
}

export interface DailyVolumePoint {
  date: string;
  credits: number;
  debits: number;
  transactions: number;
}

export interface JobRun {
  job: string;
  processed: number;
  succeeded: number;
  skipped: number;
  failed: number;
  totalAmount: number;
}
