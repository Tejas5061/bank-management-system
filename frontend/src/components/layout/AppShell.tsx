import {
  ArrowLeftRight,
  Banknote,
  Bell,
  Building2,
  ClipboardCheck,
  FileClock,
  HandCoins,
  Landmark,
  LayoutGrid,
  LogOut,
  Menu,
  Percent,
  PiggyBank,
  ShieldCheck,
  UserRound,
  Users,
  Wallet,
  X,
  type LucideIcon,
} from 'lucide-react';
import { useState } from 'react';
import { Link, NavLink, Outlet, useNavigate } from 'react-router-dom';
import { useUnreadCount } from '@/api/common';
import { useAuth } from '@/auth/useAuth';
import { cn } from '@/lib/util';
import type { Role } from '@/types/api';

interface NavItem {
  to: string;
  label: string;
  icon: LucideIcon;
  end?: boolean;
}

const PORTALS: Record<Role, { name: string; base: string; nav: NavItem[] }> = {
  CUSTOMER: {
    name: 'Personal banking',
    base: '/app',
    nav: [
      { to: '/app', label: 'Overview', icon: LayoutGrid, end: true },
      { to: '/app/accounts', label: 'Accounts', icon: Wallet },
      { to: '/app/transfer', label: 'Transfer', icon: ArrowLeftRight },
      { to: '/app/beneficiaries', label: 'Payees', icon: Users },
      { to: '/app/deposits', label: 'Fixed deposits', icon: PiggyBank },
      { to: '/app/loans', label: 'Loans', icon: HandCoins },
      { to: '/app/profile', label: 'Profile & security', icon: UserRound },
    ],
  },
  EMPLOYEE: {
    name: 'Branch desk',
    base: '/staff',
    nav: [
      { to: '/staff', label: 'Today', icon: LayoutGrid, end: true },
      { to: '/staff/kyc', label: 'KYC queue', icon: ClipboardCheck },
      { to: '/staff/customers', label: 'Customers', icon: Users },
      { to: '/staff/cash', label: 'Cash desk', icon: Banknote },
      { to: '/staff/loans', label: 'Loan applications', icon: HandCoins },
      { to: '/staff/security', label: 'Security', icon: ShieldCheck },
    ],
  },
  ADMIN: {
    name: 'Head office',
    base: '/admin',
    nav: [
      { to: '/admin', label: 'Analytics', icon: LayoutGrid, end: true },
      { to: '/admin/customers', label: 'Customers', icon: Users },
      { to: '/admin/employees', label: 'Employees', icon: UserRound },
      { to: '/admin/branches', label: 'Branches', icon: Building2 },
      { to: '/admin/rates', label: 'Rates & limits', icon: Percent },
      { to: '/admin/audit', label: 'Audit log', icon: FileClock },
      { to: '/admin/security', label: 'Security', icon: ShieldCheck },
    ],
  },
};

export function AppShell({ role }: { role: Role }) {
  const portal = PORTALS[role];
  const { user, signOut } = useAuth();
  const navigate = useNavigate();
  const [menuOpen, setMenuOpen] = useState(false);
  const unread = useUnreadCount();

  const handleSignOut = async () => {
    await signOut();
    navigate('/login', { replace: true });
  };

  const nav = (
    <nav aria-label={portal.name} className="flex flex-col gap-0.5">
      {portal.nav.map(({ to, label, icon: Icon, end }) => (
        <NavLink
          key={to}
          to={to}
          end={end}
          onClick={() => setMenuOpen(false)}
          className={({ isActive }) =>
            cn(
              'flex items-center gap-3 rounded-lg px-3 py-2 text-[0.94rem] transition-colors',
              isActive ? 'bg-kosh-700 font-medium text-white' : 'text-kosh-100/80 hover:bg-kosh-700/50 hover:text-white',
            )
          }
        >
          <Icon className="size-[18px] shrink-0" aria-hidden="true" />
          {label}
        </NavLink>
      ))}
    </nav>
  );

  const brand = (
    <Link to={portal.base} className="flex items-center gap-2.5 text-white">
      <span className="grid size-9 place-items-center rounded-lg bg-paper text-kosh-800">
        <Landmark className="size-5" aria-hidden="true" />
      </span>
      <span className="leading-tight">
        <span className="block font-display text-lg font-semibold">Kosh Bank</span>
        <span className="block text-xs text-kosh-200">{portal.name}</span>
      </span>
    </Link>
  );

  return (
    <div className="min-h-dvh lg:grid lg:grid-cols-[256px_1fr]">
      {/* Desktop sidebar */}
      <aside className="sticky top-0 hidden h-dvh flex-col justify-between bg-kosh-800 px-4 py-6 lg:flex">
        <div className="flex flex-col gap-8">
          {brand}
          {nav}
        </div>
        <div className="rounded-lg border border-kosh-700 p-3 text-sm">
          <p className="truncate font-medium text-white">{user?.fullName}</p>
          <p className="truncate text-xs text-kosh-200">{user?.email}</p>
          <button type="button" onClick={handleSignOut} className="mt-3 inline-flex items-center gap-2 text-kosh-100 hover:text-white">
            <LogOut className="size-4" aria-hidden="true" /> Sign out
          </button>
        </div>
      </aside>

      {/* Mobile drawer */}
      {menuOpen && (
        <div className="fixed inset-0 z-40 lg:hidden">
          <button type="button" className="absolute inset-0 bg-kosh-900/50" aria-label="Close menu" onClick={() => setMenuOpen(false)} />
          <div className="relative flex h-full w-72 max-w-[85vw] flex-col gap-8 bg-kosh-800 px-4 py-6">
            <div className="flex items-center justify-between">
              {brand}
              <button type="button" onClick={() => setMenuOpen(false)} className="text-kosh-100" aria-label="Close menu">
                <X className="size-5" />
              </button>
            </div>
            {nav}
            <button type="button" onClick={handleSignOut} className="mt-auto inline-flex items-center gap-2 px-3 text-kosh-100">
              <LogOut className="size-4" aria-hidden="true" /> Sign out
            </button>
          </div>
        </div>
      )}

      <div className="flex min-w-0 flex-col">
        <header className="sticky top-0 z-30 flex h-16 items-center justify-between gap-3 border-b border-rule bg-paper/90 px-4 backdrop-blur sm:px-8">
          <div className="flex items-center gap-3">
            <button type="button" className="rounded-md p-1.5 text-ink lg:hidden" onClick={() => setMenuOpen(true)} aria-label="Open menu">
              <Menu className="size-5" />
            </button>
            <p className="text-sm text-ink-soft">
              <span className="hidden sm:inline">{portal.name} · </span>
              <span className="font-medium text-ink">{greeting()}, {user?.fullName.split(' ')[0]}</span>
            </p>
          </div>
          <Link
            to={`${portal.base}/notifications`}
            className="relative rounded-full p-2 text-ink-soft hover:bg-surface hover:text-ink"
            aria-label={unread.data ? `Notifications, ${unread.data} unread` : 'Notifications'}
          >
            <Bell className="size-5" />
            {!!unread.data && (
              <span className="figures absolute -right-0.5 -top-0.5 grid min-w-5 place-items-center rounded-full bg-stamp px-1 text-[0.68rem] font-semibold text-white">
                {unread.data > 99 ? '99+' : unread.data}
              </span>
            )}
          </Link>
        </header>
        <main className="mx-auto w-full max-w-6xl flex-1 px-4 py-8 sm:px-8">
          <div className="page-enter">
            <Outlet />
          </div>
        </main>
      </div>
    </div>
  );
}

function greeting() {
  const hour = new Date().getHours();
  if (hour < 12) return 'Good morning';
  if (hour < 17) return 'Good afternoon';
  return 'Good evening';
}
