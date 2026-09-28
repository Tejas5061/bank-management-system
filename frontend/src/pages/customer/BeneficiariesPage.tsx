import { zodResolver } from '@hookform/resolvers/zod';
import { Plus, Trash2 } from 'lucide-react';
import { useState } from 'react';
import { useForm, useWatch } from 'react-hook-form';
import { Link } from 'react-router-dom';
import { toast } from 'sonner';
import { z } from 'zod';
import { useAddBeneficiary, useBeneficiaries, useDeleteBeneficiary } from '@/api/customer';
import { Button } from '@/components/ui/Button';
import { Card, EmptyState, PageHeader } from '@/components/ui/Card';
import { Dialog } from '@/components/ui/Dialog';
import { Input } from '@/components/ui/Field';
import { Code } from '@/components/ui/Money';
import { LoadingBlock } from '@/components/ui/Spinner';
import { Tag } from '@/components/ui/Status';
import { applyFieldErrors, errorMessage } from '@/lib/errors';
import { formatDateTime } from '@/lib/format';
import { ACCOUNT_NUMBER, IFSC } from '@/lib/schemas';
import type { Beneficiary } from '@/types/api';

const schema = z.object({
  name: z.string().trim().min(2, 'Enter the account holder name'),
  nickname: z.string().max(50).optional(),
  accountNumber: z.string().regex(ACCOUNT_NUMBER, 'Account numbers are 9 to 18 digits'),
  confirmAccountNumber: z.string(),
  ifsc: z.string().trim().toUpperCase().regex(IFSC, 'IFSC looks like KOSH0000001'),
  bankName: z.string().optional(),
}).refine((v) => v.accountNumber === v.confirmAccountNumber, {
  path: ['confirmAccountNumber'],
  message: 'The account numbers do not match',
});
type PayeeForm = z.infer<typeof schema>;

export default function BeneficiariesPage() {
  const beneficiaries = useBeneficiaries();
  const remove = useDeleteBeneficiary();
  const [adding, setAdding] = useState(false);
  const [deleting, setDeleting] = useState<Beneficiary | null>(null);

  return (
    <>
      <PageHeader
        title="Payees"
        description="People and businesses you pay. For your safety, a new payee can receive money 30 minutes after it is added."
        action={<Button onClick={() => setAdding(true)}><Plus className="size-4" aria-hidden="true" /> Add payee</Button>}
      />
      <Card>
        {beneficiaries.isPending ? <LoadingBlock /> : !beneficiaries.data?.length ? (
          <EmptyState title="No payees yet" description="Add someone's account number and IFSC to start paying them." action={<Button onClick={() => setAdding(true)}>Add payee</Button>} />
        ) : (
          <ul>
            {beneficiaries.data.map((b) => (
              <li key={b.id} className="ledger-row flex flex-wrap items-center gap-4 px-5 py-4">
                <div className="grid size-10 place-items-center rounded-full bg-kosh-50 font-display font-semibold text-kosh-700" aria-hidden="true">
                  {b.name.charAt(0)}
                </div>
                <div className="min-w-0 flex-1">
                  <p className="font-medium text-ink">
                    {b.name} {b.nickname && <span className="font-normal text-ink-faint">· {b.nickname}</span>}
                  </p>
                  <p className="text-sm text-ink-soft">
                    {b.bankName} · <Code>{b.maskedAccountNumber}</Code> · <Code>{b.ifsc}</Code>
                  </p>
                </div>
                {b.active ? (
                  <Link to="/app/transfer" className="text-sm font-medium text-kosh-600 hover:underline">Pay</Link>
                ) : (
                  <Tag tone="caution">Can receive from {formatDateTime(b.activatedAt)}</Tag>
                )}
                {b.internal && <Tag tone="brand">Kosh Bank</Tag>}
                <button type="button" onClick={() => setDeleting(b)} className="rounded-md p-2 text-ink-faint hover:bg-debit-soft hover:text-debit" aria-label={`Remove ${b.name}`}>
                  <Trash2 className="size-4" />
                </button>
              </li>
            ))}
          </ul>
        )}
      </Card>

      <AddPayeeDialog open={adding} onClose={() => setAdding(false)} />
      <Dialog open={deleting !== null} onClose={() => setDeleting(null)} title="Remove payee?">
        {deleting && (
          <div className="flex flex-col gap-5">
            <p className="text-ink-soft">
              {deleting.name} will be removed from your payees. Adding them again later starts a new 30-minute cooling period.
            </p>
            <div className="flex justify-end gap-2">
              <Button variant="ghost" onClick={() => setDeleting(null)}>Keep</Button>
              <Button
                variant="danger"
                loading={remove.isPending}
                onClick={async () => {
                  try {
                    await remove.mutateAsync(deleting.id);
                    toast.success(`${deleting.name} removed`);
                    setDeleting(null);
                  } catch (e) {
                    toast.error(errorMessage(e));
                  }
                }}
              >
                Remove payee
              </Button>
            </div>
          </div>
        )}
      </Dialog>
    </>
  );
}

function AddPayeeDialog({ open, onClose }: { open: boolean; onClose: () => void }) {
  const add = useAddBeneficiary();
  const [error, setError] = useState<string | null>(null);
  const { register, handleSubmit, reset, control, setError: setFieldError, formState: { errors } } = useForm<PayeeForm>({ resolver: zodResolver(schema) });
  const ifsc = (useWatch({ control, name: 'ifsc' }) ?? '').toUpperCase();
  const otherBank = ifsc.length === 11 && !ifsc.startsWith('KOSH');

  const close = () => {
    reset();
    setError(null);
    onClose();
  };

  const onSubmit = handleSubmit(async ({ confirmAccountNumber: _confirm, ...values }) => {
    setError(null);
    try {
      const payee = await add.mutateAsync({ ...values, nickname: values.nickname || undefined, bankName: values.bankName || undefined });
      toast.success(`${payee.name} added. You can pay them from ${formatDateTime(payee.activatedAt)}.`);
      close();
    } catch (e) {
      if (!applyFieldErrors(e, setFieldError)) setError(errorMessage(e));
    }
  });

  return (
    <Dialog open={open} onClose={close} title="Add a payee" description="We will email you whenever a payee is added.">
      <form onSubmit={onSubmit} className="grid gap-4 sm:grid-cols-2" noValidate>
        <Input label="Account holder name" error={errors.name?.message} {...register('name')} className="sm:col-span-2" />
        <Input label="Account number" inputMode="numeric" autoComplete="off" error={errors.accountNumber?.message} {...register('accountNumber')} />
        <Input label="Confirm account number" inputMode="numeric" autoComplete="off" onPaste={(e) => e.preventDefault()} error={errors.confirmAccountNumber?.message} {...register('confirmAccountNumber')} />
        <Input label="IFSC" placeholder="KOSH0000001" autoCapitalize="characters" error={errors.ifsc?.message} {...register('ifsc')} />
        <Input label="Nickname (optional)" error={errors.nickname?.message} {...register('nickname')} />
        {otherBank && <Input label="Bank name" error={errors.bankName?.message} {...register('bankName')} className="sm:col-span-2" />}
        {error && <p className="rounded-lg bg-debit-soft px-3 py-2.5 text-sm text-debit sm:col-span-2" role="alert">{error}</p>}
        <div className="flex justify-end gap-2 sm:col-span-2">
          <Button variant="ghost" onClick={close}>Cancel</Button>
          <Button type="submit" loading={add.isPending}>Add payee</Button>
        </div>
      </form>
    </Dialog>
  );
}
