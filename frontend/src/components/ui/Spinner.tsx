import type { ReactNode } from 'react';
import { cn } from '@/lib/util';

export function Spinner({ className }: { className?: string }) {
  return (
    <svg className={cn('size-5 animate-spin motion-reduce:animate-none', className)} viewBox="0 0 24 24" fill="none" aria-hidden="true">
      <circle cx="12" cy="12" r="9" stroke="currentColor" strokeOpacity="0.2" strokeWidth="3" />
      <path d="M21 12a9 9 0 0 0-9-9" stroke="currentColor" strokeWidth="3" strokeLinecap="round" />
    </svg>
  );
}

export function FullPageSpinner({ label, children }: { label: string; children?: ReactNode }) {
  return (
    <div className="grid min-h-dvh place-items-center px-5">
      <div className="flex flex-col items-center">
        <div className="flex items-center gap-3 text-ink-soft" role="status">
          <Spinner />
          <span>{label}</span>
        </div>
        {children}
      </div>
    </div>
  );
}

export function LoadingBlock({ label = 'Loading' }: { label?: string }) {
  return (
    <div className="flex items-center justify-center gap-3 py-16 text-ink-soft" role="status">
      <Spinner />
      <span className="text-sm">{label}</span>
    </div>
  );
}
