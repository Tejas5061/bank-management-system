import { ChevronLeft } from 'lucide-react';
import { useState } from 'react';
import { useParams } from 'react-router-dom';
import { toast } from 'sonner';
import { useAccount } from '@/api/common';
import { useChangeAccountStatus } from '@/api/staff';
import { AccountHistory } from '@/components/AccountHistory';
import { Button } from '@/components/ui/Button';
import { Card, EmptyState, Figure } from '@/components/ui/Card';
import { Dialog } from '@/components/ui/Dialog';
import { Textarea } from '@/components/ui/Field';
import { Code } from '@/components/ui/Money';
import { LoadingBlock } from '@/components/ui/Spinner';
import { Stamp } from '@/components/ui/Status';
import { errorMessage } from '@/lib/errors';
import { ACCOUNT_TYPE_LABEL, formatINR } from '@/lib/format';
import type { AccountStatus } from '@/types/api';

/** Staff view of any account: its passbook plus freeze / unfreeze / close. */
export default function StaffAccountPage() {
  const accountId = Number(useParams().accountId);
  const account = useAccount(accountId);
  const change = useChangeAccountStatus();
  const [target, setTarget] = useState<AccountStatus | null>(null);
  const [reason, setReason] = useState('');

  if (account.isPending) return <LoadingBlock />;
  if (account.isError) return <EmptyState title="Account not available" description={errorMessage(account.error)} />;
  const a = account.data;

  const confirm = async () => {
    if (!target) return;
    try {
      await change.mutateAsync({ accountId, status: target, reason: reason.trim() });
      await account.refetch();
      toast.success(`Account ${target === 'ACTIVE' ? 'reactivated' : target.toLowerCase()}`);
      setTarget(null);
      setReason('');
    } catch (e) {
      toast.error(errorMessage(e));
    }
  };

  const verb = target === 'FROZEN' ? 'Freeze' : target === 'CLOSED' ? 'Close' : 'Unfreeze';

  return (
    <div className="flex flex-col gap-6">
      <button type="button" onClick={() => window.history.back()} className="inline-flex w-fit items-center gap-1 text-sm font-medium text-kosh-600 hover:underline">
        <ChevronLeft className="size-4" aria-hidden="true" /> Back
      </button>
      <Card className="flex flex-wrap items-center justify-between gap-6 p-5">
        <div>
          <div className="flex items-center gap-3">
            <h1 className="text-2xl font-semibold">{ACCOUNT_TYPE_LABEL[a.accountType]} account</h1>
            <Stamp status={a.status} />
          </div>
          <p className="mt-1 text-sm text-ink-soft"><Code group className="text-ink">{a.accountNumber}</Code> · IFSC <Code>{a.ifsc}</Code></p>
          {a.statusReason && <p className="mt-1 text-sm text-ink-soft">Reason: {a.statusReason}</p>}
        </div>
        <Figure label="Balance" value={formatINR(a.balance)} />
        {a.status !== 'CLOSED' && (
          <div className="flex gap-2">
            {a.status === 'ACTIVE'
              ? <Button variant="danger" onClick={() => setTarget('FROZEN')}>Freeze</Button>
              : <Button onClick={() => setTarget('ACTIVE')}>Unfreeze</Button>}
            {a.accountType !== 'FIXED_DEPOSIT' && <Button variant="secondary" onClick={() => setTarget('CLOSED')}>Close</Button>}
          </div>
        )}
      </Card>
      <AccountHistory accountId={accountId} />

      <Dialog open={target !== null} onClose={() => setTarget(null)} title={`${verb} this account?`}
        description={target === 'FROZEN' ? 'A frozen account cannot send or receive money until it is unfrozen. The customer is emailed the reason.'
          : target === 'CLOSED' ? 'Only a zero-balance account can be closed. Closing cannot be undone.' : 'The customer can transact again straight away.'}>
        <div className="flex flex-col gap-4">
          <Textarea label="Reason (recorded in the audit log)" value={reason} onChange={(e) => setReason(e.target.value)} maxLength={255} />
          <div className="flex justify-end gap-2">
            <Button variant="ghost" onClick={() => setTarget(null)}>Cancel</Button>
            <Button variant={target === 'ACTIVE' ? 'primary' : 'danger'} onClick={confirm} loading={change.isPending} disabled={reason.trim().length < 3}>
              {verb} account
            </Button>
          </div>
        </div>
      </Dialog>
    </div>
  );
}
