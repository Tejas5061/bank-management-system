import { zodResolver } from '@hookform/resolvers/zod';
import { Plus } from 'lucide-react';
import { useEffect, useState } from 'react';
import { useForm } from 'react-hook-form';
import { toast } from 'sonner';
import { z } from 'zod';
import { useBranches, useCreateEmployee, useEmployees, useUnlockUser, useUpdateEmployee } from '@/api/admin';
import { Button } from '@/components/ui/Button';
import { Card, EmptyState, PageHeader } from '@/components/ui/Card';
import { Dialog } from '@/components/ui/Dialog';
import { Input, Select } from '@/components/ui/Field';
import { Code } from '@/components/ui/Money';
import { Pagination } from '@/components/ui/Pagination';
import { LoadingBlock } from '@/components/ui/Spinner';
import { Tag } from '@/components/ui/Status';
import { applyFieldErrors, errorMessage } from '@/lib/errors';
import { formatDateTime } from '@/lib/format';
import { PHONE } from '@/lib/schemas';
import { useDebounced } from '@/lib/useDebounced';
import type { Employee } from '@/types/api';

const createSchema = z.object({
  fullName: z.string().trim().min(3, 'Enter the full name'),
  email: z.email('Enter a valid email address'),
  phone: z.string().regex(PHONE, 'Enter a 10-digit mobile number'),
  branchCode: z.string().min(1, 'Choose a branch'),
  designation: z.string().trim().min(2, 'Enter a designation'),
});
type CreateForm = z.infer<typeof createSchema>;

export default function EmployeesPage() {
  const [query, setQuery] = useState('');
  const [page, setPage] = useState(0);
  const employees = useEmployees(useDebounced(query.trim(), 300), page);
  const [creating, setCreating] = useState(false);
  const [editing, setEditing] = useState<Employee | null>(null);
  const unlock = useUnlockUser();

  return (
    <>
      <PageHeader title="Employees" description="Tellers and loan officers. New employees get a temporary password by email."
        action={<Button onClick={() => setCreating(true)}><Plus className="size-4" aria-hidden="true" /> Add employee</Button>} />
      <Card>
        <div className="border-b border-rule px-5 py-4">
          <Input label="Search" placeholder="Name, email or employee code" value={query} onChange={(e) => { setQuery(e.target.value); setPage(0); }} className="max-w-sm" />
        </div>
        {employees.isPending ? <LoadingBlock /> : !employees.data?.content.length ? <EmptyState title="No employees found" /> : (
          <>
            <div className="overflow-x-auto">
              <table className="w-full min-w-[720px] text-sm">
                <thead>
                  <tr className="border-b border-rule text-left text-xs uppercase tracking-[0.08em] text-ink-faint">
                    <th scope="col" className="py-2.5 pl-5 pr-3 font-medium">Employee</th>
                    <th scope="col" className="px-3 py-2.5 font-medium">Branch</th>
                    <th scope="col" className="px-3 py-2.5 font-medium">Last sign-in</th>
                    <th scope="col" className="px-3 py-2.5 font-medium">Status</th>
                    <th scope="col" className="py-2.5 pl-3 pr-5"><span className="sr-only">Actions</span></th>
                  </tr>
                </thead>
                <tbody>
                  {employees.data.content.map((e) => (
                    <tr key={e.id} className="ledger-row">
                      <td className="py-3 pl-5 pr-3">
                        <p className="font-medium">{e.fullName}</p>
                        <p className="text-xs text-ink-faint"><Code>{e.employeeCode}</Code> · {e.designation} · {e.email}</p>
                      </td>
                      <td className="px-3 py-3">{e.branchName} <Code className="text-ink-faint">{e.branchCode}</Code></td>
                      <td className="px-3 py-3 text-ink-soft">{e.lastLoginAt ? formatDateTime(e.lastLoginAt) : 'Never'}</td>
                      <td className="px-3 py-3">
                        <div className="flex flex-wrap gap-1.5">
                          {e.enabled ? <Tag tone="credit">Active</Tag> : <Tag>Disabled</Tag>}
                          {e.locked && <Tag tone="debit">Locked out</Tag>}
                        </div>
                      </td>
                      <td className="py-3 pl-3 pr-5 text-right">
                        <div className="flex justify-end gap-1">
                          {e.locked && (
                            <Button size="sm" variant="ghost" loading={unlock.isPending} onClick={() => unlock.mutate(e.userId, {
                              onSuccess: () => toast.success(`${e.fullName} unlocked`),
                              onError: (err) => toast.error(errorMessage(err)),
                            })}>Unlock</Button>
                          )}
                          <Button size="sm" variant="ghost" onClick={() => setEditing(e)}>Edit</Button>
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <Pagination page={employees.data} onChange={setPage} />
          </>
        )}
      </Card>
      <CreateEmployeeDialog open={creating} onClose={() => setCreating(false)} />
      <EditEmployeeDialog employee={editing} onClose={() => setEditing(null)} />
    </>
  );
}

function BranchOptions() {
  const branches = useBranches();
  return <>{branches.data?.filter((b) => b.active).map((b) => <option key={b.code} value={b.code}>{b.name} ({b.code})</option>)}</>;
}

function CreateEmployeeDialog({ open, onClose }: { open: boolean; onClose: () => void }) {
  const create = useCreateEmployee();
  const { register, handleSubmit, reset, setError, formState: { errors } } = useForm<CreateForm>({ resolver: zodResolver(createSchema), defaultValues: { branchCode: '' } });
  const close = () => { reset(); onClose(); };
  const onSubmit = handleSubmit(async (values) => {
    try {
      const employee = await create.mutateAsync(values);
      toast.success(`${employee.fullName} added as ${employee.employeeCode}. Sign-in details were emailed.`);
      close();
    } catch (e) {
      if (!applyFieldErrors(e, setError)) toast.error(errorMessage(e));
    }
  });
  return (
    <Dialog open={open} onClose={close} title="Add employee">
      <form onSubmit={onSubmit} className="grid gap-4 sm:grid-cols-2" noValidate>
        <Input label="Full name" error={errors.fullName?.message} {...register('fullName')} className="sm:col-span-2" />
        <Input label="Work email" type="email" error={errors.email?.message} {...register('email')} className="sm:col-span-2" />
        <Input label="Mobile number" inputMode="numeric" error={errors.phone?.message} {...register('phone')} />
        <Input label="Designation" placeholder="Teller" error={errors.designation?.message} {...register('designation')} />
        <Select label="Branch" error={errors.branchCode?.message} {...register('branchCode')} className="sm:col-span-2">
          <option value="" disabled>Choose a branch</option>
          <BranchOptions />
        </Select>
        <div className="flex justify-end gap-2 sm:col-span-2">
          <Button variant="ghost" onClick={close}>Cancel</Button>
          <Button type="submit" loading={create.isPending}>Add employee</Button>
        </div>
      </form>
    </Dialog>
  );
}

const editSchema = z.object({
  phone: z.string().regex(PHONE, 'Enter a 10-digit mobile number'),
  branchCode: z.string().min(1),
  designation: z.string().trim().min(2, 'Enter a designation'),
  enabled: z.boolean(),
});
type EditForm = z.infer<typeof editSchema>;

function EditEmployeeDialog({ employee, onClose }: { employee: Employee | null; onClose: () => void }) {
  const update = useUpdateEmployee();
  const { register, handleSubmit, reset, formState: { errors } } = useForm<EditForm>({ resolver: zodResolver(editSchema) });
  useEffect(() => {
    if (employee) reset({ phone: employee.phone, branchCode: employee.branchCode, designation: employee.designation, enabled: employee.enabled });
  }, [employee, reset]);

  const onSubmit = handleSubmit(async (values) => {
    if (!employee) return;
    try {
      await update.mutateAsync({ id: employee.id, ...values });
      toast.success('Employee updated');
      onClose();
    } catch (e) {
      toast.error(errorMessage(e));
    }
  });

  return (
    <Dialog open={employee !== null} onClose={onClose} title={employee ? `Edit ${employee.fullName}` : 'Edit employee'}>
      <form onSubmit={onSubmit} className="flex flex-col gap-4" noValidate>
        <Input label="Mobile number" inputMode="numeric" error={errors.phone?.message} {...register('phone')} />
        <Input label="Designation" error={errors.designation?.message} {...register('designation')} />
        <Select label="Branch" {...register('branchCode')}><BranchOptions /></Select>
        <label className="flex items-center gap-3 text-sm">
          <input type="checkbox" className="size-4 accent-kosh-800" {...register('enabled')} />
          Can sign in (turning this off also signs them out everywhere)
        </label>
        <div className="flex justify-end gap-2">
          <Button variant="ghost" onClick={onClose}>Cancel</Button>
          <Button type="submit" loading={update.isPending}>Save changes</Button>
        </div>
      </form>
    </Dialog>
  );
}
