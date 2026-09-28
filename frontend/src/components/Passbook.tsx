import { formatAmount, formatDate, formatDayShort, TRANSACTION_LABEL } from '@/lib/format';
import { cn } from '@/lib/util';
import type { Transaction } from '@/types/api';
import { Code } from './ui/Money';
import { Stamp } from './ui/Status';

/**
 * Transactions set like a page of an Indian bank passbook: Date / Particulars / Withdrawals /
 * Deposits / Balance, under a double rule, with ruled lines between entries. Amounts are plain
 * figures in their column (the column says what they are), exactly as a passbook prints them.
 */
export function Passbook({ rows, compact = false, showAccount = false }: {
  rows: Transaction[];
  compact?: boolean;
  showAccount?: boolean;
}) {
  return (
    <div className="overflow-x-auto">
      <table className="w-full min-w-[300px] border-collapse text-[13px] sm:text-sm">
        <caption className="sr-only">Transactions</caption>
        <thead>
          <tr className="border-b-[3px] border-double border-rule-strong text-left font-mono text-[0.62rem] uppercase tracking-[0.04em] text-ink-faint sm:text-[0.68rem] sm:tracking-[0.14em]">
            <th scope="col" className="py-2.5 pl-4 pr-2 font-medium sm:pl-5 sm:pr-3">Date</th>
            <th scope="col" className="px-2 py-2.5 font-medium sm:px-3">Particulars</th>
            <th scope="col" className="hidden px-3 py-2.5 text-right font-medium sm:table-cell">Withdrawals</th>
            <th scope="col" className="hidden px-3 py-2.5 text-right font-medium sm:table-cell">Deposits</th>
            <th scope="col" className="px-2 py-2.5 text-right font-medium sm:hidden">Amount</th>
            <th scope="col" className="py-2.5 pl-2 pr-4 text-right font-medium sm:pl-3 sm:pr-5">Balance</th>
          </tr>
        </thead>
        <tbody>
          {rows.map((t) => {
            const failed = t.status === 'FAILED';
            const debit = t.direction === 'DEBIT';
            const figure = <span className={cn('figures', failed && 'text-ink-faint line-through')}>{formatAmount(t.amount)}</span>;
            return (
              <tr key={t.id} className={cn('ledger-row align-top', failed && 'bg-debit-soft/40')}>
                <td className="whitespace-nowrap py-3 pl-4 pr-2 text-ink-soft sm:pl-5 sm:pr-3">
                  <span className="hidden sm:inline">{formatDate(t.valueDate)}</span>
                  <span className="sm:hidden">{formatDayShort(t.valueDate)}</span>
                </td>
                <td className="max-w-0 px-2 py-3 sm:w-[45%] sm:px-3">
                  <div className="flex flex-wrap items-center gap-x-2 gap-y-1">
                    <span className="truncate font-medium text-ink">{t.description ?? TRANSACTION_LABEL[t.type]}</span>
                    {failed && <Stamp status="FAILED" label="Declined" className="text-[0.6rem]" />}
                  </div>
                  {!compact && (
                    <div className="mt-0.5 hidden flex-wrap gap-x-2 text-xs text-ink-faint sm:flex">
                      <span>{TRANSACTION_LABEL[t.type]}</span>
                      {t.counterpartyName && <span>· {t.counterpartyName}</span>}
                      {showAccount && <span>· A/c <Code>{t.accountNumber.slice(-4)}</Code></span>}
                      <Code className="text-ink-faint">{t.referenceNumber}</Code>
                    </div>
                  )}
                  {failed && t.failureReason && <p className="mt-1 hidden text-xs text-debit sm:block">{t.failureReason}</p>}
                </td>
                <td className="hidden px-3 py-3 text-right text-debit sm:table-cell">{debit && figure}</td>
                <td className="hidden px-3 py-3 text-right text-credit sm:table-cell">{!debit && figure}</td>
                <td className={cn('whitespace-nowrap px-2 py-3 text-right sm:hidden', debit ? 'text-debit' : 'text-credit')}>
                  {debit ? '−' : '+'}
                  {figure}
                </td>
                <td className="figures whitespace-nowrap py-3 pl-2 pr-4 text-right font-medium text-ink sm:pl-3 sm:pr-5">
                  {failed ? <span className="text-ink-faint">—</span> : formatAmount(t.balanceAfter)}
                </td>
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}
