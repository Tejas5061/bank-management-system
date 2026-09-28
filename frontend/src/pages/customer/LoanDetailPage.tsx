import { ChevronLeft } from 'lucide-react';
import { Link, useParams } from 'react-router-dom';
import { useMyLoan } from '@/api/customer';
import { LoanSchedule } from '@/components/LoanSchedule';
import { Card, CardHeader, EmptyState, Figure } from '@/components/ui/Card';
import { Code } from '@/components/ui/Money';
import { LoadingBlock } from '@/components/ui/Spinner';
import { Stamp } from '@/components/ui/Status';
import { errorMessage } from '@/lib/errors';
import { formatDate, formatINR, LOAN_TYPE_LABEL } from '@/lib/format';

export default function LoanDetailPage() {
  const loanId = Number(useParams().loanId);
  const detail = useMyLoan(loanId);
  if (detail.isPending) return <LoadingBlock />;
  if (detail.isError) return <EmptyState title="Loan not available" description={errorMessage(detail.error)} />;
  const { loan, schedule, paidInstallments } = detail.data;
  const progress = loan.tenureMonths ? Math.round((paidInstallments / loan.tenureMonths) * 100) : 0;

  return (
    <div className="flex flex-col gap-6">
      <Link to="/app/loans" className="inline-flex w-fit items-center gap-1 text-sm font-medium text-kosh-600 hover:underline">
        <ChevronLeft className="size-4" aria-hidden="true" /> All loans
      </Link>
      <Card className="p-5">
        <div className="flex flex-wrap items-center gap-3">
          <h1 className="text-2xl font-semibold">{LOAN_TYPE_LABEL[loan.loanType]}</h1>
          <Stamp status={loan.status} />
        </div>
        <p className="mt-1 text-sm text-ink-soft"><Code>{loan.loanNumber}</Code> · {loan.purpose} · EMIs from ··{loan.accountNumber.slice(-4)}</p>
        <div className="mt-6 grid gap-6 sm:grid-cols-4">
          <Figure label="Principal" value={formatINR(loan.principal)} note={`${loan.interestRate}% p.a.`} />
          <Figure label="Monthly EMI" value={formatINR(loan.emiAmount)} note={`${loan.tenureMonths} months`} />
          <Figure label="Left to repay" value={formatINR(loan.outstandingPrincipal)} note="principal" />
          <Figure label="Repaid" value={`${paidInstallments} of ${loan.tenureMonths}`} note={`${progress}% complete`} />
        </div>
        {loan.status === 'ACTIVE' && (
          <div className="mt-5 h-2 overflow-hidden rounded-full bg-paper" role="progressbar" aria-valuenow={progress} aria-valuemin={0} aria-valuemax={100} aria-label="Repayment progress">
            <div className="h-full rounded-full bg-kosh-500" style={{ width: `${progress}%` }} />
          </div>
        )}
        {loan.reviewRemarks && (
          <p className="mt-5 rounded-lg bg-paper px-4 py-3 text-sm text-ink-soft">
            Loan officer's remarks{loan.reviewedAt ? ` (${formatDate(loan.reviewedAt)})` : ''}: <span className="text-ink">{loan.reviewRemarks}</span>
          </p>
        )}
      </Card>
      <Card>
        <CardHeader
          title="Repayment schedule"
          description={loan.status === 'PENDING' ? 'The schedule is created when the loan is approved and disbursed.' : 'EMIs are auto-debited on the due date; missed ones are retried daily.'}
        />
        {schedule.length ? <LoanSchedule schedule={schedule} /> : <EmptyState title="No schedule yet" />}
      </Card>
    </div>
  );
}
