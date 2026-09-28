import { zodResolver } from '@hookform/resolvers/zod';
import { CheckCircle2 } from 'lucide-react';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { Link } from 'react-router-dom';
import { z } from 'zod';
import { usePublicBranches, useRegister } from '@/api/common';
import { Button } from '@/components/ui/Button';
import { Input, Select } from '@/components/ui/Field';
import { Code } from '@/components/ui/Money';
import { applyFieldErrors, errorMessage } from '@/lib/errors';
import { customerIdentitySchema } from '@/lib/schemas';
import type { RegistrationResponse } from '@/types/api';
import { AuthLayout } from './AuthLayout';

const schema = customerIdentitySchema.extend({
  password: z.string().regex(/^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[^A-Za-z0-9]).{8,64}$/,
    'Use 8+ characters with upper and lower case letters, a digit and a symbol'),
  branchCode: z.string().min(1, 'Choose your home branch'),
});
type RegisterForm = z.infer<typeof schema>;

export default function RegisterPage() {
  const branches = usePublicBranches();
  const registration = useRegister();
  const [done, setDone] = useState<RegistrationResponse | null>(null);
  const [error, setError] = useState<string | null>(null);
  const { register, handleSubmit, setError: setFieldError, formState: { errors } } = useForm<RegisterForm>({
    resolver: zodResolver(schema),
  });

  const onSubmit = handleSubmit(async (values) => {
    setError(null);
    try {
      setDone(await registration.mutateAsync({ ...values, panNumber: values.panNumber.toUpperCase() }));
    } catch (e) {
      if (!applyFieldErrors(e, setFieldError)) setError(errorMessage(e));
    }
  });

  if (done) {
    return (
      <AuthLayout>
        <CheckCircle2 className="size-10 text-credit" aria-hidden="true" />
        <h2 className="mt-4 text-2xl font-semibold">Your account is open</h2>
        <p className="mt-2 text-ink-soft">{done.message}</p>
        <dl className="mt-6 divide-y divide-rule rounded-lg border border-rule bg-surface">
          <div className="flex justify-between px-4 py-3"><dt className="text-ink-soft">Customer ID</dt><dd><Code>{done.customerNumber}</Code></dd></div>
          <div className="flex justify-between px-4 py-3"><dt className="text-ink-soft">Savings account</dt><dd><Code group>{done.accountNumber}</Code></dd></div>
        </dl>
        <Link to="/login" className="mt-6 inline-flex h-12 w-full items-center justify-center rounded-lg bg-kosh-800 font-medium text-paper hover:bg-kosh-700">
          Sign in
        </Link>
      </AuthLayout>
    );
  }

  return (
    <AuthLayout wide>
      <h2 className="text-2xl font-semibold text-ink">Open a savings account</h2>
      <p className="mt-1 text-ink-soft">
        Takes about two minutes. Bring your PAN and Aadhaar to the branch to complete KYC; until then the account cannot send or receive money.
      </p>

      <form onSubmit={onSubmit} className="mt-8 grid gap-4 sm:grid-cols-2" noValidate>
        <Input label="Full name (as on PAN)" autoComplete="name" error={errors.fullName?.message} {...register('fullName')} className="sm:col-span-2" />
        <Input label="Email" type="email" autoComplete="email" error={errors.email?.message} {...register('email')} />
        <Input label="Mobile number" inputMode="numeric" autoComplete="tel-national" placeholder="98XXXXXXXX" error={errors.phone?.message} {...register('phone')} />
        <Input label="Date of birth" type="date" error={errors.dateOfBirth?.message} {...register('dateOfBirth')} />
        <Select label="Home branch" error={errors.branchCode?.message} defaultValue="" {...register('branchCode')}>
          <option value="" disabled>Choose a branch</option>
          {branches.data?.map((b) => (
            <option key={b.code} value={b.code}>{b.name}, {b.city}</option>
          ))}
        </Select>
        <Input label="PAN" placeholder="ABCPE1234F" autoCapitalize="characters" error={errors.panNumber?.message} {...register('panNumber')} />
        <Input label="Aadhaar number" inputMode="numeric" placeholder="12 digits" error={errors.aadhaarNumber?.message} {...register('aadhaarNumber')} />
        <Input label="Address" autoComplete="street-address" error={errors.addressLine?.message} {...register('addressLine')} className="sm:col-span-2" />
        <Input label="City" autoComplete="address-level2" error={errors.city?.message} {...register('city')} />
        <Input label="State" autoComplete="address-level1" error={errors.state?.message} {...register('state')} />
        <Input label="PIN code" inputMode="numeric" autoComplete="postal-code" error={errors.pincode?.message} {...register('pincode')} />
        <Input label="Password" type="password" autoComplete="new-password" error={errors.password?.message} {...register('password')} />
        {error && <p className="rounded-lg bg-debit-soft px-3 py-2.5 text-sm text-debit sm:col-span-2" role="alert">{error}</p>}
        <div className="flex flex-col-reverse items-center justify-between gap-4 sm:col-span-2 sm:flex-row">
          <Link to="/login" className="text-sm font-medium text-kosh-600 hover:underline">I already have an account</Link>
          <Button type="submit" size="lg" loading={registration.isPending} className="w-full sm:w-auto">Open account</Button>
        </div>
      </form>
    </AuthLayout>
  );
}
