import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '@/lib/api';
import type {
  AccountPolicy,
  AccountType,
  AnalyticsOverview,
  AuditLog,
  AuditOutcome,
  Branch,
  DailyVolumePoint,
  Employee,
  JobRun,
  LoanProduct,
  LoanType,
  Page,
  RateCard,
} from '@/types/api';

export const useOverview = () =>
  useQuery({ queryKey: ['admin', 'overview'], queryFn: async () => (await api.get<AnalyticsOverview>('/admin/analytics/overview')).data });

export const useDailyVolume = (days: number) =>
  useQuery({
    queryKey: ['admin', 'volume', days],
    queryFn: async () => (await api.get<DailyVolumePoint[]>('/admin/analytics/daily-volume', { params: { days } })).data,
    placeholderData: keepPreviousData,
  });

export const useEmployees = (query: string, page: number) =>
  useQuery({
    queryKey: ['admin', 'employees', query, page],
    queryFn: async () => (await api.get<Page<Employee>>('/admin/employees', { params: { query: query || undefined, page, size: 15 } })).data,
    placeholderData: keepPreviousData,
  });

export const useBranches = () =>
  useQuery({
    queryKey: ['admin', 'branches'],
    queryFn: async () => (await api.get<Page<Branch>>('/admin/branches', { params: { size: 100 } })).data.content,
  });

export const useAdminRates = () =>
  useQuery({ queryKey: ['admin', 'rates'], queryFn: async () => (await api.get<RateCard>('/admin/rates')).data });

export interface AuditFilters {
  action: string;
  outcome: AuditOutcome | '';
  actor: string;
  page: number;
}

export const useAuditLogs = (filters: AuditFilters) =>
  useQuery({
    queryKey: ['admin', 'audit', filters],
    queryFn: async () =>
      (await api.get<Page<AuditLog>>('/admin/audit-logs', {
        params: {
          action: filters.action || undefined,
          outcome: filters.outcome || undefined,
          actor: filters.actor || undefined,
          page: filters.page,
          size: 25,
        },
      })).data,
    placeholderData: keepPreviousData,
  });

function useAdminMutation<TVars, TResult>(fn: (vars: TVars) => Promise<TResult>) {
  const client = useQueryClient();
  return useMutation({
    mutationFn: fn,
    onSuccess: () => {
      void client.invalidateQueries({ queryKey: ['admin'] });
      void client.invalidateQueries({ queryKey: ['rates'] });
    },
  });
}

export const useCreateEmployee = () =>
  useAdminMutation(async (body: { fullName: string; email: string; phone: string; branchCode: string; designation: string }) =>
    (await api.post<Employee>('/admin/employees', body)).data);

export const useUpdateEmployee = () =>
  useAdminMutation(async ({ id, ...body }: { id: number; phone: string; branchCode: string; designation: string; enabled: boolean }) =>
    (await api.put<Employee>(`/admin/employees/${id}`, body)).data);

export const useUnlockUser = () => useAdminMutation(async (userId: number) => (await api.post(`/admin/users/${userId}/unlock`)).data);

export const useSaveBranch = () =>
  useAdminMutation(async ({ id, ...body }: { id?: number } & Record<string, unknown>) =>
    (id ? await api.put<Branch>(`/admin/branches/${id}`, body) : await api.post<Branch>('/admin/branches', body)).data);

export const useUpdatePolicy = () =>
  useAdminMutation(async ({ type, ...body }: { type: AccountType; interestRate: number; minimumBalance: number; dailyTransferLimit: number }) =>
    (await api.put<AccountPolicy>(`/admin/rates/accounts/${type}`, body)).data);

export const useUpdateLoanProduct = () =>
  useAdminMutation(async ({ type, ...body }: {
    type: LoanType;
    interestRate: number;
    minAmount: number;
    maxAmount: number;
    minTenureMonths: number;
    maxTenureMonths: number;
  }) => (await api.put<LoanProduct>(`/admin/rates/loans/${type}`, body)).data);

export const useRunJob = () =>
  useAdminMutation(async (job: 'interest' | 'fd-maturity' | 'emi-debit') => (await api.post<JobRun>(`/admin/jobs/${job}`)).data);
