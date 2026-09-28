import { useState } from 'react';
import { toast } from 'sonner';
import { useFdQuote } from '@/api/common';
import { useFixedDeposits, useMyAccounts, useOpenFixedDeposit } from '@/api/customer';
import { Button } from '@/components/ui/Button';
import { Card, CardHeader, EmptyState, Figure, PageHeader } from '@/components/ui/Card';
import { Input, Select } from '@/components/ui/Field';
import { Code } from '@/components/ui/Money';
import { LoadingBlock } from '@/components/ui/Spinner';
import { Stamp } from '@/components/ui/Status';
import { useDebounced } from '@/lib/useDebounced';
import { errorCode, errorMessage } from '@/lib/errors';
import { ACCOUNT_TYPE_LABEL, formatDate, formatINR } from '@/lib/format';
import { newIdempotencyKey } from '@/lib/util';

const TENURES = [3, 6, 12, 24, 36, 60];

export default function DepositsPage() {
  const deposits = useFixedDeposits();
  const accounts = useMyAccounts();
  const book = useOpenFixedDeposit();
  const operative = (accounts.data ?? []).filter((a) => a.accountType !== 'FIXED_DEPOSIT' && a.status === 'ACTIVE');

  const [sourceId, setSourceId] = useState<number | ''>('');
  const [principal, setPrincipal] = useState('50000');
  const [tenure, setTenure] = useState(12);
  const [key, setKey] = useState(newIdempotencyKey);
  const amount = Number(principal);
  const debouncedAmount = useDebounced(amount, 350);
  const quote = useFdQuote(debouncedAmount, tenure, debouncedAmount >= 1000);
  const source = operative.find((a) => a.id === (sourceId || operative[0]?.id));

  const submit = async () => {
    if (!source) return;
    try {
      const fd = await book.mutateAsync({ idempotencyKey: key, body: { sourceAccountId: source.id, principal: amount, tenureMonths: tenure } });
      toast.success(`Fixed deposit ${fd.accountNumber} booked. It matures on ${formatDate(fd.maturityDate)}.`);
      setKey(newIdempotencyKey());
    } catch (e) {
      if (errorCode(e) === 'IDEMPOTENCY_KEY_REUSED') setKey(newIdempotencyKey());
      toast.error(errorMessage(e));
    }
  };

  return (
    <>
      <PageHeader title="Fixed deposits" description="Lock money away at a fixed rate. Interest compounds quarterly and is paid out with your principal on maturity." />
      <div className="grid gap-6 xl:grid-cols-[minmax(0,1fr)_360px]">
        <Card>
          <CardHeader title="Your deposits" />
          {deposits.isPending ? <LoadingBlock /> : !deposits.data?.length ? (
            <EmptyState title="No fixed deposits yet" description="Book one on the right. The rate is locked for the full tenure." />
          ) : (
            <ul>
              {deposits.data.map((fd) => (
                <li key={fd.id} className="ledger-row grid gap-4 px-5 py-4 sm:grid-cols-[1fr_auto]">
                  <div>
                    <div className="flex items-center gap-3">
                      <p className="figures font-display text-xl font-semibold">{formatINR(fd.principal)}</p>
                      <Stamp status={fd.status} />
                    </div>
                    <p className="mt-1 text-sm text-ink-soft">
                      <Code>{fd.accountNumber}</Code> · {fd.interestRate}% p.a. · {fd.tenureMonths} months · pays into ··{fd.payoutAccountNumber.slice(-4)}
                    </p>
                  </div>
                  <div className="text-sm sm:text-right">
                    <p className="text-ink-soft">{fd.status === 'MATURED' ? 'Matured' : 'Matures'} {formatDate(fd.maturityDate)}</p>
                    <p className="figures font-medium text-ink">{formatINR(fd.maturityAmount)}</p>
                    <p className="figures text-credit">+{formatINR(fd.interestEarned)} interest</p>
                  </div>
                </li>
              ))}
            </ul>
          )}
        </Card>

        <Card className="h-fit p-5">
          <h2 className="text-base font-semibold">Book a deposit</h2>
          {operative.length === 0 ? (
            <p className="mt-3 text-sm text-ink-soft">You need an active savings or current account to fund a deposit.</p>
          ) : (
            <div className="mt-4 flex flex-col gap-4">
              <Select label="Fund from" value={source?.id ?? ''} onChange={(e) => setSourceId(Number(e.target.value))}>
                {operative.map((a) => (
                  <option key={a.id} value={a.id}>{ACCOUNT_TYPE_LABEL[a.accountType]} ··{a.accountNumber.slice(-4)} · {formatINR(a.availableBalance)}</option>
                ))}
              </Select>
              <Input label="Amount" prefix="₹" type="number" inputMode="decimal" min={10000} step="1000" value={principal}
                onChange={(e) => setPrincipal(e.target.value)} hint="Minimum ₹10,000" />
              <fieldset>
                <legend className="text-sm font-medium">Tenure</legend>
                <div className="mt-1.5 grid grid-cols-3 gap-2">
                  {TENURES.map((t) => (
                    <button key={t} type="button" onClick={() => setTenure(t)} aria-pressed={tenure === t}
                      className={`rounded-lg border px-2 py-2 text-sm ${tenure === t ? 'border-kosh-800 bg-kosh-800 text-paper' : 'border-rule-strong hover:bg-kosh-50'}`}>
                      {t < 12 ? `${t} months` : `${t / 12} year${t > 12 ? 's' : ''}`}
                    </button>
                  ))}
                </div>
              </fieldset>
              {quote.data && amount >= 1000 && (
                <div className="grid grid-cols-2 gap-4 rounded-lg bg-paper p-4">
                  <Figure label="At maturity" value={formatINR(quote.data.maturityAmount)} />
                  <Figure label="Interest" value={formatINR(quote.data.interestEarned)} tone="credit" note={`${quote.data.interestRate}% p.a.`} />
                  <p className="col-span-2 text-xs text-ink-soft">Matures on {formatDate(quote.data.maturityDate)} and pays into ··{source?.accountNumber.slice(-4)}.</p>
                </div>
              )}
              <Button size="lg" onClick={submit} loading={book.isPending} disabled={!(amount >= 10000)}>
                Book deposit of {Number.isFinite(amount) ? formatINR(amount) : '₹0'}
              </Button>
            </div>
          )}
        </Card>
      </div>
    </>
  );
}
