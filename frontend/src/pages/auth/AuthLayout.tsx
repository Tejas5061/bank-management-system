import { Landmark } from 'lucide-react';
import type { ReactNode } from 'react';
import { Link } from 'react-router-dom';

/**
 * Split screen. The left panel shows a passbook page on the brand colour: the product's visual
 * idea (a live passbook) stated before the user has even signed in.
 */
export function AuthLayout({ children, wide = false }: { children: ReactNode; wide?: boolean }) {
  return (
    <div className="grid min-h-dvh lg:grid-cols-[minmax(0,5fr)_minmax(0,6fr)]">
      <aside className="relative hidden overflow-hidden bg-kosh-800 px-12 py-10 text-paper lg:flex lg:flex-col lg:justify-between">
        <Link to="/" className="flex items-center gap-2.5">
          <span className="grid size-9 place-items-center rounded-lg bg-paper text-kosh-800">
            <Landmark className="size-5" aria-hidden="true" />
          </span>
          <span className="font-display text-xl font-semibold">Kosh Bank</span>
        </Link>

        <div>
          <h1 className="max-w-md font-display text-[2.6rem] font-semibold leading-[1.05] tracking-tight">
            Every rupee, entered the moment it moves.
          </h1>
          <p className="mt-4 max-w-sm text-kosh-100/85">
            Your passbook, without the queue at the printer. Balances, transfers, deposits and loans in one place.
          </p>
          <PassbookFragment />
        </div>

        <p className="text-xs text-kosh-200">A portfolio project. All customers, accounts and money shown here are fictitious.</p>
      </aside>

      <main className="flex items-center justify-center px-5 py-10 sm:px-10">
        <div className={wide ? 'w-full max-w-2xl' : 'w-full max-w-sm'}>
          <Link to="/" className="mb-8 flex items-center gap-2 lg:hidden">
            <span className="grid size-8 place-items-center rounded-lg bg-kosh-800 text-paper">
              <Landmark className="size-4" aria-hidden="true" />
            </span>
            <span className="font-display text-lg font-semibold text-ink">Kosh Bank</span>
          </Link>
          {children}
        </div>
      </main>
    </div>
  );
}

const ENTRIES = [
  ['02 Sep', 'Salary - Nimbus Technologies', '', '85,000.00', '2,95,704.00'],
  ['07 Sep', 'House rent', '18,000.00', '', '2,77,704.00'],
  ['12 Sep', 'Broadband bill', '1,199.00', '', '2,76,505.00'],
];

function PassbookFragment() {
  return (
    <div aria-hidden="true" className="relative mt-10 max-w-md -rotate-1 rounded-md bg-[#f7faf8] p-5 text-ink shadow-[0_24px_50px_-20px_rgb(0_0_0/0.55)]">
      <div className="flex items-baseline justify-between font-mono text-[0.62rem] uppercase tracking-[0.16em] text-ink-faint">
        <span>Savings A/c 0001 1000 0015</span>
        <span>Page 14</span>
      </div>
      <div className="mt-3 grid grid-cols-[2.7rem_minmax(0,1fr)_3.7rem_4rem_4.5rem] border-b-[3px] border-double border-rule-strong pb-1.5 font-mono text-[0.58rem] uppercase tracking-[0.12em] text-ink-faint">
        <span>Date</span><span>Particulars</span><span className="text-right">Withdr.</span><span className="text-right">Deposit</span><span className="text-right">Balance</span>
      </div>
      {ENTRIES.map(([date, particulars, out, inn, balance]) => (
        <div key={date} className="grid grid-cols-[2.7rem_minmax(0,1fr)_3.7rem_4rem_4.5rem] border-b border-rule py-2 text-[0.7rem]">
          <span className="text-ink-soft">{date}</span>
          <span className="truncate pr-2">{particulars}</span>
          <span className="figures text-right text-debit">{out}</span>
          <span className="figures text-right text-credit">{inn}</span>
          <span className="figures text-right font-medium">{balance}</span>
        </div>
      ))}
      <span className="stamp absolute -bottom-3 right-6 bg-[#f7faf8] text-[0.62rem]">KYC verified · Fort branch</span>
    </div>
  );
}
