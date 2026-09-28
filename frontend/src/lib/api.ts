import axios, { AxiosError, type InternalAxiosRequestConfig } from 'axios';
import type { ApiError, AuthResponse } from '@/types/api';
import { clearSession, getAccessToken, setSession } from './session';

const baseURL = import.meta.env.VITE_API_URL ?? '/api/v1';

export const api = axios.create({ baseURL, withCredentials: true });

api.interceptors.request.use((config) => {
  const token = getAccessToken();
  if (token) config.headers.Authorization = `Bearer ${token}`;
  return config;
});

let inflightRefresh: Promise<string | null> | null = null;

/**
 * Single-flight refresh: ten requests failing with an expired token trigger ONE refresh call, and
 * all ten retry with the new token. Refresh tokens are single-use on the server, so parallel
 * refreshes would otherwise race and look like token theft.
 */
export function refreshSession(): Promise<string | null> {
  if (!inflightRefresh) {
    const hadSession = getAccessToken() !== null;
    inflightRefresh = (async () => {
      for (let attempt = 0; attempt < 2; attempt++) {
        try {
          const { data } = await axios.post<AuthResponse>(`${baseURL}/auth/refresh`, null, { withCredentials: true });
          setSession(data);
          return data.accessToken;
        } catch (error) {
          // Another tab may have rotated the cookie a moment ago; the browser now holds the new
          // one, so a single retry after a short pause usually succeeds. (Only worth it when this
          // tab already had a session; on first load a 401 just means "not signed in".)
          if (attempt === 0 && hadSession && axios.isAxiosError(error) && error.response?.status === 401) {
            await new Promise((resolve) => setTimeout(resolve, 400));
            continue;
          }
          break;
        }
      }
      clearSession();
      return null;
    })().finally(() => {
      inflightRefresh = null;
    });
  }
  return inflightRefresh;
}

type RetriableConfig = InternalAxiosRequestConfig & { _retried?: boolean };

api.interceptors.response.use(
  (response) => response,
  async (error: AxiosError<ApiError>) => {
    const original = error.config as RetriableConfig | undefined;
    const code = error.response?.data?.errorCode;
    const refreshable = code === 'TOKEN_EXPIRED' || code === 'INVALID_TOKEN';
    if (error.response?.status === 401 && refreshable && original && !original._retried) {
      original._retried = true;
      const token = await refreshSession();
      if (token) {
        original.headers.Authorization = `Bearer ${token}`;
        return api(original);
      }
    }
    return Promise.reject(error);
  },
);
