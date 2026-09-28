import { useContext } from 'react';
import type { Role } from '@/types/api';
import { AuthContext } from './authContext';

export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) throw new Error('useAuth must be used inside <AuthProvider>');
  return context;
}

export const HOME_BY_ROLE: Record<Role, string> = {
  CUSTOMER: '/app',
  EMPLOYEE: '/staff',
  ADMIN: '/admin',
};
