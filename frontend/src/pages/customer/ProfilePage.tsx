import { zodResolver } from '@hookform/resolvers/zod';
import { useEffect } from 'react';
import { useForm } from 'react-hook-form';
import { toast } from 'sonner';
import { z } from 'zod';
import { useProfile, useUpdateProfile } from '@/api/customer';
import { ChangePasswordCard } from '@/pages/shared/SecurityPage';
import { Button } from '@/components/ui/Button';
import { Card, CardHeader, EmptyState, PageHeader } from '@/components/ui/Card';
import { Input } from '@/components/ui/Field';
import { Code } from '@/components/ui/Money';
import { LoadingBlock } from '@/components/ui/Spinner';
import { Stamp } from '@/components/ui/Status';
import { applyFieldErrors, errorMessage } from '@/lib/errors';
import { formatDate } from '@/lib/format';
import { PHONE, PINCODE } from '@/lib/schemas';

const schema = z.object({
  phone: z.string().regex(PHONE, 'Enter a 10-digit mobile number'),
  addressLine: z.string().trim().min(3, 'Enter your address'),
  city: z.string().trim().min(2, 'Enter your city'),
  state: z.string().trim().min(2, 'Enter your state'),
  pincode: z.string().regex(PINCODE, 'Enter a 6-digit PIN code'),
});
type ContactForm = z.infer<typeof schema>;

export default function ProfilePage() {
  const profile = useProfile();
  const update = useUpdateProfile();
  const { register, handleSubmit, reset, setError, formState: { errors, isDirty } } = useForm<ContactForm>({ resolver: zodResolver(schema) });

  useEffect(() => {
    if (profile.data) {
      const { phone, addressLine, city, state, pincode } = profile.data;
      reset({ phone, addressLine, city, state, pincode });
    }
  }, [profile.data, reset]);

  if (profile.isPending) return <LoadingBlock />;
  if (profile.isError) return <EmptyState title="Could not load your profile" description={errorMessage(profile.error)} />;
  const p = profile.data;

  const onSubmit = handleSubmit(async (values) => {
    try {
      await update.mutateAsync(values);
      toast.success('Contact details saved');
    } catch (e) {
      if (!applyFieldErrors(e, setError)) toast.error(errorMessage(e));
    }
  });

  return (
    <>
      <PageHeader title="Profile & security" />
      <div className="grid gap-6 lg:grid-cols-2">
        <Card>
          <CardHeader title="Identity" description="Verified at the branch. To correct these, visit your home branch." action={<Stamp status={p.kycStatus} label={`KYC ${p.kycStatus.toLowerCase()}`} />} />
          <dl className="grid grid-cols-[auto_1fr] gap-x-6 gap-y-3 px-5 py-5 text-sm">
            <dt className="text-ink-soft">Name</dt><dd className="font-medium">{p.fullName}</dd>
            <dt className="text-ink-soft">Customer ID</dt><dd><Code>{p.customerNumber}</Code></dd>
            <dt className="text-ink-soft">Email</dt><dd>{p.email}</dd>
            <dt className="text-ink-soft">Date of birth</dt><dd>{formatDate(p.dateOfBirth)}</dd>
            <dt className="text-ink-soft">PAN</dt><dd><Code>{p.maskedPan}</Code></dd>
            <dt className="text-ink-soft">Aadhaar</dt><dd><Code>{p.maskedAadhaar}</Code></dd>
            <dt className="text-ink-soft">Home branch</dt><dd>{p.homeBranch.name}, {p.homeBranch.city} · <Code>{p.homeBranch.ifsc}</Code></dd>
            {p.kycRemarks && <><dt className="text-ink-soft">KYC note</dt><dd>{p.kycRemarks}</dd></>}
          </dl>
        </Card>

        <Card>
          <CardHeader title="Contact details" />
          <form onSubmit={onSubmit} className="grid gap-4 px-5 py-5 sm:grid-cols-2" noValidate>
            <Input label="Mobile number" inputMode="numeric" error={errors.phone?.message} {...register('phone')} />
            <Input label="PIN code" inputMode="numeric" error={errors.pincode?.message} {...register('pincode')} />
            <Input label="Address" error={errors.addressLine?.message} {...register('addressLine')} className="sm:col-span-2" />
            <Input label="City" error={errors.city?.message} {...register('city')} />
            <Input label="State" error={errors.state?.message} {...register('state')} />
            <div className="sm:col-span-2">
              <Button type="submit" loading={update.isPending} disabled={!isDirty}>Save contact details</Button>
            </div>
          </form>
        </Card>

        <ChangePasswordCard />
      </div>
    </>
  );
}
