import { useState } from 'react';
import { toast } from 'sonner';
import { useAccountLookup, useCash } from '@/api/staff';
import { Button } from '@/components/ui/Button';
import { Card, CardHeader, EmptyState, Figure, PageHeader } from '@/components/ui/Card';
import { Input } from '@/components/ui/Field';
import { Code } from '@/components/ui/Money';
import { Spinner } from '@/components/ui/Spinner';
import { Stamp } from '@/components/ui/Status';
import { Tabs } from '@/components/ui/Tabs';
import { errorCode, errorMessage } from '@/lib/errors';
import { ACCOUNT_TYPE_LABEL, formatDateTime, formatINR } from '@/lib/format';
import { newIdempotencyKey } from '@/lib/util';
import type { CashResponse } from '@/types/api';

type Kind = 'deposits' | 'withdrawals';

export default function CashDeskPage() {
  const [accountNumber, setAccountNumber] = useState('');
  const [kind, setKind] = useState<Kind>('deposits');
  const [amount, setAmount] = useState('');
  const [remarks, setRemarks] = useState('');
  const [key, setKey] = useState(newIdempotencyKey);
  const [slips, setSlips] = useState<CashResponse[]>([]);
  const digits = accountNumber.replace(/\s/g, '');
  const lookup = useAccountLookup(digits);
  const cash = useCash();
  const value = Number(amount);
  const account = lookup.data;
  const blocked = account && (account.kycStatus !== 'VERIFIED' || account.status !== 'ACTIVE' || account.accountType === 'FIXED_DEPOSIT');

  const submit = async () => {
    try {
      const slip = await cash.mutateAsync({ kind, idempotencyKey: key, accountNumber: digits, amount: value, remarks: remarks.trim() || undefined });
      setSlips((s) => [slip, ...s].slice(0, 8));
      setKey(newIdempotencyKey());
      setAmount('');
      setRemarks('');
      await lookup.refetch();
      toast.success(`${kind === 'deposits' ? 'Deposited' : 'Paid out'} ${formatINR(slip.amount)} · ref ${slip.referenceNumber}`);
    } catch (e) {
      if (errorCode(e) === 'IDEMPOTENCY_KEY_REUSED') setKey(newIdempotencyKey());
      toast.error(errorMessage(e));
    }
  };

  return (
    <>
      <PageHeader title="Cash desk" description="Count the cash first, then record it. Each slip is recorded exactly once, even if the button is pressed twice." />
      <div className="grid gap-6 lg:grid-cols-[minmax(0,1fr)_minmax(0,1fr)]">
        <Card className="p-5">
          <Input label="Account number" inputMode="numeric" autoComplete="off" placeholder="12-digit account number"
            value={accountNumber} onChange={(e) => setAccountNumber(e.target.value)} className="[&_input]:font-mono [&_input]:tracking-wider" />
          <div className="mt-5 min-h-40">
            {digits.length < 9 ? (
              <p className="text-sm text-ink-faint">Enter an account number to see the holder and balance.</p>
            ) : lookup.isFetching && !account ? (
              <div className="flex items-center gap-2 text-sm text-ink-soft"><Spinner className="size-4" /> Looking up account</div>
            ) : lookup.isError ? (
              <p className="text-sm text-debit">{errorMessage(lookup.error)}</p>
            ) : account ? (
              <div className="rounded-lg border border-rule p-4">
                <div className="flex items-start justify-between gap-3">
                  <div>
                    <p className="text-lg font-semibold">{account.holderName}</p>
                    <p className="text-sm text-ink-soft">{ACCOUNT_TYPE_LABEL[account.accountType]} · <Code>{account.customerNumber}</Code> · branch <Code>{account.branchCode}</Code></p>
                  </div>
                  <div className="flex flex-col items-end gap-2">
                    <Stamp status={account.kycStatus} label={`KYC ${account.kycStatus.toLowerCase()}`} />
                    {account.status !== 'ACTIVE' && <Stamp status={account.status} />}
                  </div>
                </div>
                <div className="mt-4"><Figure label="Balance" value={formatINR(account.balance)} /></div>
                {blocked && (
                  <p className="mt-3 rounded-md bg-caution-soft px-3 py-2 text-sm text-caution">
                    {account.accountType === 'FIXED_DEPOSIT' ? 'Cash cannot be taken into a fixed deposit.'
                      : account.kycStatus !== 'VERIFIED' ? 'KYC must be verified before this account can take or pay out cash.'
                        : `This account is ${account.status.toLowerCase()}.`}
                  </p>
                )}
              </div>
            ) : null}
          </div>
        </Card>

        <Card className="p-5">
          <Tabs<Kind> label="Transaction" value={kind} onChange={setKind}
            options={[{ value: 'deposits', label: 'Deposit' }, { value: 'withdrawals', label: 'Withdrawal' }]} />
          <div className="mt-5 flex flex-col gap-4">
            <Input label="Amount" prefix="₹" type="number" inputMode="decimal" min="1" step="0.01" value={amount}
              onChange={(e) => setAmount(e.target.value)} hint="Up to ₹10,00,000 per slip" />
            <Input label="Remarks (optional)" value={remarks} onChange={(e) => setRemarks(e.target.value)} maxLength={140} />
            <Button size="lg" onClick={submit} loading={cash.isPending} disabled={!account || !!blocked || !(value > 0)}>
              {kind === 'deposits' ? 'Record deposit' : 'Pay out'} {value > 0 ? formatINR(value) : ''}
            </Button>
          </div>
        </Card>
      </div>

      <Card className="mt-6">
        <CardHeader title="Slips this session" />
        {slips.length === 0 ? <EmptyState title="No cash handled yet in this session" /> : (
          <ul>
            {slips.map((slip) => (
              <li key={slip.referenceNumber} className="ledger-row flex flex-wrap items-center gap-4 px-5 py-3 text-sm">
                <Stamp status="SUCCESS" label={slip.type === 'DEPOSIT' ? 'Deposited' : 'Paid out'} land />
                <span className="min-w-0 flex-1">{slip.holderName} · <Code>{slip.accountNumber}</Code></span>
                <Code className="text-ink-faint">{slip.referenceNumber}</Code>
                <span className="figures font-medium">{formatINR(slip.amount)}</span>
                <span className="text-ink-faint">{formatDateTime(slip.completedAt)}</span>
              </li>
            ))}
          </ul>
        )}
      </Card>
    </>
  );
}
