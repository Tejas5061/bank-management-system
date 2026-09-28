import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { Toaster } from 'sonner';
import { AuthProvider } from '@/auth/AuthProvider';
import { apiError } from '@/lib/errors';
import { App } from './App';
import './index.css';

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 30_000,
      refetchOnWindowFocus: false,
      // 4xx answers will not change on retry; only retry network blips and 5xx.
      retry: (failureCount, error) => {
        const status = apiError(error)?.status;
        return failureCount < 2 && (status === undefined || status >= 500);
      },
    },
    mutations: { retry: false },
  },
});

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <QueryClientProvider client={queryClient}>
      <AuthProvider>
        <App />
        <Toaster position="top-right" richColors closeButton toastOptions={{ style: { fontFamily: 'var(--font-sans)' } }} />
      </AuthProvider>
    </QueryClientProvider>
  </StrictMode>,
);
