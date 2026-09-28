import { useQueryClient } from '@tanstack/react-query';
import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react';
import { api, refreshSession } from '@/lib/api';
import { clearSession, getUser, msUntilExpiry, onSessionChange, setSession } from '@/lib/session';
import type { AuthResponse, UserSummary } from '@/types/api';
import { AuthContext, type AuthContextValue } from './authContext';

export function AuthProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient();
  const [user, setUser] = useState<UserSummary | null>(getUser());
  const [restoring, setRestoring] = useState(true);

  useEffect(() => onSessionChange(setUser), []);

  // On first load, resume the session from the HttpOnly refresh cookie (if there is one).
  useEffect(() => {
    void refreshSession().finally(() => setRestoring(false));
  }, []);

  // Renew the access token a minute before it expires, so active users never hit a 401.
  useEffect(() => {
    if (!user) return undefined;
    const timer = window.setTimeout(() => void refreshSession(), Math.max(5_000, msUntilExpiry() - 60_000));
    return () => window.clearTimeout(timer);
  }, [user]);

  const signIn = useCallback(async (email: string, password: string) => {
    const { data } = await api.post<AuthResponse>('/auth/login', { email, password });
    queryClient.clear();
    setSession(data);
    return data.user;
  }, [queryClient]);

  const signOut = useCallback(async () => {
    try {
      await api.post('/auth/logout');
    } finally {
      clearSession();
      queryClient.clear();
    }
  }, [queryClient]);

  const value = useMemo<AuthContextValue>(() => ({
    status: restoring ? 'restoring' : user ? 'signed-in' : 'signed-out',
    user,
    signIn,
    signOut,
  }), [restoring, user, signIn, signOut]);

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
