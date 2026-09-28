import type { ReactNode } from 'react';
import { Navigate, useLocation } from 'react-router-dom';
import { WakeNotice } from '@/components/WakeNotice';
import { FullPageSpinner } from '@/components/ui/Spinner';
import type { Role } from '@/types/api';
import { HOME_BY_ROLE, useAuth } from './useAuth';

function RestoringSession() {
  return (
    <FullPageSpinner label="Opening your session">
      <WakeNotice className="mt-6 max-w-sm" />
    </FullPageSpinner>
  );
}

/**
 * Client-side route guard. This is a UX convenience, not security: the API enforces every rule
 * again with @PreAuthorize, so a user who edits the bundle still gets 403s.
 */
export function RequireRole({ roles, children }: { roles: Role[]; children: ReactNode }) {
  const { status, user } = useAuth();
  const location = useLocation();

  if (status === 'restoring') return <RestoringSession />;
  if (!user) return <Navigate to="/login" replace state={{ from: location.pathname }} />;
  if (!roles.includes(user.role)) return <Navigate to={HOME_BY_ROLE[user.role]} replace />;
  return <>{children}</>;
}

export function RedirectHome() {
  const { status, user } = useAuth();
  if (status === 'restoring') return <RestoringSession />;
  return <Navigate to={user ? HOME_BY_ROLE[user.role] : '/login'} replace />;
}
