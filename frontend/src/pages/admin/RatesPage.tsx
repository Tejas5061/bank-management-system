import { useState, type ChangeEvent } from 'react';
import { toast } from 'sonner';
import { useAdminRates, useUpdateLoanProduct, useUpdatePolicy } from '@/api/admin';
import { Button } from '@/components/ui/Button';
import { Card, CardHeader, EmptyState, PageHeader } from '@/components/ui/Card';
import { Input } from '@/components/ui/Field';
import { LoadingBlock } from '@/components/ui/Spinner';
import { errorMessage } from '@/lib/errors';
import { ACCOUNT_TYPE_LABEL, formatDateTime, LOAN_TYPE_LABEL } from '@/lib/format';
import type { AccountPolicy, LoanProduct } from '@/types/api';

export default function RatesPage() {
  const rates = useAdminRates();
  if (rates.isPending) return <LoadingBlock />;
  if (rates.isError) return <EmptyState title="Could not load rates" description={errorMessage(rates.error)} />;
  return (
    <>
      <PageHeader title="Rates & limits" description="Changes apply to new bookings immediately. Existing FDs and loans keep the rate they were booked at. Every change is audited." />
      <Card>
        <CardHeader title="Accounts" description="For fixed deposits, the minimum balance is the minimum amount per deposit." />
        {rates.data.accounts.map((policy) => <PolicyRow key={policy.accountType} policy={policy} />)}
      </Card>
      <Card className="mt-6">
        <CardHeader title="Loan products" />
        {rates.data.loans.map((product) => <LoanRow key={product.loanType} product={product} />)}
      </Card>
    </>
  );
}

function PolicyRow({ policy }: { policy: AccountPolicy }) {
  const update = useUpdatePolicy();
  const [rate, setRate] = useState(String(policy.interestRate));
  const [minimum, setMinimum] = useState(String(policy.minimumBalance));
  const [limit, setLimit] = useState(String(policy.dailyTransferLimit));
  const dirty = rate !== String(policy.interestRate) || minimum !== String(policy.minimumBalance) || limit !== String(policy.dailyTransferLimit);

  return (
    <form className="ledger-row grid items-end gap-4 px-5 py-4 md:grid-cols-[10rem_1fr_1fr_1fr_auto]" onSubmit={(e) => {
      e.preventDefault();
      update.mutate({ type: policy.accountType, interestRate: Number(rate), minimumBalance: Number(minimum), dailyTransferLimit: Number(limit) }, {
        onSuccess: () => toast.success(`${ACCOUNT_TYPE_LABEL[policy.accountType]} rates saved`),
        onError: (err) => toast.error(errorMessage(err)),
      });
    }}>
      <div>
        <p className="font-medium">{ACCOUNT_TYPE_LABEL[policy.accountType]}</p>
        <p className="text-xs text-ink-faint">Updated {formatDateTime(policy.updatedAt)}</p>
      </div>
      <Input label="Interest % p.a." type="number" step="0.01" min="0" max="30" value={rate} onChange={(e) => setRate(e.target.value)} />
      <Input label="Minimum balance" prefix="₹" type="number" step="1" min="0" value={minimum} onChange={(e) => setMinimum(e.target.value)} />
      <Input label="Daily transfer limit" prefix="₹" type="number" step="1" min="0" value={limit} onChange={(e) => setLimit(e.target.value)} />
      <Button type="submit" variant="secondary" disabled={!dirty} loading={update.isPending}>Save</Button>
    </form>
  );
}

function LoanRow({ product }: { product: LoanProduct }) {
  const update = useUpdateLoanProduct();
  const [values, setValues] = useState({
    interestRate: String(product.interestRate),
    minAmount: String(product.minAmount),
    maxAmount: String(product.maxAmount),
    minTenureMonths: String(product.minTenureMonths),
    maxTenureMonths: String(product.maxTenureMonths),
  });
  const set = (key: keyof typeof values) => (e: ChangeEvent<HTMLInputElement>) => setValues((v) => ({ ...v, [key]: e.target.value }));

  return (
    <form className="ledger-row grid items-end gap-4 px-5 py-4 md:grid-cols-[10rem_repeat(5,1fr)_auto]" onSubmit={(e) => {
      e.preventDefault();
      update.mutate({
        type: product.loanType,
        interestRate: Number(values.interestRate),
        minAmount: Number(values.minAmount),
        maxAmount: Number(values.maxAmount),
        minTenureMonths: Number(values.minTenureMonths),
        maxTenureMonths: Number(values.maxTenureMonths),
      }, {
        onSuccess: () => toast.success(`${LOAN_TYPE_LABEL[product.loanType]} saved`),
        onError: (err) => toast.error(errorMessage(err)),
      });
    }}>
      <p className="font-medium">{LOAN_TYPE_LABEL[product.loanType]}</p>
      <Input label="Rate %" type="number" step="0.01" value={values.interestRate} onChange={set('interestRate')} />
      <Input label="Min amount" type="number" value={values.minAmount} onChange={set('minAmount')} />
      <Input label="Max amount" type="number" value={values.maxAmount} onChange={set('maxAmount')} />
      <Input label="Min months" type="number" value={values.minTenureMonths} onChange={set('minTenureMonths')} />
      <Input label="Max months" type="number" value={values.maxTenureMonths} onChange={set('maxTenureMonths')} />
      <Button type="submit" variant="secondary" loading={update.isPending}>Save</Button>
    </form>
  );
}
