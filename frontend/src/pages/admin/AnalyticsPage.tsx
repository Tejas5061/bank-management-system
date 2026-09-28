import { Play } from 'lucide-react';
import { useState } from 'react';
import { toast } from 'sonner';
import { useDailyVolume, useOverview, useRunJob } from '@/api/admin';
import { BarList, VolumeChart } from '@/components/charts/Charts';
import { Button } from '@/components/ui/Button';
import { Card, CardHeader, EmptyState, Figure, PageHeader } from '@/components/ui/Card';
import { LoadingBlock } from '@/components/ui/Spinner';
import { Tabs } from '@/components/ui/Tabs';
import { errorMessage } from '@/lib/errors';
import { ACCOUNT_TYPE_LABEL, formatINR, LOAN_TYPE_LABEL, plural } from '@/lib/format';

const JOBS = [
  { job: 'interest', title: 'Post savings interest', detail: 'Credits last month’s interest (daily-product method). Runs 01:30 on the 1st.' },
  { job: 'fd-maturity', title: 'Pay out matured FDs', detail: 'Credits principal plus interest to the payout account. Runs 00:15 daily.' },
  { job: 'emi-debit', title: 'Collect due EMIs', detail: 'Debits due and overdue instalments, oldest first. Runs 06:00 daily.' },
] as const;

export default function AnalyticsPage() {
  const overview = useOverview();
  const [days, setDays] = useState<'30' | '90'>('30');
  const volume = useDailyVolume(Number(days));

  if (overview.isPending) return <LoadingBlock />;
  if (overview.isError) return <EmptyState title="Could not load analytics" description={errorMessage(overview.error)} />;
  const o = overview.data;
  const loans = o.loanPortfolio;

  return (
    <>
      <PageHeader title="Bank at a glance" description="Figures are live from the ledger." />

      <Card className="grid gap-6 p-5 sm:grid-cols-2 xl:grid-cols-4">
        <Figure label="Total deposits" value={formatINR(o.totalDeposits)} note={`${plural(o.activeAccounts, 'active account')}`} />
        <Figure label="Loans outstanding" value={formatINR(loans.totalOutstanding)} note={plural(loans.activeLoans, 'active loan')} />
        <Figure label="Today's volume" value={formatINR(o.todayTransactionVolume)} note={`${o.todayTransactionCount} ledger ${o.todayTransactionCount === 1 ? 'entry' : 'entries'}`} />
        <Figure label="KYC waiting" value={o.pendingKyc} note={`of ${plural(o.totalCustomers, 'customer')} · ${plural(o.frozenAccounts, 'frozen account')}`} tone={o.pendingKyc ? 'caution' : 'ink'} />
      </Card>

      <Card className="mt-6">
        <CardHeader
          title="Daily transaction volume"
          description="Successful credits and debits across all accounts, by business date."
          action={<Tabs<'30' | '90'> label="Range" value={days} onChange={setDays} options={[{ value: '30', label: '30 days' }, { value: '90', label: '90 days' }]} />}
        />
        <div className="p-5">{volume.data ? <VolumeChart data={volume.data} /> : <LoadingBlock />}</div>
      </Card>

      <div className="mt-6 grid gap-6 lg:grid-cols-2">
        <Card>
          <CardHeader title="Deposits by account type" />
          <div className="px-5 py-3">
            <BarList caption="Deposits by account type" items={o.depositsByType.map((d) => ({
              label: ACCOUNT_TYPE_LABEL[d.accountType], value: d.balance, note: plural(d.accounts, 'account'),
            }))} />
          </div>
        </Card>
        <Card>
          <CardHeader title="Loan portfolio" description={`${plural(loans.pendingApplications, 'application')} waiting · ${plural(loans.overdueInstallments, 'overdue EMI')} · ${loans.closedLoans} closed`} />
          <div className="px-5 py-3">
            <BarList caption="Outstanding principal by loan type" items={loans.byType.map((l) => ({
              label: LOAN_TYPE_LABEL[l.loanType], value: l.outstanding, note: `${l.activeLoans} active · ${l.pending} pending`,
            }))} />
            <p className="mt-2 text-xs text-ink-faint">Outstanding principal. Total disbursed to date: {formatINR(loans.totalDisbursed)}.</p>
          </div>
        </Card>
      </div>

      <Card className="mt-6">
        <CardHeader title="Scheduled jobs" description="These run automatically. Run one now to catch up after downtime or for a demo; each is safe to run twice." />
        <ul>
          {JOBS.map((job) => <JobRow key={job.job} {...job} />)}
        </ul>
      </Card>
    </>
  );
}

function JobRow({ job, title, detail }: (typeof JOBS)[number]) {
  const run = useRunJob();
  return (
    <li className="ledger-row flex flex-wrap items-center gap-4 px-5 py-4">
      <div className="min-w-0 flex-1">
        <p className="font-medium">{title}</p>
        <p className="text-sm text-ink-soft">{detail}</p>
        {run.data && (
          <p className="mt-1 text-sm text-ink">
            Last run: {run.data.processed} checked · {run.data.succeeded} processed · {run.data.skipped} skipped · {run.data.failed} failed · {formatINR(run.data.totalAmount)}
          </p>
        )}
      </div>
      <Button variant="secondary" loading={run.isPending} onClick={() => run.mutate(job, {
        onSuccess: (r) => toast.success(`${title}: ${r.succeeded} processed`),
        onError: (e) => toast.error(errorMessage(e)),
      })}>
        <Play className="size-4" aria-hidden="true" /> Run now
      </Button>
    </li>
  );
}
