import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '@/lib/api';
import type {
  Account,
  AccountType,
  Beneficiary,
  CustomerDashboard,
  CustomerProfile,
  FixedDeposit,
  Loan,
  LoanDetail,
  LoanType,
  Page,
  TransferResponse,
} from '@/types/api';

const withKey = (idempotencyKey: string) => ({ headers: { 'Idempotency-Key': idempotencyKey } });

export const useDashboard = () =>
  useQuery({ queryKey: ['me', 'dashboard'], queryFn: async () => (await api.get<CustomerDashboard>('/me/dashboard')).data });

export const useMyAccounts = () =>
  useQuery({ queryKey: ['me', 'accounts'], queryFn: async () => (await api.get<Account[]>('/me/accounts')).data });

export const useProfile = () =>
  useQuery({ queryKey: ['me', 'profile'], queryFn: async () => (await api.get<CustomerProfile>('/me/profile')).data });

export const useBeneficiaries = () =>
  useQuery({
    queryKey: ['me', 'beneficiaries'],
    queryFn: async () => (await api.get<Page<Beneficiary>>('/me/beneficiaries', { params: { size: 100 } })).data.content,
  });

export const useFixedDeposits = () =>
  useQuery({
    queryKey: ['me', 'fixed-deposits'],
    queryFn: async () => (await api.get<Page<FixedDeposit>>('/me/fixed-deposits', { params: { size: 50 } })).data.content,
  });

export const useMyLoans = () =>
  useQuery({
    queryKey: ['me', 'loans'],
    queryFn: async () => (await api.get<Page<Loan>>('/me/loans', { params: { size: 50 } })).data.content,
  });

export const useMyLoan = (id: number) =>
  useQuery({ queryKey: ['me', 'loans', id], queryFn: async () => (await api.get<LoanDetail>(`/me/loans/${id}`)).data });

/** Anything that moves money changes balances, history and the dashboard: refresh them all. */
function useMoneyMutation<TBody, TResult>(fn: (body: TBody, idempotencyKey: string) => Promise<TResult>) {
  const client = useQueryClient();
  return useMutation({
    mutationFn: ({ body, idempotencyKey }: { body: TBody; idempotencyKey: string }) => fn(body, idempotencyKey),
    onSettled: () => {
      void client.invalidateQueries({ queryKey: ['me'] });
      void client.invalidateQueries({ queryKey: ['history'] });
      void client.invalidateQueries({ queryKey: ['account'] });
      void client.invalidateQueries({ queryKey: ['notifications'] });
    },
  });
}

export const useInternalTransfer = () =>
  useMoneyMutation(async (body: { fromAccountId: number; toAccountId: number; amount: number; remarks?: string }, key: string) =>
    (await api.post<TransferResponse>('/transfers/internal', body, withKey(key))).data);

export const useBeneficiaryTransfer = () =>
  useMoneyMutation(async (body: { fromAccountId: number; beneficiaryId: number; amount: number; remarks?: string }, key: string) =>
    (await api.post<TransferResponse>('/transfers/beneficiary', body, withKey(key))).data);

export const useOpenFixedDeposit = () =>
  useMoneyMutation(async (body: { sourceAccountId: number; principal: number; tenureMonths: number }, key: string) =>
    (await api.post<FixedDeposit>('/me/fixed-deposits', body, withKey(key))).data);

export function useOpenAccount() {
  const client = useQueryClient();
  return useMutation({
    mutationFn: async (accountType: AccountType) => (await api.post<Account>('/me/accounts', { accountType })).data,
    onSuccess: () => client.invalidateQueries({ queryKey: ['me'] }),
  });
}

export function useAddBeneficiary() {
  const client = useQueryClient();
  return useMutation({
    mutationFn: async (body: { name: string; nickname?: string; accountNumber: string; ifsc: string; bankName?: string }) =>
      (await api.post<Beneficiary>('/me/beneficiaries', body)).data,
    onSuccess: () => client.invalidateQueries({ queryKey: ['me', 'beneficiaries'] }),
  });
}

export function useDeleteBeneficiary() {
  const client = useQueryClient();
  return useMutation({
    mutationFn: (id: number) => api.delete(`/me/beneficiaries/${id}`),
    onSuccess: () => client.invalidateQueries({ queryKey: ['me', 'beneficiaries'] }),
  });
}

export function useApplyLoan() {
  const client = useQueryClient();
  return useMutation({
    mutationFn: async (body: { loanType: LoanType; amount: number; tenureMonths: number; accountId: number; purpose: string }) =>
      (await api.post<Loan>('/me/loans', body)).data,
    onSuccess: () => client.invalidateQueries({ queryKey: ['me'] }),
  });
}

export function useUpdateProfile() {
  const client = useQueryClient();
  return useMutation({
    mutationFn: async (body: { phone: string; addressLine: string; city: string; state: string; pincode: string }) =>
      (await api.put<CustomerProfile>('/me/profile', body)).data,
    onSuccess: (profile) => client.setQueryData(['me', 'profile'], profile),
  });
}
