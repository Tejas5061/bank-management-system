import { useState } from 'react';
import { Link } from 'react-router-dom';
import { toast } from 'sonner';
import { useEmiCalculation, useRates } from '@/api/common';
import { useApplyLoan, useMyAccounts, useMyLoans } from '@/api/customer';
import { Button } from '@/components/ui/Button';
import { Card, CardHeader, EmptyState, Figure, PageHeader } from '@/components/ui/Card';
import { Input, Select, Textarea } from '@/components/ui/Field';
import { Code } from '@/components/ui/Money';
import { LoadingBlock } from '@/components/ui/Spinner';
import { Stamp } from '@/components/ui/Status';
import { errorMessage } from '@/lib/errors';
import { ACCOUNT_TYPE_LABEL, formatDate, formatINR, LOAN_TYPE_LABEL } from '@/lib/format';
import { useDebounced } from '@/lib/useDebounced';
import type { LoanType } from '@/types/api';

export default function LoansPage() {
  const loans = useMyLoans();
  return (
    <>
      <PageHeader title="Loans" description="Apply online; a loan officer reviews every application. EMIs are collected automatically from the account you choose." />
      <div className="grid gap-6 xl:grid-cols-[minmax(0,1fr)_380px]">
        <Card>
          <CardHeader title="Your loans and applications" />
          {loans.isPending ? <LoadingBlock /> : !loans.data?.length ? (
            <EmptyState title="No loans" description="Use the calculator to see what a loan would cost before you apply." />
          ) : (
            <ul>
              {loans.data.map((loan) => (
                <li key={loan.id} className="ledger-row">
                  <Link to={`/app/loans/${loan.id}`} className="grid gap-3 px-5 py-4 hover:bg-kosh-50/60 sm:grid-cols-[1fr_auto]">
                    <div>
                      <div className="flex flex-wrap items-center gap-3">
                        <p className="font-medium">{LOAN_TYPE_LABEL[loan.loanType]}</p>
                        <Stamp status={loan.status} />
                      </div>
                      <p className="mt-1 text-sm text-ink-soft"><Code>{loan.loanNumber}</Code> · {loan.purpose} · applied {formatDate(loan.appliedAt)}</p>
                    </div>
                    <div className="text-sm sm:text-right">
                      <p className="figures font-display text-lg font-semibold">{formatINR(loan.principal)}</p>
                      <p className="figures text-ink-soft">EMI {formatINR(loan.emiAmount)} × {loan.tenureMonths}</p>
                      {loan.status === 'ACTIVE' && <p className="figures text-ink-soft">{formatINR(loan.outstandingPrincipal)} left to repay</p>}
                    </div>
                  </Link>
                </li>
              ))}
            </ul>
          )}
        </Card>
        <ApplyCard />
      </div>
    </>
  );
}

function ApplyCard() {
  const rates = useRates();
  const accounts = useMyAccounts();
  const apply = useApplyLoan();
  const [loanType, setLoanType] = useState<LoanType>('PERSONAL');
  const [amount, setAmount] = useState('300000');
  const [tenure, setTenure] = useState('36');
  const [accountId, setAccountId] = useState<number | ''>('');
  const [purpose, setPurpose] = useState('');

  const product = rates.data?.loans.find((p) => p.loanType === loanType);
  const operative = (accounts.data ?? []).filter((a) => a.accountType !== 'FIXED_DEPOSIT' && a.status === 'ACTIVE');
  const principal = Number(amount);
  const months = Number(tenure);
  const debouncedPrincipal = useDebounced(principal, 350);
  const debouncedMonths = useDebounced(months, 350);
  const withinLimits = !!product && principal >= product.minAmount && principal <= product.maxAmount
    && months >= product.minTenureMonths && months <= product.maxTenureMonths;
  const emi = useEmiCalculation(debouncedPrincipal, product?.interestRate ?? 0, debouncedMonths,
    !!product && debouncedPrincipal >= 1000 && Number.isInteger(debouncedMonths) && debouncedMonths >= 1 && debouncedMonths <= 480);

  const submit = async () => {
    const account = accountId || operative[0]?.id;
    if (!account) return;
    try {
      const loan = await apply.mutateAsync({ loanType, amount: principal, tenureMonths: months, accountId: account, purpose: purpose.trim() });
      toast.success(`Application ${loan.loanNumber} submitted. We will notify you when it is reviewed.`);
      setPurpose('');
    } catch (e) {
      toast.error(errorMessage(e));
    }
  };

  return (
    <Card className="h-fit p-5">
      <h2 className="text-base font-semibold">EMI calculator & application</h2>
      <div className="mt-4 flex flex-col gap-4">
        <Select label="Loan type" value={loanType} onChange={(e) => setLoanType(e.target.value as LoanType)}>
          {(['PERSONAL', 'HOME', 'EDUCATION'] as const).map((t) => <option key={t} value={t}>{LOAN_TYPE_LABEL[t]}</option>)}
        </Select>
        {product && (
          <p className="-mt-2 text-xs text-ink-faint">
            {product.interestRate}% p.a. · {formatINR(product.minAmount)} to {formatINR(product.maxAmount)} · {product.minTenureMonths}–{product.maxTenureMonths} months
          </p>
        )}
        <div className="grid grid-cols-2 gap-3">
          <Input label="Amount" prefix="₹" type="number" inputMode="decimal" value={amount} onChange={(e) => setAmount(e.target.value)} />
          <Input label="Months" type="number" inputMode="numeric" value={tenure} onChange={(e) => setTenure(e.target.value)} />
        </div>
        {emi.data && (
          <div className="grid grid-cols-2 gap-4 rounded-lg bg-paper p-4">
            <Figure label="Monthly EMI" value={formatINR(emi.data.emi)} />
            <Figure label="Total interest" value={formatINR(emi.data.totalInterest)} />
            <p className="col-span-2 text-xs text-ink-soft">You repay {formatINR(emi.data.totalPayment)} in total over {emi.data.tenureMonths} months.</p>
          </div>
        )}
        {!withinLimits && product && <p className="text-sm text-caution">This amount or tenure is outside the {LOAN_TYPE_LABEL[loanType].toLowerCase()} limits above.</p>}
        <Select label="Disburse to and collect EMIs from" value={accountId || operative[0]?.id || ''} onChange={(e) => setAccountId(Number(e.target.value))}>
          {operative.map((a) => <option key={a.id} value={a.id}>{ACCOUNT_TYPE_LABEL[a.accountType]} ··{a.accountNumber.slice(-4)}</option>)}
        </Select>
        <Textarea label="Purpose" value={purpose} onChange={(e) => setPurpose(e.target.value)} maxLength={255} placeholder="What is the loan for?" />
        <Button size="lg" onClick={submit} loading={apply.isPending} disabled={!withinLimits || purpose.trim().length < 3 || operative.length === 0}>
          Apply for {formatINR(Number.isFinite(principal) ? principal : 0)}
        </Button>
      </div>
    </Card>
  );
}
