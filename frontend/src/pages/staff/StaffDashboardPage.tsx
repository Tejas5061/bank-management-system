import { ArrowRight } from 'lucide-react';
import type { ReactNode } from 'react';
import { Link } from 'react-router-dom';
import { useStaffDashboard } from '@/api/staff';
import { Card, EmptyState, Figure, PageHeader } from '@/components/ui/Card';
import { LoadingBlock } from '@/components/ui/Spinner';
import { errorMessage } from '@/lib/errors';
import { formatINR } from '@/lib/format';

export default function StaffDashboardPage() {
  const dashboard = useStaffDashboard();
  if (dashboard.isPending) return <LoadingBlock />;
  if (dashboard.isError) return <EmptyState title="Could not load today's desk" description={errorMessage(dashboard.error)} />;
  const d = dashboard.data;

  return (
    <>
      <PageHeader title="Today at the desk" description="Work waiting for you, and the cash that crossed the counter today." />
      <div className="grid gap-4 md:grid-cols-3">
        <Queue to="/staff/kyc" count={d.pendingKyc} label="KYC checks waiting" action="Open KYC queue" />
        <Queue to="/staff/loans" count={d.pendingLoans} label="Loan applications to review" action="Review loans" />
        <Queue to="/staff/loans" count={d.overdueInstallments} label="Overdue EMIs" action="See loans" tone={d.overdueInstallments ? 'debit' : 'ink'} />
      </div>
      <Card className="mt-6 grid gap-6 p-5 sm:grid-cols-3">
        <Figure label="Cash deposits today" value={formatINR(d.todayDepositAmount)} note={`${d.todayDepositCount} deposit${d.todayDepositCount === 1 ? '' : 's'}`} tone="credit" />
        <Figure label="Cash withdrawals today" value={formatINR(d.todayWithdrawalAmount)} note={`${d.todayWithdrawalCount} withdrawal${d.todayWithdrawalCount === 1 ? '' : 's'}`} tone="debit" />
        <Figure label="Frozen accounts" value={d.frozenAccounts} note="Across all branches" />
      </Card>
      <div className="mt-6 flex flex-wrap gap-3">
        <Link to="/staff/cash" className="inline-flex h-10 items-center rounded-lg bg-kosh-800 px-4 text-sm font-medium text-paper hover:bg-kosh-700">Open cash desk</Link>
        <Link to="/staff/customers?onboard=1" className="inline-flex h-10 items-center rounded-lg border border-rule-strong bg-surface px-4 text-sm font-medium hover:bg-kosh-50">Onboard a customer</Link>
      </div>
    </>
  );
}

function Queue({ to, count, label, action, tone = 'ink' }: { to: string; count: number; label: string; action: ReactNode; tone?: 'ink' | 'debit' }) {
  return (
    <Link to={to} className="group">
      <Card className="flex h-full flex-col justify-between gap-6 p-5 transition-shadow group-hover:shadow-[var(--shadow-lift)]">
        <div>
          <p className={`figures font-display text-5xl font-semibold ${tone === 'debit' ? 'text-debit' : 'text-ink'}`}>{count}</p>
          <p className="mt-1 text-ink-soft">{label}</p>
        </div>
        <span className="inline-flex items-center gap-1 text-sm font-medium text-kosh-600">
          {action} <ArrowRight className="size-4 transition-transform group-hover:translate-x-0.5" aria-hidden="true" />
        </span>
      </Card>
    </Link>
  );
}
