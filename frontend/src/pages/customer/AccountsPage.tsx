import { Plus } from 'lucide-react';
import { useState } from 'react';
import { Link } from 'react-router-dom';
import { toast } from 'sonner';
import { useMyAccounts, useOpenAccount } from '@/api/customer';
import { Button } from '@/components/ui/Button';
import { Card, EmptyState, PageHeader } from '@/components/ui/Card';
import { Dialog } from '@/components/ui/Dialog';
import { Select } from '@/components/ui/Field';
import { Code } from '@/components/ui/Money';
import { LoadingBlock } from '@/components/ui/Spinner';
import { Stamp } from '@/components/ui/Status';
import { errorMessage } from '@/lib/errors';
import { ACCOUNT_TYPE_LABEL, formatDate, formatINR } from '@/lib/format';
import type { Account, AccountType } from '@/types/api';

export default function AccountsPage() {
  const accounts = useMyAccounts();
  const [opening, setOpening] = useState(false);

  return (
    <>
      <PageHeader
        title="Accounts"
        description="Savings, current and fixed deposit accounts in your name."
        action={<Button onClick={() => setOpening(true)}><Plus className="size-4" aria-hidden="true" /> Open an account</Button>}
      />
      {accounts.isPending ? (
        <LoadingBlock />
      ) : accounts.isError ? (
        <EmptyState title="Could not load accounts" description={errorMessage(accounts.error)} />
      ) : (
        <div className="grid gap-4 md:grid-cols-2">
          {accounts.data.map((account) => <AccountCard key={account.id} account={account} />)}
        </div>
      )}
      <OpenAccountDialog open={opening} onClose={() => setOpening(false)} />
    </>
  );
}

function AccountCard({ account }: { account: Account }) {
  const isFd = account.accountType === 'FIXED_DEPOSIT';
  return (
    <Link to={isFd ? '/app/deposits' : `/app/accounts/${account.id}`} className="group">
      <Card className="h-full p-5 transition-shadow group-hover:shadow-[var(--shadow-lift)]">
        <div className="flex items-start justify-between gap-3">
          <div>
            <p className="font-medium text-ink">{ACCOUNT_TYPE_LABEL[account.accountType]}</p>
            <p className="mt-0.5 text-sm text-ink-soft"><Code group>{account.accountNumber}</Code></p>
          </div>
          {account.status !== 'ACTIVE' && <Stamp status={account.status} />}
        </div>
        <p className="figures mt-6 font-display text-3xl font-semibold text-ink">{formatINR(account.balance)}</p>
        <dl className="mt-4 grid grid-cols-2 gap-y-1 text-sm">
          <dt className="text-ink-faint">{isFd ? 'Rate' : 'Interest'}</dt>
          <dd className="text-right text-ink-soft">{account.interestRate}% p.a.</dd>
          {!isFd && <><dt className="text-ink-faint">Available</dt><dd className="figures text-right text-ink-soft">{formatINR(account.availableBalance)}</dd></>}
          <dt className="text-ink-faint">Branch</dt>
          <dd className="text-right text-ink-soft">{account.branchName}</dd>
          <dt className="text-ink-faint">Opened</dt>
          <dd className="text-right text-ink-soft">{formatDate(account.openedAt)}</dd>
        </dl>
      </Card>
    </Link>
  );
}

function OpenAccountDialog({ open, onClose }: { open: boolean; onClose: () => void }) {
  const [type, setType] = useState<AccountType>('SAVINGS');
  const openAccount = useOpenAccount();

  const submit = async () => {
    try {
      const account = await openAccount.mutateAsync(type);
      toast.success(`${ACCOUNT_TYPE_LABEL[account.accountType]} account ${account.accountNumber} opened`);
      onClose();
    } catch (e) {
      toast.error(errorMessage(e));
    }
  };

  return (
    <Dialog open={open} onClose={onClose} title="Open an account" description="Opens at your home branch with a zero balance. Your KYC must be verified.">
      <div className="flex flex-col gap-5">
        <Select label="Account type" value={type} onChange={(e) => setType(e.target.value as AccountType)}>
          <option value="SAVINGS">Savings: earns interest, ₹1,000 minimum balance</option>
          <option value="CURRENT">Current: for business, ₹5,000 minimum balance</option>
        </Select>
        <div className="flex justify-end gap-2">
          <Button variant="ghost" onClick={onClose}>Cancel</Button>
          <Button onClick={submit} loading={openAccount.isPending}>Open account</Button>
        </div>
      </div>
    </Dialog>
  );
}
