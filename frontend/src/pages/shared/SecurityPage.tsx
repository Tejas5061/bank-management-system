import { zodResolver } from '@hookform/resolvers/zod';
import { useForm } from 'react-hook-form';
import { useNavigate } from 'react-router-dom';
import { toast } from 'sonner';
import { z } from 'zod';
import { useChangePassword } from '@/api/common';
import { useAuth } from '@/auth/useAuth';
import { Button } from '@/components/ui/Button';
import { Card, CardHeader, PageHeader } from '@/components/ui/Card';
import { Input } from '@/components/ui/Field';
import { applyFieldErrors, errorMessage } from '@/lib/errors';
import { STRONG_PASSWORD } from '@/lib/schemas';

const schema = z.object({
  currentPassword: z.string().min(1, 'Enter your current password'),
  newPassword: z.string().regex(STRONG_PASSWORD, 'Use 8+ characters with upper and lower case letters, a digit and a symbol'),
  confirmPassword: z.string(),
}).refine((v) => v.newPassword === v.confirmPassword, { path: ['confirmPassword'], message: 'The passwords do not match' });
type PasswordForm = z.infer<typeof schema>;

export function ChangePasswordCard() {
  const change = useChangePassword();
  const { signOut } = useAuth();
  const navigate = useNavigate();
  const { register, handleSubmit, setError, formState: { errors } } = useForm<PasswordForm>({ resolver: zodResolver(schema) });

  const onSubmit = handleSubmit(async ({ currentPassword, newPassword }) => {
    try {
      await change.mutateAsync({ currentPassword, newPassword });
      toast.success('Password changed. Sign in again with the new password.');
      await signOut();
      navigate('/login', { replace: true });
    } catch (e) {
      if (!applyFieldErrors(e, setError)) toast.error(errorMessage(e));
    }
  });

  return (
    <Card>
      <CardHeader title="Change password" description="Changing it signs you out on every device." />
      <form onSubmit={onSubmit} className="flex flex-col gap-4 px-5 py-5" noValidate>
        <Input label="Current password" type="password" autoComplete="current-password" error={errors.currentPassword?.message} {...register('currentPassword')} />
        <Input label="New password" type="password" autoComplete="new-password" error={errors.newPassword?.message} {...register('newPassword')} />
        <Input label="Confirm new password" type="password" autoComplete="new-password" error={errors.confirmPassword?.message} {...register('confirmPassword')} />
        <div><Button type="submit" loading={change.isPending}>Change password</Button></div>
      </form>
    </Card>
  );
}

export default function SecurityPage() {
  return (
    <>
      <PageHeader title="Security" description="Staff accounts are created with a temporary password. Replace it with your own here." />
      <div className="max-w-xl"><ChangePasswordCard /></div>
    </>
  );
}
