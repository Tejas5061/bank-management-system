import { zodResolver } from '@hookform/resolvers/zod';
import { ShieldCheck } from 'lucide-react';
import { useMemo, useState } from 'react';
import { useForm, useWatch } from 'react-hook-form';
import { Link, useSearchParams } from 'react-router-dom';
import { z } from 'zod';
import { useBeneficiaries, useBeneficiaryTransfer, useInternalTransfer, useMyAccounts } from '@/api/customer';
import { Button } from '@/components/ui/Button';
import { Card, EmptyState, PageHeader } from '@/components/ui/Card';
import { Dialog } from '@/components/ui/Dialog';
import { Input, Select } from '@/components/ui/Field';
import { Code } from '@/components/ui/Money';
import { LoadingBlock } from '@/components/ui/Spinner';
import { Stamp } from '@/components/ui/Status';
import { Tabs } from '@/components/ui/Tabs';
import { errorCode, errorMessage } from '@/lib/errors';
import { ACCOUNT_TYPE_LABEL, formatDateTime, formatINR } from '@/lib/format';
import { amountSchema } from '@/lib/schemas';
import { newIdempotencyKey } from '@/lib/util';
import type { Account, Beneficiary, TransferResponse } from '@/types/api';

type Mode = 'payee' | 'own';

const schema = z.object({
  fromAccountId: z.number({ error: 'Choose the account to pay from' }),
  toId: z.number({ error: 'Choose where the money goes' }),
  amount: amountSchema(1),
  remarks: z.string().max(140, 'Keep remarks under 140 characters').optional(),
});
type TransferForm = z.infer<typeof schema>;

export default function TransferPage() {
  const [params] = useSearchParams();
  const [mode, setMode] = useState<Mode>(params.get('mode') === 'own' ? 'own' : 'payee');
  const accounts = useMyAccounts();
  const beneficiaries = useBeneficiaries();
  const [receipt, setReceipt] = useState<TransferResponse | null>(null);

  const operative = useMemo(
    () => (accounts.data ?? []).filter((a) => a.accountType !== 'FIXED_DEPOSIT' && a.status === 'ACTIVE'),
    [accounts.data],
  );

  if (accounts.isPending || beneficiaries.isPending) return <LoadingBlock />;

  return (
    <>
      <PageHeader title="Transfer money" description="Pay a saved payee, or move money between your own accounts." />
      <div className="grid gap-6 xl:grid-cols-[minmax(0,1fr)_320px]">
        <Card className="p-5 sm:p-6">
          <Tabs<Mode>
            label="Transfer type"
            value={mode}
            onChange={(m) => { setMode(m); setReceipt(null); }}
            options={[{ value: 'payee', label: 'To a payee' }, { value: 'own', label: 'Between my accounts' }]}
          />
          {receipt ? (
            <Receipt receipt={receipt} onAnother={() => setReceipt(null)} />
          ) : operative.length === 0 ? (
            <EmptyState title="No account can send money yet" description="Accounts become active for transfers once your KYC is verified." />
          ) : (
            <TransferForm key={mode} mode={mode} accounts={operative} beneficiaries={beneficiaries.data ?? []} onDone={setReceipt} />
          )}
        </Card>
        <aside className="flex flex-col gap-3 text-sm text-ink-soft">
          <div className="flex gap-3 rounded-[var(--radius-card)] border border-rule bg-surface p-4">
            <ShieldCheck className="mt-0.5 size-5 shrink-0 text-kosh-500" aria-hidden="true" />
            <p>Each transfer carries a one-time key, so pressing the button twice or retrying after a dropped connection never sends the money twice.</p>
          </div>
          <p className="px-1">New payees can receive money 30 minutes after you add them. Savings accounts can send up to ₹2,00,000 a day.</p>
        </aside>
      </div>
    </>
  );
}

function TransferForm({ mode, accounts, beneficiaries, onDone }: {
  mode: Mode;
  accounts: Account[];
  beneficiaries: Beneficiary[];
  onDone: (receipt: TransferResponse) => void;
}) {
  const internal = useInternalTransfer();
  const toPayee = useBeneficiaryTransfer();
  const [review, setReview] = useState<TransferForm | null>(null);
  const [idempotencyKey, setIdempotencyKey] = useState(newIdempotencyKey);
  const [error, setError] = useState<string | null>(null);
  const { register, handleSubmit, control, formState: { errors } } = useForm<TransferForm>({
    resolver: zodResolver(schema),
    defaultValues: { fromAccountId: accounts[0]?.id },
  });

  const fromId = useWatch({ control, name: 'fromAccountId' });
  const from = accounts.find((a) => a.id === Number(fromId));
  const destinations = mode === 'own' ? accounts.filter((a) => a.id !== from?.id) : [];
  const describeDestination = (values: TransferForm) => {
    if (mode === 'own') {
      const to = accounts.find((a) => a.id === values.toId);
      return to ? `${ACCOUNT_TYPE_LABEL[to.accountType]} ··${to.accountNumber.slice(-4)}` : '';
    }
    const payee = beneficiaries.find((b) => b.id === values.toId);
    return payee ? `${payee.name} · ${payee.bankName} ··${payee.maskedAccountNumber.slice(-4)}` : '';
  };

  const send = async () => {
    if (!review) return;
    setError(null);
    try {
      const receipt = mode === 'own'
        ? await internal.mutateAsync({ idempotencyKey, body: { fromAccountId: review.fromAccountId, toAccountId: review.toId, amount: review.amount, remarks: review.remarks || undefined } })
        : await toPayee.mutateAsync({ idempotencyKey, body: { fromAccountId: review.fromAccountId, beneficiaryId: review.toId, amount: review.amount, remarks: review.remarks || undefined } });
      setIdempotencyKey(newIdempotencyKey());
      setReview(null);
      onDone(receipt);
    } catch (e) {
      // A declined transfer releases its key server-side; a key clash means the form changed.
      if (errorCode(e) === 'IDEMPOTENCY_KEY_REUSED') setIdempotencyKey(newIdempotencyKey());
      setError(errorMessage(e));
    }
  };

  const noDestinations = mode === 'own' ? destinations.length === 0 : beneficiaries.length === 0;

  return (
    <form onSubmit={handleSubmit((values) => { setError(null); setReview(values); })} className="mt-6 flex flex-col gap-5" noValidate>
      <Select label="From" error={errors.fromAccountId?.message} {...register('fromAccountId', { valueAsNumber: true })}>
        {accounts.map((a) => (
          <option key={a.id} value={a.id}>
            {ACCOUNT_TYPE_LABEL[a.accountType]} ··{a.accountNumber.slice(-4)} · {formatINR(a.availableBalance)} available
          </option>
        ))}
      </Select>

      {noDestinations ? (
        <p className="rounded-lg bg-paper px-4 py-3 text-sm text-ink-soft">
          {mode === 'own'
            ? 'You need a second active account to move money between your own accounts.'
            : <>You have no saved payees yet. <Link to="/app/beneficiaries" className="font-medium text-kosh-600 hover:underline">Add a payee</Link>.</>}
        </p>
      ) : (
        <Select label="To" error={errors.toId?.message} defaultValue="" {...register('toId', { valueAsNumber: true })}>
          <option value="" disabled>{mode === 'own' ? 'Choose an account' : 'Choose a payee'}</option>
          {mode === 'own'
            ? destinations.map((a) => <option key={a.id} value={a.id}>{ACCOUNT_TYPE_LABEL[a.accountType]} ··{a.accountNumber.slice(-4)}</option>)
            : beneficiaries.map((b) => (
              <option key={b.id} value={b.id} disabled={!b.active}>
                {b.nickname ? `${b.nickname} (${b.name})` : b.name} · {b.bankName} ··{b.maskedAccountNumber.slice(-4)}
                {!b.active ? ` - available from ${formatDateTime(b.activatedAt)}` : ''}
              </option>
            ))}
        </Select>
      )}

      <Input
        label="Amount"
        prefix="₹"
        type="number"
        inputMode="decimal"
        step="0.01"
        min="1"
        error={errors.amount?.message}
        hint={from ? `You can send up to ${formatINR(from.availableBalance)} from this account` : undefined}
        {...register('amount', { valueAsNumber: true })}
      />
      <Input label="Remarks (optional)" placeholder="Shown in both passbooks" error={errors.remarks?.message} {...register('remarks')} />

      <Button type="submit" size="lg" disabled={noDestinations}>Review transfer</Button>

      <Dialog open={review !== null} onClose={() => setReview(null)} title="Confirm transfer">
        {review && (
          <div className="flex flex-col gap-5">
            <p className="figures text-center font-display text-4xl font-semibold">{formatINR(review.amount)}</p>
            <dl className="divide-y divide-rule rounded-lg border border-rule text-sm">
              <div className="flex justify-between gap-4 px-4 py-3"><dt className="text-ink-soft">From</dt><dd className="text-right">{from ? `${ACCOUNT_TYPE_LABEL[from.accountType]} ··${from.accountNumber.slice(-4)}` : ''}</dd></div>
              <div className="flex justify-between gap-4 px-4 py-3"><dt className="text-ink-soft">To</dt><dd className="text-right">{describeDestination(review)}</dd></div>
              {review.remarks && <div className="flex justify-between gap-4 px-4 py-3"><dt className="text-ink-soft">Remarks</dt><dd className="text-right">{review.remarks}</dd></div>}
            </dl>
            {error && <p className="rounded-lg bg-debit-soft px-3 py-2.5 text-sm text-debit" role="alert">{error}</p>}
            <div className="flex justify-end gap-2">
              <Button variant="ghost" onClick={() => setReview(null)}>Edit</Button>
              <Button onClick={send} loading={internal.isPending || toPayee.isPending}>Send {formatINR(review.amount)}</Button>
            </div>
          </div>
        )}
      </Dialog>
    </form>
  );
}

function Receipt({ receipt, onAnother }: { receipt: TransferResponse; onAnother: () => void }) {
  return (
    <div className="mt-6 flex flex-col gap-5">
      <div className="flex items-start justify-between gap-4">
        <div>
          <p className="text-sm text-ink-soft">Sent to {receipt.toName}</p>
          <p className="figures font-display text-4xl font-semibold">{formatINR(receipt.amount)}</p>
        </div>
        <Stamp status="SUCCESS" label="Paid" land className="text-sm" />
      </div>
      <dl className="divide-y divide-rule rounded-lg border border-rule text-sm">
        <div className="flex justify-between gap-4 px-4 py-3"><dt className="text-ink-soft">Reference</dt><dd><Code>{receipt.referenceNumber}</Code></dd></div>
        <div className="flex justify-between gap-4 px-4 py-3"><dt className="text-ink-soft">To account</dt><dd><Code>{`··${receipt.toAccountNumber.slice(-4)} · ${receipt.toIfsc}`}</Code></dd></div>
        <div className="flex justify-between gap-4 px-4 py-3"><dt className="text-ink-soft">Balance after</dt><dd className="figures">{formatINR(receipt.balanceAfter)}</dd></div>
        <div className="flex justify-between gap-4 px-4 py-3"><dt className="text-ink-soft">Completed</dt><dd>{formatDateTime(receipt.completedAt)}</dd></div>
      </dl>
      <div className="flex gap-2">
        <Button onClick={onAnother}>New transfer</Button>
        <Link to="/app" className="inline-flex h-10 items-center rounded-lg px-4 text-sm font-medium text-ink-soft hover:bg-kosh-50">Back to overview</Link>
      </div>
    </div>
  );
}
