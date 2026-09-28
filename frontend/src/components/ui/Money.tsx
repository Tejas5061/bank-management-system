import { formatINR, groupDigits } from '@/lib/format';
import { cn } from '@/lib/util';
import type { Direction } from '@/types/api';

export function Amount({ value, direction, className }: { value: number; direction?: Direction; className?: string }) {
  const sign = direction === 'CREDIT' ? '+' : direction === 'DEBIT' ? '−' : '';
  return (
    <span
      className={cn(
        'figures whitespace-nowrap',
        direction === 'CREDIT' && 'text-credit',
        direction === 'DEBIT' && 'text-debit',
        className,
      )}
    >
      {sign}
      {formatINR(value)}
    </span>
  );
}

/** Machine identifiers (account numbers, IFSC, references) are set in mono, like printed codes. */
export function Code({ children, className, group = false }: { children: string; className?: string; group?: boolean }) {
  return <span className={cn('font-mono text-[0.92em] tracking-wide', className)}>{group ? groupDigits(children) : children}</span>;
}
