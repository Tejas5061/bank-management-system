import { useState } from 'react';
import { toast } from 'sonner';
import { useLoanDecision, useStaffLoan, useStaffLoans } from '@/api/staff';
import { useAuth } from '@/auth/useAuth';
import { LoanSchedule } from '@/components/LoanSchedule';
import { Button } from '@/components/ui/Button';
import { Card, EmptyState, Figure, PageHeader } from '@/components/ui/Card';
import { Dialog } from '@/components/ui/Dialog';
import { Textarea } from '@/components/ui/Field';
import { Code } from '@/components/ui/Money';
import { Pagination } from '@/components/ui/Pagination';
import { LoadingBlock } from '@/components/ui/Spinner';
import { Stamp } from '@/components/ui/Status';
import { Tabs } from '@/components/ui/Tabs';
import { errorMessage } from '@/lib/errors';
import { formatDate, formatINR, LOAN_TYPE_LABEL } from '@/lib/format';
import type { LoanStatus } from '@/types/api';

export default function LoanReviewPage() {
  const [status, setStatus] = useState<LoanStatus | ''>('PENDING');
  const [page, setPage] = useState(0);
  const [openId, setOpenId] = useState<number | null>(null);
  const loans = useStaffLoans(status, page);

  return (
    <>
      <PageHeader title="Loan applications" description="Approving disburses the loan to the customer's account straight away and creates the EMI schedule." />
      <div className="mb-4">
        <Tabs<LoanStatus | ''> label="Filter loans" value={status} onChange={(s) => { setStatus(s); setPage(0); }}
          options={[{ value: 'PENDING', label: 'To review' }, { value: 'ACTIVE', label: 'Active' }, { value: 'REJECTED', label: 'Rejected' }, { value: 'CLOSED', label: 'Closed' }, { value: '', label: 'All' }]} />
      </div>
      <Card>
        {loans.isPending ? <LoadingBlock /> : !loans.data?.content.length ? (
          <EmptyState title={status === 'PENDING' ? 'No applications waiting' : 'No loans here'} />
        ) : (
          <>
            <ul>
              {loans.data.content.map((loan) => (
                <li key={loan.id} className="ledger-row">
                  <button type="button" onClick={() => setOpenId(loan.id)} className="grid w-full gap-3 px-5 py-4 text-left hover:bg-kosh-50/60 sm:grid-cols-[1fr_auto]">
                    <div>
                      <div className="flex flex-wrap items-center gap-3">
                        <p className="font-medium">{loan.customerName}</p>
                        <Stamp status={loan.status} />
                      </div>
                      <p className="mt-1 text-sm text-ink-soft">
                        {LOAN_TYPE_LABEL[loan.loanType]} · <Code>{loan.loanNumber}</Code> · {loan.purpose} · applied {formatDate(loan.appliedAt)}
                      </p>
                    </div>
                    <div className="text-sm sm:text-right">
                      <p className="figures font-display text-lg font-semibold">{formatINR(loan.principal)}</p>
                      <p className="figures text-ink-soft">{loan.tenureMonths} months · {loan.interestRate}%</p>
                    </div>
                  </button>
                </li>
              ))}
            </ul>
            <Pagination page={loans.data} onChange={setPage} />
          </>
        )}
      </Card>
      <LoanReviewDialog loanId={openId} onClose={() => setOpenId(null)} />
    </>
  );
}

function LoanReviewDialog({ loanId, onClose }: { loanId: number | null; onClose: () => void }) {
  const { user } = useAuth();
  const detail = useStaffLoan(loanId);
  const decide = useLoanDecision();
  const [remarks, setRemarks] = useState('');
  const loan = detail.data?.loan;

  const act = async (decision: 'approve' | 'reject') => {
    if (!loanId) return;
    try {
      await decide.mutateAsync({ loanId, decision, remarks: remarks.trim() });
      await detail.refetch();
      toast.success(decision === 'approve' ? 'Loan approved and disbursed' : 'Application rejected; the customer has been notified');
      setRemarks('');
    } catch (e) {
      toast.error(errorMessage(e));
    }
  };

  return (
    <Dialog open={loanId !== null} onClose={onClose} wide title={loan ? `${LOAN_TYPE_LABEL[loan.loanType]} · ${loan.customerName}` : 'Loan'}>
      {!loan ? <LoadingBlock /> : (
        <div className="flex flex-col gap-5">
          <div className="flex flex-wrap items-center gap-3 text-sm text-ink-soft">
            <Stamp status={loan.status} land={loan.status !== 'PENDING'} />
            <Code>{loan.loanNumber}</Code> · <Code>{loan.customerNumber}</Code> · to A/c <Code>{loan.accountNumber}</Code>
          </div>
          <div className="grid grid-cols-2 gap-5 sm:grid-cols-4">
            <Figure label="Amount" value={formatINR(loan.principal)} />
            <Figure label="Rate" value={`${loan.interestRate}%`} />
            <Figure label="Tenure" value={`${loan.tenureMonths} mo`} />
            <Figure label="EMI" value={formatINR(loan.emiAmount)} />
          </div>
          <p className="rounded-lg bg-paper px-4 py-3 text-sm"><span className="text-ink-soft">Purpose:</span> {loan.purpose}</p>
          {loan.status === 'PENDING' && user?.role === 'EMPLOYEE' ? (
            <div className="flex flex-col gap-3">
              <Textarea label="Remarks for the customer" value={remarks} onChange={(e) => setRemarks(e.target.value)} maxLength={500}
                hint="Required. Shown to the customer and recorded in the audit log." />
              <div className="flex justify-end gap-2">
                <Button variant="secondary" onClick={() => act('reject')} disabled={remarks.trim().length < 3 || decide.isPending}>Reject</Button>
                <Button onClick={() => act('approve')} loading={decide.isPending} disabled={remarks.trim().length < 3}>
                  Approve and disburse {formatINR(loan.principal)}
                </Button>
              </div>
            </div>
          ) : loan.reviewRemarks ? (
            <p className="text-sm text-ink-soft">Remarks: <span className="text-ink">{loan.reviewRemarks}</span></p>
          ) : null}
          {detail.data && detail.data.schedule.length > 0 && (
            <div className="-mx-5 border-t border-rule">
              <LoanSchedule schedule={detail.data.schedule} />
            </div>
          )}
        </div>
      )}
    </Dialog>
  );
}
