import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '@/lib/api';
import type {
  Account,
  AccountLookup,
  AccountStatus,
  AccountType,
  CashResponse,
  CustomerDetail,
  CustomerProfile,
  CustomerSummary,
  KycStatus,
  Loan,
  LoanDetail,
  LoanStatus,
  Page,
  RegistrationResponse,
  StaffDashboard,
} from '@/types/api';

export const useStaffDashboard = () =>
  useQuery({ queryKey: ['staff', 'dashboard'], queryFn: async () => (await api.get<StaffDashboard>('/staff/dashboard')).data });

export const useCustomers = (query: string, kycStatus: KycStatus | '', page: number, sort = 'createdAt,desc') =>
  useQuery({
    queryKey: ['staff', 'customers', query, kycStatus, page, sort],
    queryFn: async () =>
      (await api.get<Page<CustomerSummary>>('/staff/customers', {
        params: { query: query || undefined, kycStatus: kycStatus || undefined, page, size: 15, sort },
      })).data,
    placeholderData: keepPreviousData,
  });

export const useCustomer = (id: number) =>
  useQuery({ queryKey: ['staff', 'customer', id], queryFn: async () => (await api.get<CustomerDetail>(`/staff/customers/${id}`)).data });

export const useAccountLookup = (accountNumber: string) =>
  useQuery({
    queryKey: ['staff', 'lookup', accountNumber],
    queryFn: async () => (await api.get<AccountLookup>('/staff/accounts/lookup', { params: { accountNumber } })).data,
    enabled: /^\d{9,18}$/.test(accountNumber),
    retry: false,
  });

export const useStaffLoans = (status: LoanStatus | '', page: number) =>
  useQuery({
    queryKey: ['staff', 'loans', status, page],
    queryFn: async () =>
      (await api.get<Page<Loan>>('/staff/loans', { params: { status: status || undefined, page, size: 15 } })).data,
    placeholderData: keepPreviousData,
  });

export const useStaffLoan = (id: number | null) =>
  useQuery({
    queryKey: ['staff', 'loan', id],
    queryFn: async () => (await api.get<LoanDetail>(`/staff/loans/${id}`)).data,
    enabled: id !== null,
  });

function useStaffMutation<TVars, TResult>(fn: (vars: TVars) => Promise<TResult>) {
  const client = useQueryClient();
  return useMutation({ mutationFn: fn, onSuccess: () => client.invalidateQueries({ queryKey: ['staff'] }) });
}

export const useOnboardCustomer = () =>
  useStaffMutation(async (body: Record<string, unknown>) => (await api.post<RegistrationResponse>('/staff/customers', body)).data);

export const useReviewKyc = () =>
  useStaffMutation(async ({ customerId, status, remarks }: { customerId: number; status: KycStatus; remarks?: string }) =>
    (await api.patch<CustomerProfile>(`/staff/customers/${customerId}/kyc`, { status, remarks })).data);

export const useOpenAccountFor = () =>
  useStaffMutation(async ({ customerId, accountType }: { customerId: number; accountType: AccountType }) =>
    (await api.post<Account>(`/staff/customers/${customerId}/accounts`, { accountType })).data);

export const useChangeAccountStatus = () =>
  useStaffMutation(async ({ accountId, status, reason }: { accountId: number; status: AccountStatus; reason: string }) =>
    (await api.patch<Account>(`/staff/accounts/${accountId}/status`, { status, reason })).data);

export const useCash = () =>
  useStaffMutation(async ({ kind, idempotencyKey, ...body }: {
    kind: 'deposits' | 'withdrawals';
    idempotencyKey: string;
    accountNumber: string;
    amount: number;
    remarks?: string;
  }) => (await api.post<CashResponse>(`/staff/cash/${kind}`, body, { headers: { 'Idempotency-Key': idempotencyKey } })).data);

export const useLoanDecision = () =>
  useStaffMutation(async ({ loanId, decision, remarks }: { loanId: number; decision: 'approve' | 'reject'; remarks: string }) =>
    (await api.post<LoanDetail>(`/staff/loans/${loanId}/${decision}`, { remarks })).data);
