import type { AuthResponse, UserSummary } from '@/types/api';

/**
 * The access token lives only in memory - never localStorage - so an XSS payload cannot lift a
 * long-lived credential from storage. On reload the session is restored by calling /auth/refresh,
 * which uses the HttpOnly refresh cookie the browser holds for us.
 */
type Listener = (user: UserSummary | null) => void;

let accessToken: string | null = null;
let currentUser: UserSummary | null = null;
let expiresAt = 0;
const listeners = new Set<Listener>();

export function setSession(auth: AuthResponse) {
  accessToken = auth.accessToken;
  currentUser = auth.user;
  expiresAt = Date.now() + auth.expiresIn * 1000;
  listeners.forEach((listener) => listener(currentUser));
}

export function clearSession() {
  accessToken = null;
  currentUser = null;
  expiresAt = 0;
  listeners.forEach((listener) => listener(null));
}

export const getAccessToken = () => accessToken;
export const getUser = () => currentUser;
export const msUntilExpiry = () => Math.max(0, expiresAt - Date.now());

export function onSessionChange(listener: Listener) {
  listeners.add(listener);
  return () => {
    listeners.delete(listener);
  };
}
