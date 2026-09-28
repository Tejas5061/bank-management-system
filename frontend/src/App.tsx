import { lazy, Suspense, type ReactNode } from 'react';
import { BrowserRouter, Route, Routes } from 'react-router-dom';
import { RedirectHome, RequireRole } from '@/auth/RequireRole';
import { AppShell } from '@/components/layout/AppShell';
import { FullPageSpinner } from '@/components/ui/Spinner';
import { LoginPage } from '@/pages/auth/LoginPage';
import { NotFoundPage } from '@/pages/NotFoundPage';

// Each portal is its own chunk: a customer never downloads the admin console.
const RegisterPage = lazy(() => import('@/pages/auth/RegisterPage'));
const ForgotPasswordPage = lazy(() => import('@/pages/auth/ForgotPasswordPage'));
const NotificationsPage = lazy(() => import('@/pages/shared/NotificationsPage'));
const SecurityPage = lazy(() => import('@/pages/shared/SecurityPage'));

const DashboardPage = lazy(() => import('@/pages/customer/DashboardPage'));
const AccountsPage = lazy(() => import('@/pages/customer/AccountsPage'));
const AccountDetailPage = lazy(() => import('@/pages/customer/AccountDetailPage'));
const TransferPage = lazy(() => import('@/pages/customer/TransferPage'));
const BeneficiariesPage = lazy(() => import('@/pages/customer/BeneficiariesPage'));
const DepositsPage = lazy(() => import('@/pages/customer/DepositsPage'));
const LoansPage = lazy(() => import('@/pages/customer/LoansPage'));
const LoanDetailPage = lazy(() => import('@/pages/customer/LoanDetailPage'));
const ProfilePage = lazy(() => import('@/pages/customer/ProfilePage'));

const StaffDashboardPage = lazy(() => import('@/pages/staff/StaffDashboardPage'));
const CustomersPage = lazy(() => import('@/pages/staff/CustomersPage'));
const CustomerDetailPage = lazy(() => import('@/pages/staff/CustomerDetailPage'));
const CashDeskPage = lazy(() => import('@/pages/staff/CashDeskPage'));
const LoanReviewPage = lazy(() => import('@/pages/staff/LoanReviewPage'));
const StaffAccountPage = lazy(() => import('@/pages/staff/StaffAccountPage'));

const AnalyticsPage = lazy(() => import('@/pages/admin/AnalyticsPage'));
const EmployeesPage = lazy(() => import('@/pages/admin/EmployeesPage'));
const BranchesPage = lazy(() => import('@/pages/admin/BranchesPage'));
const RatesPage = lazy(() => import('@/pages/admin/RatesPage'));
const AuditLogPage = lazy(() => import('@/pages/admin/AuditLogPage'));

const page = (element: ReactNode) => <Suspense fallback={<FullPageSpinner label="Loading" />}>{element}</Suspense>;

export function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/" element={<RedirectHome />} />
        <Route path="/login" element={<LoginPage />} />
        <Route path="/register" element={page(<RegisterPage />)} />
        <Route path="/forgot-password" element={page(<ForgotPasswordPage />)} />

        <Route path="/app" element={<RequireRole roles={['CUSTOMER']}><AppShell role="CUSTOMER" /></RequireRole>}>
          <Route index element={page(<DashboardPage />)} />
          <Route path="accounts" element={page(<AccountsPage />)} />
          <Route path="accounts/:accountId" element={page(<AccountDetailPage />)} />
          <Route path="transfer" element={page(<TransferPage />)} />
          <Route path="beneficiaries" element={page(<BeneficiariesPage />)} />
          <Route path="deposits" element={page(<DepositsPage />)} />
          <Route path="loans" element={page(<LoansPage />)} />
          <Route path="loans/:loanId" element={page(<LoanDetailPage />)} />
          <Route path="profile" element={page(<ProfilePage />)} />
          <Route path="notifications" element={page(<NotificationsPage />)} />
        </Route>

        <Route path="/staff" element={<RequireRole roles={['EMPLOYEE']}><AppShell role="EMPLOYEE" /></RequireRole>}>
          <Route index element={page(<StaffDashboardPage />)} />
          <Route path="kyc" element={page(<CustomersPage kycQueue />)} />
          <Route path="customers" element={page(<CustomersPage />)} />
          <Route path="customers/:customerId" element={page(<CustomerDetailPage />)} />
          <Route path="accounts/:accountId" element={page(<StaffAccountPage />)} />
          <Route path="cash" element={page(<CashDeskPage />)} />
          <Route path="loans" element={page(<LoanReviewPage />)} />
          <Route path="security" element={page(<SecurityPage />)} />
          <Route path="notifications" element={page(<NotificationsPage />)} />
        </Route>

        <Route path="/admin" element={<RequireRole roles={['ADMIN']}><AppShell role="ADMIN" /></RequireRole>}>
          <Route index element={page(<AnalyticsPage />)} />
          <Route path="customers" element={page(<CustomersPage />)} />
          <Route path="customers/:customerId" element={page(<CustomerDetailPage />)} />
          <Route path="accounts/:accountId" element={page(<StaffAccountPage />)} />
          <Route path="employees" element={page(<EmployeesPage />)} />
          <Route path="branches" element={page(<BranchesPage />)} />
          <Route path="rates" element={page(<RatesPage />)} />
          <Route path="audit" element={page(<AuditLogPage />)} />
          <Route path="security" element={page(<SecurityPage />)} />
          <Route path="notifications" element={page(<NotificationsPage />)} />
        </Route>

        <Route path="*" element={<NotFoundPage />} />
      </Routes>
    </BrowserRouter>
  );
}
