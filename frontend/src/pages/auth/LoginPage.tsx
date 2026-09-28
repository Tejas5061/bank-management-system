import { zodResolver } from '@hookform/resolvers/zod';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { Link, Navigate, useLocation, useNavigate } from 'react-router-dom';
import { z } from 'zod';
import { HOME_BY_ROLE, useAuth } from '@/auth/useAuth';
import { WakeNotice } from '@/components/WakeNotice';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Field';
import { errorMessage } from '@/lib/errors';
import { AuthLayout } from './AuthLayout';

const schema = z.object({
  email: z.email('Enter a valid email address'),
  password: z.string().min(1, 'Enter your password'),
});
type LoginForm = z.infer<typeof schema>;

const DEMO_USERS = [
  { label: 'Customer', who: 'Ananya Sharma', email: 'ananya.sharma@example.com', password: 'Customer@123' },
  { label: 'Teller', who: 'Priya Nair', email: 'priya.nair@koshbank.test', password: 'Employee@123' },
  { label: 'Admin', who: 'Aditi Rao', email: 'admin@koshbank.test', password: 'Admin@123' },
];
const SHOW_DEMO = import.meta.env.DEV || import.meta.env.VITE_DEMO_MODE === 'true';

export function LoginPage() {
  const { status, user, signIn } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [error, setError] = useState<string | null>(null);
  const { register, handleSubmit, setValue, formState } = useForm<LoginForm>({ resolver: zodResolver(schema) });

  if (status === 'signed-in' && user) return <Navigate to={HOME_BY_ROLE[user.role]} replace />;

  const onSubmit = handleSubmit(async ({ email, password }) => {
    setError(null);
    try {
      const signedIn = await signIn(email, password);
      const from = (location.state as { from?: string } | null)?.from;
      navigate(from && from.startsWith(HOME_BY_ROLE[signedIn.role]) ? from : HOME_BY_ROLE[signedIn.role], { replace: true });
    } catch (e) {
      setError(errorMessage(e));
    }
  });

  return (
    <AuthLayout>
      <h2 className="text-2xl font-semibold text-ink">Sign in</h2>
      <p className="mt-1 text-ink-soft">Use the email you registered with.</p>

      <WakeNotice className="mt-6" />

      <form onSubmit={onSubmit} className="mt-8 flex flex-col gap-4" noValidate>
        <Input label="Email" type="email" autoComplete="username" error={formState.errors.email?.message} {...register('email')} />
        <Input
          label="Password"
          type="password"
          autoComplete="current-password"
          error={formState.errors.password?.message}
          {...register('password')}
        />
        {error && <p className="rounded-lg bg-debit-soft px-3 py-2.5 text-sm text-debit" role="alert">{error}</p>}
        <Button type="submit" size="lg" loading={formState.isSubmitting}>Sign in</Button>
      </form>

      <div className="mt-5 flex justify-between text-sm">
        <Link to="/forgot-password" className="font-medium text-kosh-600 hover:underline">Forgot password?</Link>
        <Link to="/register" className="font-medium text-kosh-600 hover:underline">Open an account</Link>
      </div>

      {SHOW_DEMO && (
        <section className="mt-10 border-t border-rule pt-6" aria-labelledby="demo-heading">
          <h3 id="demo-heading" className="font-mono text-[0.7rem] uppercase tracking-[0.14em] text-ink-faint">Demo accounts</h3>
          <div className="mt-3 grid grid-cols-3 gap-2">
            {DEMO_USERS.map((demo) => (
              <button
                key={demo.label}
                type="button"
                onClick={() => {
                  setValue('email', demo.email, { shouldValidate: true });
                  setValue('password', demo.password, { shouldValidate: true });
                }}
                className="rounded-lg border border-rule bg-surface px-2.5 py-2 text-left transition-colors hover:border-kosh-500"
              >
                <span className="block text-sm font-medium text-ink">{demo.label}</span>
                <span className="block truncate text-xs text-ink-faint">{demo.who}</span>
              </button>
            ))}
          </div>
        </section>
      )}
    </AuthLayout>
  );
}
