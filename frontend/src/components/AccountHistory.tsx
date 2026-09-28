import { Download } from 'lucide-react';
import { useState } from 'react';
import { toast } from 'sonner';
import { useHistory, type HistoryFilters } from '@/api/common';
import { Passbook } from './Passbook';
import { Button } from './ui/Button';
import { Card } from './ui/Card';
import { Input, Select } from './ui/Field';
import { Pagination } from './ui/Pagination';
import { LoadingBlock } from './ui/Spinner';
import { EmptyState } from './ui/Card';
import { errorMessage } from '@/lib/errors';
import { TRANSACTION_LABEL } from '@/lib/format';
import { downloadFile } from '@/lib/util';
import type { TransactionType } from '@/types/api';

/** Filterable, paginated passbook for one account, with statement downloads for the same range. */
export function AccountHistory({ accountId }: { accountId: number }) {
  const [filters, setFilters] = useState<HistoryFilters>({ from: '', to: '', type: '', status: '', page: 0 });
  const history = useHistory(accountId, filters);
  const [downloading, setDownloading] = useState<'PDF' | 'CSV' | null>(null);
  const update = (patch: Partial<HistoryFilters>) => setFilters((f) => ({ ...f, ...patch, page: 'page' in patch ? patch.page! : 0 }));

  const download = async (format: 'PDF' | 'CSV') => {
    setDownloading(format);
    try {
      await downloadFile(`/accounts/${accountId}/statement`, { format, from: filters.from || undefined, to: filters.to || undefined },
        `statement.${format.toLowerCase()}`);
    } catch (e) {
      toast.error(errorMessage(e));
    } finally {
      setDownloading(null);
    }
  };

  return (
    <Card>
      <div className="flex flex-wrap items-end gap-3 border-b border-rule px-5 py-4">
        <Input label="From" type="date" value={filters.from} max={filters.to || undefined} onChange={(e) => update({ from: e.target.value })} className="w-40" />
        <Input label="To" type="date" value={filters.to} min={filters.from || undefined} onChange={(e) => update({ to: e.target.value })} className="w-40" />
        <Select label="Type" value={filters.type} onChange={(e) => update({ type: e.target.value as TransactionType | '' })} className="w-44">
          <option value="">All types</option>
          {Object.entries(TRANSACTION_LABEL).map(([value, label]) => <option key={value} value={value}>{label}</option>)}
        </Select>
        <Select label="Status" value={filters.status} onChange={(e) => update({ status: e.target.value as HistoryFilters['status'] })} className="w-36">
          <option value="">All</option>
          <option value="SUCCESS">Completed</option>
          <option value="FAILED">Declined</option>
        </Select>
        <div className="ml-auto flex gap-2">
          <Button variant="secondary" onClick={() => download('PDF')} loading={downloading === 'PDF'}>
            <Download className="size-4" aria-hidden="true" /> PDF
          </Button>
          <Button variant="secondary" onClick={() => download('CSV')} loading={downloading === 'CSV'}>
            <Download className="size-4" aria-hidden="true" /> CSV
          </Button>
        </div>
      </div>
      {history.isPending ? (
        <LoadingBlock label="Printing entries" />
      ) : history.isError ? (
        <EmptyState title="Could not load transactions" description={errorMessage(history.error)} />
      ) : history.data.content.length === 0 ? (
        <EmptyState title="No entries match these filters" description="Widen the date range or clear the type filter." />
      ) : (
        <>
          <Passbook rows={history.data.content} />
          <Pagination page={history.data} onChange={(page) => update({ page })} />
        </>
      )}
      <p className="border-t border-rule px-5 py-3 text-xs text-ink-faint">
        Statements list completed entries only and default to the last 30 days; declined attempts appear here for your reference.
      </p>
    </Card>
  );
}
