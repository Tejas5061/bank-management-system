import { zodResolver } from '@hookform/resolvers/zod';
import { Plus } from 'lucide-react';
import { useEffect, useState } from 'react';
import { useForm } from 'react-hook-form';
import { toast } from 'sonner';
import { z } from 'zod';
import { useBranches, useSaveBranch } from '@/api/admin';
import { Button } from '@/components/ui/Button';
import { Card, EmptyState, PageHeader } from '@/components/ui/Card';
import { Dialog } from '@/components/ui/Dialog';
import { Input } from '@/components/ui/Field';
import { Code } from '@/components/ui/Money';
import { LoadingBlock } from '@/components/ui/Spinner';
import { Tag } from '@/components/ui/Status';
import { applyFieldErrors, errorMessage } from '@/lib/errors';
import { PINCODE } from '@/lib/schemas';
import type { Branch } from '@/types/api';

const schema = z.object({
  code: z.string().regex(/^\d{6}$/, 'Six digits, e.g. 000004'),
  name: z.string().trim().min(2, 'Enter the branch name'),
  addressLine: z.string().trim().min(3, 'Enter the address'),
  city: z.string().trim().min(2, 'Enter the city'),
  state: z.string().trim().min(2, 'Enter the state'),
  pincode: z.string().regex(PINCODE, 'Enter a 6-digit PIN code'),
  phone: z.string().regex(/^(\d{10,15})?$/, '10 to 15 digits').optional(),
  active: z.boolean(),
});
type BranchForm = z.infer<typeof schema>;

export default function BranchesPage() {
  const branches = useBranches();
  const [editing, setEditing] = useState<Branch | 'new' | null>(null);
  return (
    <>
      <PageHeader title="Branches" description="The IFSC is generated from the branch code and never changes."
        action={<Button onClick={() => setEditing('new')}><Plus className="size-4" aria-hidden="true" /> Add branch</Button>} />
      <Card>
        {branches.isPending ? <LoadingBlock /> : !branches.data?.length ? <EmptyState title="No branches" /> : (
          <ul>
            {branches.data.map((b) => (
              <li key={b.id} className="ledger-row flex flex-wrap items-center gap-4 px-5 py-4">
                <div className="min-w-0 flex-1">
                  <p className="font-medium">{b.name} {!b.active && <Tag>Inactive</Tag>}</p>
                  <p className="text-sm text-ink-soft">{b.addressLine}, {b.city}, {b.state} {b.pincode}</p>
                </div>
                <Code className="text-ink-soft">{b.ifsc}</Code>
                <Button size="sm" variant="ghost" onClick={() => setEditing(b)}>Edit</Button>
              </li>
            ))}
          </ul>
        )}
      </Card>
      <BranchDialog branch={editing} onClose={() => setEditing(null)} />
    </>
  );
}

function BranchDialog({ branch, onClose }: { branch: Branch | 'new' | null; onClose: () => void }) {
  const save = useSaveBranch();
  const isNew = branch === 'new';
  const { register, handleSubmit, reset, setError, formState: { errors } } = useForm<BranchForm>({ resolver: zodResolver(schema) });

  useEffect(() => {
    if (branch === 'new') reset({ code: '', name: '', addressLine: '', city: '', state: '', pincode: '', phone: '', active: true });
    else if (branch) reset({ ...branch, phone: branch.phone ?? '' });
  }, [branch, reset]);

  const onSubmit = handleSubmit(async (values) => {
    try {
      const saved = await save.mutateAsync({ ...values, phone: values.phone || undefined, id: isNew ? undefined : (branch as Branch).id });
      toast.success(isNew ? `Branch created with IFSC ${saved.ifsc}` : 'Branch updated');
      onClose();
    } catch (e) {
      if (!applyFieldErrors(e, setError)) toast.error(errorMessage(e));
    }
  });

  return (
    <Dialog open={branch !== null} onClose={onClose} title={isNew ? 'Add branch' : 'Edit branch'}>
      <form onSubmit={onSubmit} className="grid gap-4 sm:grid-cols-2" noValidate>
        <Input label="Branch code" inputMode="numeric" disabled={!isNew} error={errors.code?.message} {...register('code')} />
        <Input label="Name" error={errors.name?.message} {...register('name')} />
        <Input label="Address" error={errors.addressLine?.message} {...register('addressLine')} className="sm:col-span-2" />
        <Input label="City" error={errors.city?.message} {...register('city')} />
        <Input label="State" error={errors.state?.message} {...register('state')} />
        <Input label="PIN code" inputMode="numeric" error={errors.pincode?.message} {...register('pincode')} />
        <Input label="Phone" inputMode="numeric" error={errors.phone?.message} {...register('phone')} />
        <label className="flex items-center gap-3 text-sm sm:col-span-2">
          <input type="checkbox" className="size-4 accent-kosh-800" {...register('active')} />
          Open: customers can choose it and staff can be assigned to it
        </label>
        <div className="flex justify-end gap-2 sm:col-span-2">
          <Button variant="ghost" onClick={onClose}>Cancel</Button>
          <Button type="submit" loading={save.isPending}>{isNew ? 'Create branch' : 'Save changes'}</Button>
        </div>
      </form>
    </Dialog>
  );
}
