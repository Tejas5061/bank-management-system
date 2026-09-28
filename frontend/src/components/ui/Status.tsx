import type { CSSProperties, ReactNode } from 'react';
import { cn } from '@/lib/util';

type Ink = 'violet' | 'red' | 'amber' | 'grey';

const INK: Record<Ink, string> = {
  violet: 'var(--color-stamp)',
  red: 'var(--color-debit)',
  amber: 'var(--color-caution)',
  grey: 'var(--color-ink-faint)',
};

const STATUS_INK: Record<string, Ink> = {
  VERIFIED: 'violet',
  PAID: 'violet',
  MATURED: 'violet',
  ACTIVE: 'violet',
  SUCCESS: 'violet',
  CLOSED: 'grey',
  PENDING: 'amber',
  OVERDUE: 'red',
  REJECTED: 'red',
  FROZEN: 'red',
  FAILED: 'red',
};

/**
 * The rubber stamp: official statuses (KYC verified, EMI paid, FD matured...) are shown the way a
 * branch would mark a passbook. `land` plays the stamp-down animation once, for the moment a
 * status actually changes.
 */
export function Stamp({ status, label, land = false, className }: {
  status: string;
  label?: ReactNode;
  land?: boolean;
  className?: string;
}) {
  const ink = STATUS_INK[status] ?? 'grey';
  return (
    <span
      className={cn('stamp', land && 'stamp-land', className)}
      style={{ '--stamp-color': INK[ink] } as CSSProperties}
    >
      {label ?? status.replace(/_/g, ' ')}
    </span>
  );
}

/** Quiet pill for non-official labels (transaction type, channel, role). */
export function Tag({ children, tone = 'neutral' }: { children: ReactNode; tone?: 'neutral' | 'brand' | 'credit' | 'debit' | 'caution' }) {
  const tones = {
    neutral: 'bg-paper text-ink-soft',
    brand: 'bg-kosh-50 text-kosh-700',
    credit: 'bg-credit-soft text-credit',
    debit: 'bg-debit-soft text-debit',
    caution: 'bg-caution-soft text-caution',
  };
  return <span className={cn('inline-flex items-center rounded-md px-2 py-0.5 text-xs font-medium', tones[tone])}>{children}</span>;
}
