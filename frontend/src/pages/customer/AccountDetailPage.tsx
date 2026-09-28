import { ChevronLeft } from 'lucide-react';
import { Link, useParams } from 'react-router-dom';
import { useAccount } from '@/api/common';
import { AccountHistory } from '@/components/AccountHistory';
import { Card, EmptyState, Figure } from '@/components/ui/Card';
import { Code } from '@/components/ui/Money';
import { LoadingBlock } from '@/components/ui/Spinner';
import { Stamp } from '@/components/ui/Status';
import { errorMessage } from '@/lib/errors';
import { ACCOUNT_TYPE_LABEL, formatINR } from '@/lib/format';

export default function AccountDetailPage() {
  const accountId = Number(useParams().accountId);
  const account = useAccount(accountId);

  if (account.isPending) return <LoadingBlock />;
  if (account.isError) return <EmptyState title="Account not available" description={errorMessage(account.error)} />;
  const a = account.data;

  return (
    <div className="flex flex-col gap-6">
      <Link to="/app/accounts" className="inline-flex w-fit items-center gap-1 text-sm font-medium text-kosh-600 hover:underline">
        <ChevronLeft className="size-4" aria-hidden="true" /> All accounts
      </Link>
      <Card className="flex flex-wrap items-center justify-between gap-6 p-5">
        <div>
          <div className="flex items-center gap-3">
            <h1 className="text-2xl font-semibold">{ACCOUNT_TYPE_LABEL[a.accountType]} account</h1>
            {a.status !== 'ACTIVE' && <Stamp status={a.status} />}
          </div>
          <p className="mt-1 text-sm text-ink-soft">
            <Code group className="text-ink">{a.accountNumber}</Code> · IFSC <Code>{a.ifsc}</Code> · {a.branchName}
          </p>
          {a.statusReason && a.status !== 'ACTIVE' && <p className="mt-1 text-sm text-debit">{a.statusReason}</p>}
        </div>
        <div className="flex gap-8">
          <Figure label="Balance" value={formatINR(a.balance)} />
          <Figure label="Available" value={formatINR(a.availableBalance)} />
          <Figure label="Interest" value={`${a.interestRate}%`} />
        </div>
      </Card>
      <AccountHistory accountId={accountId} />
    </div>
  );
}
