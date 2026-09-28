import type { HTMLAttributes, ReactNode } from 'react';
import { cn } from '@/lib/util';

export function Card({ className, ...rest }: HTMLAttributes<HTMLDivElement>) {
  return <div className={cn('rounded-[var(--radius-card)] border border-rule bg-surface shadow-[var(--shadow-card)]', className)} {...rest} />;
}

export function CardHeader({ title, description, action, className }: {
  title: ReactNode;
  description?: ReactNode;
  action?: ReactNode;
  className?: string;
}) {
  return (
    <div className={cn('flex flex-wrap items-start justify-between gap-3 border-b border-rule px-5 py-4', className)}>
      <div className="min-w-0">
        <h2 className="text-base font-semibold text-ink">{title}</h2>
        {description && <p className="mt-0.5 text-sm text-ink-soft">{description}</p>}
      </div>
      {action}
    </div>
  );
}

export function PageHeader({ title, description, action }: { title: string; description?: ReactNode; action?: ReactNode }) {
  return (
    <header className="mb-6 flex flex-wrap items-end justify-between gap-4">
      <div>
        <h1 className="text-[1.75rem] font-semibold leading-tight text-ink">{title}</h1>
        {description && <p className="mt-1 max-w-2xl text-ink-soft">{description}</p>}
      </div>
      {action}
    </header>
  );
}

export function EmptyState({ title, description, action }: { title: string; description?: string; action?: ReactNode }) {
  return (
    <div className="flex flex-col items-center gap-2 px-6 py-14 text-center">
      <p className="font-medium text-ink">{title}</p>
      {description && <p className="max-w-sm text-sm text-ink-soft">{description}</p>}
      {action && <div className="mt-3">{action}</div>}
    </div>
  );
}

/** A labelled figure. Used for KPIs; the label is small caps so the number carries the weight. */
export function Figure({ label, value, note, tone = 'ink' }: {
  label: string;
  value: ReactNode;
  note?: ReactNode;
  tone?: 'ink' | 'credit' | 'debit' | 'caution';
}) {
  const toneClass = { ink: 'text-ink', credit: 'text-credit', debit: 'text-debit', caution: 'text-caution' }[tone];
  return (
    <div className="min-w-0">
      <p className="text-xs font-medium uppercase tracking-[0.08em] text-ink-faint">{label}</p>
      <p className={cn('figures mt-1 truncate font-display text-2xl font-semibold', toneClass)}>{value}</p>
      {note && <p className="mt-0.5 text-sm text-ink-soft">{note}</p>}
    </div>
  );
}
