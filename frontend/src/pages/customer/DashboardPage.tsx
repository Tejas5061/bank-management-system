import { ArrowLeftRight, Download, PiggyBank, UserPlus } from 'lucide-react';
import { useState } from 'react';
import { Link } from 'react-router-dom';
import { toast } from 'sonner';
import { useHistory } from '@/api/common';
import { useDashboard } from '@/api/customer';
import { BarList, CashflowChart } from '@/components/charts/Charts';
import { Passbook } from '@/components/Passbook';
import { Card, CardHeader, EmptyState, Figure } from '@/components/ui/Card';
import { Code } from '@/components/ui/Money';
import { LoadingBlock } from '@/components/ui/Spinner';
import { Stamp } from '@/components/ui/Status';
import { errorMessage } from '@/lib/errors';
import { ACCOUNT_TYPE_LABEL, formatINR, TRANSACTION_LABEL } from '@/lib/format';
import { cn, downloadFile } from '@/lib/util';
import type { Account } from '@/types/api';

export default function DashboardPage() {
  const dashboard = useDashboard();
  if (dashboard.isPending) return <LoadingBlock label="Loading your accounts" />;
  if (dashboard.isError) return <EmptyState title="We could not load your dashboard" description={errorMessage(dashboard.error)} />;
  const d = dashboard.data;
  const operative = d.accounts.filter((a) => a.accountType !== 'FIXED_DEPOSIT' && a.status !== 'CLOSED');

  return (
    <div className="flex flex-col gap-6">
      {d.kycStatus !== 'VERIFIED' && (
        <div className="flex flex-wrap items-center gap-4 rounded-[var(--radius-card)] border border-caution/30 bg-caution-soft px-5 py-4">
          <Stamp status={d.kycStatus} label={d.kycStatus === 'PENDING' ? 'KYC pending' : 'KYC rejected'} />
          <p className="min-w-0 flex-1 text-sm text-ink">
            Your accounts can't send or receive money until KYC is verified. Take your PAN and Aadhaar to your home branch. It usually takes one visit.
          </p>
        </div>
      )}

      {operative.length > 0 ? <PassbookCard accounts={operative} /> : (
        <Card><EmptyState title="No active accounts" description="Open a savings or current account from the Accounts page." /></Card>
      )}

      <div className="grid gap-6 lg:grid-cols-3">
        <Card className="flex flex-col gap-5 p-5">
          <Figure label="In your accounts" value={formatINR(d.totalBalance)} />
          <Figure label="In fixed deposits" value={formatINR(d.fixedDepositTotal)} />
          <Figure
            label="Loans outstanding"
            value={formatINR(d.loanOutstanding)}
            note={d.activeLoans ? `${d.activeLoans} active loan${d.activeLoans > 1 ? 's' : ''}` : 'No active loans'}
          />
        </Card>
        <Card className="lg:col-span-2">
          <CardHeader title="Money in and out" description="Last six months, all accounts. Moves between your own accounts are left out." />
          <div className="p-5"><CashflowChart data={d.monthlyCashflow} /></div>
        </Card>
      </div>

      <div className="grid gap-6 lg:grid-cols-3">
        <Card className="lg:col-span-2">
          <CardHeader title="Where your money went" description="Last 30 days" />
          <div className="px-5 py-3">
            {d.spendingByCategory.length ? (
              <BarList
                caption="Spending by category, last 30 days"
                items={d.spendingByCategory.map((s) => ({
                  label: TRANSACTION_LABEL[s.type],
                  value: s.amount,
                  note: `${s.count} payment${s.count > 1 ? 's' : ''}`,
                }))}
              />
            ) : (
              <EmptyState title="Nothing spent in the last 30 days" />
            )}
          </div>
        </Card>
        <Card className="h-fit p-2">
          <QuickLink to="/app/transfer" icon={ArrowLeftRight} label="Send money" />
          <QuickLink to="/app/beneficiaries" icon={UserPlus} label="Add a payee" />
          <QuickLink to="/app/deposits" icon={PiggyBank} label="Book a fixed deposit" />
        </Card>
      </div>
    </div>
  );
}

/**
 * The hero: the selected account as an open passbook page, with the live balance above its
 * latest entries.
 */
function PassbookCard({ accounts }: { accounts: Account[] }) {
  const [selectedId, setSelectedId] = useState(accounts[0].id);
  const account = accounts.find((a) => a.id === selectedId) ?? accounts[0];
  const history = useHistory(account.id, { page: 0 });
  const [downloading, setDownloading] = useState(false);

  const download = async () => {
    setDownloading(true);
    try {
      await downloadFile(`/accounts/${account.id}/statement`, { format: 'PDF' }, 'statement.pdf');
    } catch (e) {
      toast.error(errorMessage(e));
    } finally {
      setDownloading(false);
    }
  };

  return (
    <Card className="overflow-hidden">
      <div className="border-b border-rule bg-[linear-gradient(180deg,#f7faf8,#ffffff)] px-5 pb-5 pt-4">
        {accounts.length > 1 && (
          <div className="mb-4 flex flex-wrap gap-2" role="tablist" aria-label="Choose account">
            {accounts.map((a) => (
              <button
                key={a.id}
                type="button"
                role="tab"
                aria-selected={a.id === account.id}
                onClick={() => setSelectedId(a.id)}
                className={cn(
                  'rounded-full border px-3 py-1 text-sm transition-colors',
                  a.id === account.id ? 'border-kosh-800 bg-kosh-800 text-paper' : 'border-rule-strong text-ink-soft hover:text-ink',
                )}
              >
                {ACCOUNT_TYPE_LABEL[a.accountType]} ··{a.accountNumber.slice(-4)}
              </button>
            ))}
          </div>
        )}
        <div className="flex flex-wrap items-start justify-between gap-4">
          <div>
            <p className="font-mono text-[0.7rem] uppercase tracking-[0.14em] text-ink-faint">
              {ACCOUNT_TYPE_LABEL[account.accountType]} account · {account.branchName}
            </p>
            <p className="mt-1 text-sm text-ink-soft">
              <Code group className="text-ink">{account.accountNumber}</Code>
              <span className="mx-2 text-rule-strong">|</span>IFSC <Code>{account.ifsc}</Code>
            </p>
          </div>
          {account.status !== 'ACTIVE' && <Stamp status={account.status} />}
        </div>
        <div className="mt-5 flex flex-wrap items-end justify-between gap-4">
          <div>
            <p className="text-sm text-ink-soft">Available to spend</p>
            <p className="figures font-display text-[2.6rem] font-semibold leading-none tracking-tight text-ink">
              {formatINR(account.availableBalance)}
            </p>
            <p className="mt-2 text-sm text-ink-soft">
              Balance {formatINR(account.balance)} · {formatINR(account.minimumBalance)} kept as minimum balance
            </p>
          </div>
          <div className="flex gap-2">
            <button type="button" onClick={download} disabled={downloading} className="inline-flex items-center gap-1.5 rounded-lg border border-rule-strong bg-surface px-3 py-2 text-sm font-medium hover:bg-kosh-50 disabled:opacity-60">
              <Download className="size-4" aria-hidden="true" /> Statement
            </button>
            <Link to={`/app/accounts/${account.id}`} className="inline-flex items-center rounded-lg bg-kosh-800 px-3 py-2 text-sm font-medium text-paper hover:bg-kosh-700">
              Full passbook
            </Link>
          </div>
        </div>
      </div>
      {history.isPending ? (
        <LoadingBlock label="Printing entries" />
      ) : history.data?.content.length ? (
        <Passbook rows={history.data.content.slice(0, 6)} />
      ) : (
        <EmptyState title="No entries yet" description="Deposits and transfers will appear here as they happen." />
      )}
    </Card>
  );
}

function QuickLink({ to, icon: Icon, label }: { to: string; icon: typeof ArrowLeftRight; label: string }) {
  return (
    <Link to={to} className="flex items-center gap-3 rounded-lg px-3 py-2.5 text-sm font-medium text-ink hover:bg-kosh-50">
      <span className="grid size-8 place-items-center rounded-lg bg-kosh-50 text-kosh-700"><Icon className="size-4" aria-hidden="true" /></span>
      {label}
    </Link>
  );
}
