import { createContext } from 'react';
import type { UserSummary } from '@/types/api';

export type AuthStatus = 'restoring' | 'signed-in' | 'signed-out';

export interface AuthContextValue {
  status: AuthStatus;
  user: UserSummary | null;
  signIn: (email: string, password: string) => Promise<UserSummary>;
  signOut: () => Promise<void>;
}

export const AuthContext = createContext<AuthContextValue | null>(null);
