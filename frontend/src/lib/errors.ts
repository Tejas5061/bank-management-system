import axios from 'axios';
import type { FieldValues, Path, UseFormSetError } from 'react-hook-form';
import type { ApiError } from '@/types/api';

export function apiError(error: unknown): ApiError | null {
  if (axios.isAxiosError(error) && error.response?.data && typeof error.response.data === 'object') {
    const data = error.response.data as Partial<ApiError>;
    if (data.errorCode) return data as ApiError;
  }
  return null;
}

/** A sentence the user can act on; the server's messages are already written for people. */
export function errorMessage(error: unknown, fallback = 'Something went wrong. Please try again.'): string {
  const parsed = apiError(error);
  if (parsed) return parsed.message;
  if (axios.isAxiosError(error) && !error.response) return 'Cannot reach the bank right now. Check your connection and try again.';
  return fallback;
}

export function errorCode(error: unknown): string | undefined {
  return apiError(error)?.errorCode;
}

/** Puts server-side field errors next to the matching inputs; returns true if any were placed. */
export function applyFieldErrors<T extends FieldValues>(error: unknown, setError: UseFormSetError<T>): boolean {
  const fields = apiError(error)?.fieldErrors ?? [];
  fields.forEach((f) => setError(f.field as Path<T>, { type: 'server', message: f.message }));
  return fields.length > 0;
}
