import { ChevronLeft } from 'lucide-react';
import { useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { toast } from 'sonner';
import { useCustomer, useOpenAccountFor, useReviewKyc } from '@/api/staff';
import { useAuth } from '@/auth/useAuth';
import { Button } from '@/components/ui/Button';
import { Card, CardHeader, EmptyState } from '@/components/ui/Card';
import { Select, Textarea } from '@/components/ui/Field';
import { Code } from '@/components/ui/Money';
import { LoadingBlock } from '@/components/ui/Spinner';
import { Stamp } from '@/components/ui/Status';
import { errorMessage } from '@/lib/errors';
import { ACCOUNT_TYPE_LABEL, formatDate, formatINR } from '@/lib/format';
import type { AccountType } from '@/types/api';

export default function CustomerDetailPage() {
  const customerId = Number(useParams().customerId);
  const { user } = useAuth();
  const isTeller = user?.role === 'EMPLOYEE';
  const base = isTeller ? '/staff' : '/admin';
  const customer = useCustomer(customerId);
  const review = useReviewKyc();
  const openAccount = useOpenAccountFor();
  const [remarks, setRemarks] = useState('');
  const [justStamped, setJustStamped] = useState(false);
  const [newType, setNewType] = useState<AccountType>('SAVINGS');

  if (customer.isPending) return <LoadingBlock />;
  if (customer.isError) return <EmptyState title="Customer not found" description={errorMessage(customer.error)} />;
  const { profile: p, accounts } = customer.data;

  const decide = async (status: 'VERIFIED' | 'REJECTED') => {
    try {
      await review.mutateAsync({ customerId, status, remarks: remarks.trim() || undefined });
      setJustStamped(true);
      setRemarks('');
      toast.success(status === 'VERIFIED' ? `${p.fullName}'s KYC is verified` : `KYC rejected; ${p.fullName} has been notified`);
    } catch (e) {
      toast.error(errorMessage(e));
    }
  };

  return (
    <div className="flex flex-col gap-6">
      <Link to={`${base}/customers`} className="inline-flex w-fit items-center gap-1 text-sm font-medium text-kosh-600 hover:underline">
        <ChevronLeft className="size-4" aria-hidden="true" /> Customers
      </Link>

      <div className="grid gap-6 lg:grid-cols-[minmax(0,3fr)_minmax(0,2fr)]">
        <Card>
          <div className="flex flex-wrap items-start justify-between gap-4 border-b border-rule px-5 py-5">
            <div>
              <h1 className="text-2xl font-semibold">{p.fullName}</h1>
              <p className="mt-1 text-sm text-ink-soft"><Code>{p.customerNumber}</Code> · customer since {formatDate(p.createdAt)}</p>
            </div>
            <Stamp key={p.kycStatus} status={p.kycStatus} label={`KYC ${p.kycStatus.toLowerCase()}`} land={justStamped} className="text-sm" />
          </div>
          <dl className="grid gap-x-6 gap-y-3 px-5 py-5 text-sm sm:grid-cols-[auto_1fr]">
            <dt className="text-ink-soft">Email</dt><dd>{p.email}</dd>
            <dt className="text-ink-soft">Mobile</dt><dd>{p.phone}</dd>
            <dt className="text-ink-soft">Date of birth</dt><dd>{formatDate(p.dateOfBirth)}</dd>
            <dt className="text-ink-soft">PAN</dt><dd><Code>{p.maskedPan}</Code></dd>
            <dt className="text-ink-soft">Aadhaar</dt><dd><Code>{p.maskedAadhaar}</Code></dd>
            <dt className="text-ink-soft">Address</dt><dd>{p.addressLine}, {p.city}, {p.state} {p.pincode}</dd>
            <dt className="text-ink-soft">Home branch</dt><dd>{p.homeBranch.name} · <Code>{p.homeBranch.ifsc}</Code></dd>
            {p.kycReviewedAt && <><dt className="text-ink-soft">KYC reviewed</dt><dd>{formatDate(p.kycReviewedAt)}{p.kycRemarks ? ` · ${p.kycRemarks}` : ''}</dd></>}
          </dl>
        </Card>

        {isTeller ? (
          <Card className="h-fit">
            <CardHeader title="KYC decision" description="Check the original PAN and Aadhaar against the details on the left." />
            <div className="flex flex-col gap-4 px-5 py-5">
              <Textarea label="Remarks" value={remarks} onChange={(e) => setRemarks(e.target.value)} maxLength={500}
                hint="Required when rejecting; the customer sees this message." />
              <div className="flex flex-wrap gap-2">
                <Button onClick={() => decide('VERIFIED')} loading={review.isPending} disabled={p.kycStatus === 'VERIFIED'}>Verify KYC</Button>
                <Button variant="secondary" onClick={() => decide('REJECTED')} disabled={p.kycStatus === 'REJECTED' || remarks.trim().length === 0 || review.isPending}>
                  Reject
                </Button>
              </div>
            </div>
          </Card>
        ) : (
          <Card className="h-fit p-5 text-sm text-ink-soft">KYC decisions, account opening and cash handling are done by branch staff.</Card>
        )}
      </div>

      <Card>
        <CardHeader
          title="Accounts"
          action={isTeller && p.kycStatus === 'VERIFIED' && (
            <div className="flex items-end gap-2">
              <Select label="New account" value={newType} onChange={(e) => setNewType(e.target.value as AccountType)} className="w-36">
                <option value="SAVINGS">Savings</option>
                <option value="CURRENT">Current</option>
              </Select>
              <Button variant="secondary" loading={openAccount.isPending} onClick={async () => {
                try {
                  const account = await openAccount.mutateAsync({ customerId, accountType: newType });
                  toast.success(`Opened ${ACCOUNT_TYPE_LABEL[account.accountType].toLowerCase()} account ${account.accountNumber}`);
                } catch (e) {
                  toast.error(errorMessage(e));
                }
              }}>Open</Button>
            </div>
          )}
        />
        <ul>
          {accounts.map((a) => (
            <li key={a.id} className="ledger-row">
              <Link to={`${base}/accounts/${a.id}`} className="flex flex-wrap items-center gap-4 px-5 py-4 hover:bg-kosh-50/60">
                <div className="min-w-0 flex-1">
                  <p className="font-medium">{ACCOUNT_TYPE_LABEL[a.accountType]}</p>
                  <p className="text-sm text-ink-soft"><Code group>{a.accountNumber}</Code> · {a.branchName}</p>
                </div>
                {a.status !== 'ACTIVE' && <Stamp status={a.status} />}
                <p className="figures font-display text-lg font-semibold">{formatINR(a.balance)}</p>
              </Link>
            </li>
          ))}
        </ul>
      </Card>
    </div>
  );
}
