import { clsx, type ClassValue } from 'clsx';
import { api } from './api';

export const cn = (...inputs: ClassValue[]) => clsx(inputs);

/** One key per logical money movement; reused on retries of the same submission. */
export function newIdempotencyKey(): string {
  if (typeof crypto !== 'undefined' && 'randomUUID' in crypto) return crypto.randomUUID();
  return `k-${Date.now()}-${Math.random().toString(36).slice(2, 12)}`;
}

/** Downloads an authenticated file (statements) without exposing the token in a URL. */
export async function downloadFile(url: string, params: Record<string, string | undefined>, fallbackName: string) {
  const response = await api.get<Blob>(url, { params, responseType: 'blob' });
  const disposition = response.headers['content-disposition'] as string | undefined;
  const name = /filename="?([^";]+)"?/.exec(disposition ?? '')?.[1] ?? fallbackName;
  const href = URL.createObjectURL(response.data);
  const link = document.createElement('a');
  link.href = href;
  link.download = name;
  document.body.appendChild(link);
  link.click();
  link.remove();
  setTimeout(() => URL.revokeObjectURL(href), 1000);
}
