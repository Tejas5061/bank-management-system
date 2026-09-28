import { formatDate, formatINR } from '@/lib/format';
import type { Installment } from '@/types/api';
import { Code } from './ui/Money';
import { Stamp } from './ui/Status';

/** EMI schedule; paid instalments carry a violet PAID stamp, like a stamped repayment card. */
export function LoanSchedule({ schedule }: { schedule: Installment[] }) {
  return (
    <div className="overflow-x-auto">
      <table className="w-full min-w-[640px] text-sm">
        <caption className="sr-only">EMI schedule</caption>
        <thead>
          <tr className="border-b-[3px] border-double border-rule-strong text-left font-mono text-[0.68rem] uppercase tracking-[0.14em] text-ink-faint">
            <th scope="col" className="py-2.5 pl-5 pr-3 font-medium">No.</th>
            <th scope="col" className="px-3 py-2.5 font-medium">Due</th>
            <th scope="col" className="px-3 py-2.5 text-right font-medium">EMI</th>
            <th scope="col" className="px-3 py-2.5 text-right font-medium">Principal</th>
            <th scope="col" className="px-3 py-2.5 text-right font-medium">Interest</th>
            <th scope="col" className="px-3 py-2.5 text-right font-medium">Balance after</th>
            <th scope="col" className="py-2.5 pl-3 pr-5 font-medium">Status</th>
          </tr>
        </thead>
        <tbody>
          {schedule.map((row) => (
            <tr key={row.installmentNumber} className="ledger-row">
              <td className="figures py-2.5 pl-5 pr-3 text-ink-soft">{row.installmentNumber}</td>
              <td className="px-3 py-2.5">{formatDate(row.dueDate)}</td>
              <td className="figures px-3 py-2.5 text-right font-medium">{formatINR(row.emiAmount)}</td>
              <td className="figures px-3 py-2.5 text-right text-ink-soft">{formatINR(row.principalComponent)}</td>
              <td className="figures px-3 py-2.5 text-right text-ink-soft">{formatINR(row.interestComponent)}</td>
              <td className="figures px-3 py-2.5 text-right">{formatINR(row.closingPrincipal)}</td>
              <td className="py-2.5 pl-3 pr-5">
                {row.status === 'PENDING' ? <span className="text-ink-faint">Upcoming</span> : <Stamp status={row.status} />}
                {row.transactionRef && <Code className="ml-2 text-xs text-ink-faint">{row.transactionRef}</Code>}
                {row.status === 'OVERDUE' && row.lastFailureReason && <p className="mt-0.5 text-xs text-debit">{row.lastFailureReason}</p>}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
