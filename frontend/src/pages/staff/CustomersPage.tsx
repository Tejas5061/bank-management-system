import { zodResolver } from '@hookform/resolvers/zod';
import { Search, UserPlus } from 'lucide-react';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { toast } from 'sonner';
import { z } from 'zod';
import { usePublicBranches } from '@/api/common';
import { useCustomers, useOnboardCustomer } from '@/api/staff';
import { useAuth } from '@/auth/useAuth';
import { Button } from '@/components/ui/Button';
import { Card, EmptyState, PageHeader } from '@/components/ui/Card';
import { Dialog } from '@/components/ui/Dialog';
import { Input, Select } from '@/components/ui/Field';
import { Code } from '@/components/ui/Money';
import { Pagination } from '@/components/ui/Pagination';
import { LoadingBlock } from '@/components/ui/Spinner';
import { Stamp } from '@/components/ui/Status';
import { applyFieldErrors, errorMessage } from '@/lib/errors';
import { formatDate } from '@/lib/format';
import { useDebounced } from '@/lib/useDebounced';
import { customerIdentitySchema } from '@/lib/schemas';
import type { KycStatus } from '@/types/api';

export default function CustomersPage({ kycQueue = false }: { kycQueue?: boolean }) {
  const { user } = useAuth();
  const base = user?.role === 'ADMIN' ? '/admin' : '/staff';
  const [params, setParams] = useSearchParams();
  const [query, setQuery] = useState('');
  const [kyc, setKyc] = useState<KycStatus | ''>(kycQueue ? 'PENDING' : '');
  const [page, setPage] = useState(0);
  const debouncedQuery = useDebounced(query.trim(), 300);
  const customers = useCustomers(debouncedQuery, kycQueue ? 'PENDING' : kyc, page, kycQueue ? 'createdAt,asc' : 'createdAt,desc');
  const onboarding = params.get('onboard') === '1';
  const canOnboard = user?.role === 'EMPLOYEE';

  return (
    <>
      <PageHeader
        title={kycQueue ? 'KYC queue' : 'Customers'}
        description={kycQueue
          ? 'Customers waiting for identity verification, oldest first. Their accounts cannot transact until you verify them.'
          : 'Search by name, email, phone or customer ID.'}
        action={canOnboard && !kycQueue && (
          <Button onClick={() => setParams({ onboard: '1' })}><UserPlus className="size-4" aria-hidden="true" /> Onboard customer</Button>
        )}
      />
      <Card>
        <div className="flex flex-wrap items-end gap-3 border-b border-rule px-5 py-4">
          <div className="relative min-w-60 flex-1">
            <Search className="pointer-events-none absolute bottom-3.5 left-3 z-10 size-4 text-ink-faint" aria-hidden="true" />
            <Input label="Search" placeholder="Name, email, phone or CIF" value={query} onChange={(e) => { setQuery(e.target.value); setPage(0); }} className="[&_input]:pl-9" />
          </div>
          {!kycQueue && (
            <Select label="KYC status" value={kyc} onChange={(e) => { setKyc(e.target.value as KycStatus | ''); setPage(0); }} className="w-44">
              <option value="">Any</option>
              <option value="PENDING">Pending</option>
              <option value="VERIFIED">Verified</option>
              <option value="REJECTED">Rejected</option>
            </Select>
          )}
        </div>
        {customers.isPending ? <LoadingBlock /> : !customers.data?.content.length ? (
          <EmptyState title={kycQueue ? 'The KYC queue is empty' : 'No customers found'} description={kycQueue ? 'New registrations will appear here.' : 'Try a different search.'} />
        ) : (
          <>
            <div className="overflow-x-auto">
              <table className="w-full min-w-[640px] text-sm">
                <thead>
                  <tr className="border-b border-rule text-left text-xs uppercase tracking-[0.08em] text-ink-faint">
                    <th scope="col" className="py-2.5 pl-5 pr-3 font-medium">Customer</th>
                    <th scope="col" className="px-3 py-2.5 font-medium">Contact</th>
                    <th scope="col" className="px-3 py-2.5 font-medium">Branch</th>
                    <th scope="col" className="px-3 py-2.5 font-medium">Since</th>
                    <th scope="col" className="py-2.5 pl-3 pr-5 font-medium">KYC</th>
                  </tr>
                </thead>
                <tbody>
                  {customers.data.content.map((c) => (
                    <tr key={c.id} className="ledger-row hover:bg-kosh-50/60">
                      <td className="py-3 pl-5 pr-3">
                        <Link to={`${base}/customers/${c.id}`} className="font-medium text-ink hover:text-kosh-600 hover:underline">{c.fullName}</Link>
                        <p className="text-xs text-ink-faint"><Code>{c.customerNumber}</Code></p>
                      </td>
                      <td className="px-3 py-3 text-ink-soft">{c.email}<p className="text-xs text-ink-faint">{c.phone}</p></td>
                      <td className="px-3 py-3"><Code>{c.homeBranchCode}</Code></td>
                      <td className="px-3 py-3 text-ink-soft">{formatDate(c.createdAt)}</td>
                      <td className="py-3 pl-3 pr-5"><Stamp status={c.kycStatus} /></td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <Pagination page={customers.data} onChange={setPage} />
          </>
        )}
      </Card>
      {canOnboard && <OnboardDialog open={onboarding} onClose={() => setParams({})} />}
    </>
  );
}

const onboardSchema = customerIdentitySchema.extend({
  branchCode: z.string().optional(),
  accountType: z.enum(['SAVINGS', 'CURRENT']),
});
type OnboardForm = z.infer<typeof onboardSchema>;

function OnboardDialog({ open, onClose }: { open: boolean; onClose: () => void }) {
  const branches = usePublicBranches();
  const onboard = useOnboardCustomer();
  const navigate = useNavigate();
  const [error, setError] = useState<string | null>(null);
  const { register, handleSubmit, reset, setError: setFieldError, formState: { errors } } = useForm<OnboardForm>({
    resolver: zodResolver(onboardSchema),
    defaultValues: { accountType: 'SAVINGS', branchCode: '' },
  });

  const close = () => { reset(); setError(null); onClose(); };

  const onSubmit = handleSubmit(async (values) => {
    setError(null);
    try {
      const result = await onboard.mutateAsync({ ...values, branchCode: values.branchCode || undefined });
      toast.success(`${values.fullName} onboarded as ${result.customerNumber}. Login details were emailed to them.`);
      close();
      navigate('/staff/kyc');
    } catch (e) {
      if (!applyFieldErrors(e, setFieldError)) setError(errorMessage(e));
    }
  });

  return (
    <Dialog open={open} onClose={close} wide title="Onboard a customer" description="Opens the first account and emails the customer a temporary password. Verify KYC once you have seen the originals.">
      <form onSubmit={onSubmit} className="grid gap-4 sm:grid-cols-2" noValidate>
        <Input label="Full name (as on PAN)" error={errors.fullName?.message} {...register('fullName')} className="sm:col-span-2" />
        <Input label="Email" type="email" error={errors.email?.message} {...register('email')} />
        <Input label="Mobile number" inputMode="numeric" error={errors.phone?.message} {...register('phone')} />
        <Input label="Date of birth" type="date" error={errors.dateOfBirth?.message} {...register('dateOfBirth')} />
        <Input label="PAN" autoCapitalize="characters" error={errors.panNumber?.message} {...register('panNumber')} />
        <Input label="Aadhaar number" inputMode="numeric" error={errors.aadhaarNumber?.message} {...register('aadhaarNumber')} />
        <Select label="First account" error={errors.accountType?.message} {...register('accountType')}>
          <option value="SAVINGS">Savings</option>
          <option value="CURRENT">Current</option>
        </Select>
        <Input label="Address" error={errors.addressLine?.message} {...register('addressLine')} className="sm:col-span-2" />
        <Input label="City" error={errors.city?.message} {...register('city')} />
        <Input label="State" error={errors.state?.message} {...register('state')} />
        <Input label="PIN code" inputMode="numeric" error={errors.pincode?.message} {...register('pincode')} />
        <Select label="Home branch" {...register('branchCode')}>
          <option value="">My branch</option>
          {branches.data?.map((b) => <option key={b.code} value={b.code}>{b.name}, {b.city}</option>)}
        </Select>
        {error && <p className="rounded-lg bg-debit-soft px-3 py-2.5 text-sm text-debit sm:col-span-2" role="alert">{error}</p>}
        <div className="flex justify-end gap-2 sm:col-span-2">
          <Button variant="ghost" onClick={close}>Cancel</Button>
          <Button type="submit" loading={onboard.isPending}>Onboard customer</Button>
        </div>
      </form>
    </Dialog>
  );
}
