import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '@/lib/api';
import type {
  Account,
  BranchSummary,
  EmiCalculation,
  FixedDepositQuote,
  MessageResponse,
  NotificationItem,
  Page,
  RateCard,
  RegistrationResponse,
  Transaction,
  TransactionStatus,
  TransactionType,
} from '@/types/api';

export const keys = {
  branches: ['branches'] as const,
  rates: ['rates'] as const,
  account: (id: number) => ['account', id] as const,
  history: (id: number, filters: object) => ['history', id, filters] as const,
  notifications: (unreadOnly: boolean, page: number) => ['notifications', unreadOnly, page] as const,
  unread: ['notifications', 'unread'] as const,
};

export const usePublicBranches = () =>
  useQuery({ queryKey: keys.branches, queryFn: async () => (await api.get<BranchSummary[]>('/public/branches')).data, staleTime: 60 * 60_000 });

export const useRates = () =>
  useQuery({ queryKey: keys.rates, queryFn: async () => (await api.get<RateCard>('/public/rates')).data, staleTime: 5 * 60_000 });

export const useEmiCalculation = (principal: number, annualRate: number, tenureMonths: number, enabled: boolean) =>
  useQuery({
    queryKey: ['emi', principal, annualRate, tenureMonths],
    queryFn: async () => (await api.post<EmiCalculation>('/public/emi-calculator', { principal, annualRate, tenureMonths })).data,
    enabled,
    placeholderData: keepPreviousData,
    staleTime: Infinity,
  });

export const useFdQuote = (principal: number, tenureMonths: number, enabled: boolean) =>
  useQuery({
    queryKey: ['fd-quote', principal, tenureMonths],
    queryFn: async () => (await api.get<FixedDepositQuote>('/public/fd-quote', { params: { principal, tenureMonths } })).data,
    enabled,
    placeholderData: keepPreviousData,
  });

export const useAccount = (id: number) =>
  useQuery({ queryKey: keys.account(id), queryFn: async () => (await api.get<Account>(`/accounts/${id}`)).data });

export interface HistoryFilters {
  from?: string;
  to?: string;
  type?: TransactionType | '';
  status?: TransactionStatus | '';
  page: number;
}

export const useHistory = (accountId: number, filters: HistoryFilters) =>
  useQuery({
    queryKey: keys.history(accountId, filters),
    queryFn: async () =>
      (await api.get<Page<Transaction>>(`/accounts/${accountId}/transactions`, {
        params: {
          from: filters.from || undefined,
          to: filters.to || undefined,
          type: filters.type || undefined,
          status: filters.status || undefined,
          page: filters.page,
          size: 15,
        },
      })).data,
    placeholderData: keepPreviousData,
  });

export const useRegister = () =>
  useMutation({
    mutationFn: async (body: Record<string, unknown>) => (await api.post<RegistrationResponse>('/auth/register', body)).data,
  });

export const useForgotPassword = () =>
  useMutation({ mutationFn: async (email: string) => (await api.post<MessageResponse>('/auth/password/forgot', { email })).data });

export const useResetPassword = () =>
  useMutation({
    mutationFn: async (body: { email: string; otp: string; newPassword: string }) =>
      (await api.post<MessageResponse>('/auth/password/reset', body)).data,
  });

export const useChangePassword = () =>
  useMutation({
    mutationFn: async (body: { currentPassword: string; newPassword: string }) =>
      (await api.post<MessageResponse>('/auth/password/change', body)).data,
  });

export const useUnreadCount = () =>
  useQuery({
    queryKey: keys.unread,
    queryFn: async () => (await api.get<{ count: number }>('/notifications/unread-count')).data.count,
    refetchInterval: 30_000,
  });

export const useNotifications = (unreadOnly: boolean, page: number) =>
  useQuery({
    queryKey: keys.notifications(unreadOnly, page),
    queryFn: async () => (await api.get<Page<NotificationItem>>('/notifications', { params: { unreadOnly, page, size: 20 } })).data,
    placeholderData: keepPreviousData,
  });

export function useMarkNotifications() {
  const client = useQueryClient();
  const invalidate = () => client.invalidateQueries({ queryKey: ['notifications'] });
  return {
    one: useMutation({ mutationFn: (id: number) => api.patch(`/notifications/${id}/read`), onSuccess: invalidate }),
    all: useMutation({ mutationFn: () => api.post('/notifications/read-all'), onSuccess: invalidate }),
  };
}
