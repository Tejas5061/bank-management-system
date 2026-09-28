import { zodResolver } from '@hookform/resolvers/zod';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { Link, useNavigate } from 'react-router-dom';
import { toast } from 'sonner';
import { z } from 'zod';
import { useForgotPassword, useResetPassword } from '@/api/common';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Field';
import { errorMessage } from '@/lib/errors';
import { STRONG_PASSWORD } from '@/lib/schemas';
import { AuthLayout } from './AuthLayout';

const emailSchema = z.object({ email: z.email('Enter a valid email address') });
const resetSchema = z.object({
  otp: z.string().regex(/^\d{6}$/, 'Enter the 6-digit code from the email'),
  newPassword: z.string().regex(STRONG_PASSWORD, 'Use 8+ characters with upper and lower case letters, a digit and a symbol'),
});

/** Two steps on one screen: request a code, then set a new password with it. */
export default function ForgotPasswordPage() {
  const [email, setEmail] = useState<string | null>(null);
  const forgot = useForgotPassword();
  const reset = useResetPassword();
  const navigate = useNavigate();
  const [error, setError] = useState<string | null>(null);

  const requestForm = useForm<z.infer<typeof emailSchema>>({ resolver: zodResolver(emailSchema) });
  const resetForm = useForm<z.infer<typeof resetSchema>>({ resolver: zodResolver(resetSchema) });

  const requestCode = requestForm.handleSubmit(async (values) => {
    setError(null);
    try {
      await forgot.mutateAsync(values.email);
      setEmail(values.email);
    } catch (e) {
      setError(errorMessage(e));
    }
  });

  const setPassword = resetForm.handleSubmit(async (values) => {
    setError(null);
    try {
      await reset.mutateAsync({ email: email!, ...values });
      toast.success('Password updated. Sign in with your new password.');
      navigate('/login', { replace: true });
    } catch (e) {
      setError(errorMessage(e));
    }
  });

  return (
    <AuthLayout>
      <h2 className="text-2xl font-semibold text-ink">Reset your password</h2>
      {!email ? (
        <>
          <p className="mt-1 text-ink-soft">We will email you a 6-digit code. It works once and expires in 10 minutes.</p>
          <form onSubmit={requestCode} className="mt-8 flex flex-col gap-4" noValidate>
            <Input label="Email" type="email" autoComplete="username" error={requestForm.formState.errors.email?.message} {...requestForm.register('email')} />
            {error && <p className="rounded-lg bg-debit-soft px-3 py-2.5 text-sm text-debit" role="alert">{error}</p>}
            <Button type="submit" size="lg" loading={forgot.isPending}>Email me a code</Button>
          </form>
        </>
      ) : (
        <>
          <p className="mt-1 text-ink-soft">
            If <span className="font-medium text-ink">{email}</span> has an account, the code is on its way. Setting a new password also unlocks the account and signs out every session.
          </p>
          <form onSubmit={setPassword} className="mt-8 flex flex-col gap-4" noValidate>
            <Input label="6-digit code" inputMode="numeric" autoComplete="one-time-code" maxLength={6} error={resetForm.formState.errors.otp?.message} {...resetForm.register('otp')} />
            <Input label="New password" type="password" autoComplete="new-password" error={resetForm.formState.errors.newPassword?.message} {...resetForm.register('newPassword')} />
            {error && <p className="rounded-lg bg-debit-soft px-3 py-2.5 text-sm text-debit" role="alert">{error}</p>}
            <Button type="submit" size="lg" loading={reset.isPending}>Set new password</Button>
            <button type="button" className="text-sm font-medium text-kosh-600 hover:underline" onClick={() => setEmail(null)}>
              Use a different email
            </button>
          </form>
        </>
      )}
      <Link to="/login" className="mt-6 block text-sm font-medium text-kosh-600 hover:underline">Back to sign in</Link>
    </AuthLayout>
  );
}
