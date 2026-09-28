import type { TransactionType } from '@/types/api';

// Intl handles Indian digit grouping (12,34,567.00) natively for en-IN.
const inr = new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', minimumFractionDigits: 2 });
const inrCompact = new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', notation: 'compact', maximumSignificantDigits: 3 });
const plain = new Intl.NumberFormat('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
const day = new Intl.DateTimeFormat('en-IN', { day: '2-digit', month: 'short', year: 'numeric' });
const dayShort = new Intl.DateTimeFormat('en-IN', { day: '2-digit', month: 'short' });
const stamp = new Intl.DateTimeFormat('en-IN', { day: '2-digit', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit' });
const month = new Intl.DateTimeFormat('en-IN', { month: 'short' });

export const formatINR = (value: number) => inr.format(value);
export const formatINRCompact = (value: number) => inrCompact.format(value);
export const formatAmount = (value: number) => plain.format(value);

/** Accepts yyyy-MM-dd (business dates) or ISO instants. Plain dates are read as local dates. */
function toDate(value: string) {
  return /^\d{4}-\d{2}-\d{2}$/.test(value) ? new Date(`${value}T00:00:00`) : new Date(value);
}

export const formatDate = (value: string) => day.format(toDate(value));
export const formatDayShort = (value: string) => dayShort.format(toDate(value));
export const formatDateTime = (value: string) => stamp.format(toDate(value));
export const formatMonth = (yyyyMm: string) => month.format(new Date(`${yyyyMm}-01T00:00:00`));

/** Account numbers read in groups of four, like a printed passbook: 0001 1000 0015. */
export const groupDigits = (value: string) => value.replace(/(\d{4})(?=\d)/g, '$1 ');

/** plural(1, 'account') -> '1 account'; plural(3, 'account') -> '3 accounts'. */
export const plural = (count: number, noun: string) => `${count} ${noun}${count === 1 ? '' : 's'}`;

const ACRONYMS = new Set(['kyc', 'fd', 'emi', 'otp', 'ifsc']);

/** LOAN_APPROVED -> "Loan approved"; KYC_STATUS_CHANGED -> "KYC status changed". */
export const titleCase = (value: string) =>
  value
    .toLowerCase()
    .split('_')
    .map((word, i) => (ACRONYMS.has(word) ? word.toUpperCase() : i === 0 ? word.charAt(0).toUpperCase() + word.slice(1) : word))
    .join(' ');

export const TRANSACTION_LABEL: Record<TransactionType, string> = {
  DEPOSIT: 'Deposit',
  WITHDRAWAL: 'Withdrawal',
  TRANSFER_IN: 'Money received',
  TRANSFER_OUT: 'Money sent',
  INTEREST_CREDIT: 'Interest',
  FD_BOOKING: 'Fixed deposit',
  FD_MATURITY: 'FD maturity',
  LOAN_DISBURSEMENT: 'Loan credit',
  EMI_DEBIT: 'Loan EMI',
};

export const ACCOUNT_TYPE_LABEL = {
  SAVINGS: 'Savings',
  CURRENT: 'Current',
  FIXED_DEPOSIT: 'Fixed deposit',
} as const;

export const LOAN_TYPE_LABEL = {
  PERSONAL: 'Personal loan',
  HOME: 'Home loan',
  EDUCATION: 'Education loan',
} as const;
