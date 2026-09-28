import { useState } from 'react';
import { useAuditLogs, type AuditFilters } from '@/api/admin';
import { Card, EmptyState, PageHeader } from '@/components/ui/Card';
import { Input, Select } from '@/components/ui/Field';
import { Code } from '@/components/ui/Money';
import { Pagination } from '@/components/ui/Pagination';
import { LoadingBlock } from '@/components/ui/Spinner';
import { Tag } from '@/components/ui/Status';
import { formatDateTime, titleCase } from '@/lib/format';
import { useDebounced } from '@/lib/useDebounced';
import type { AuditOutcome } from '@/types/api';

const ACTIONS = [
  'LOGIN', 'LOGOUT', 'TOKEN_REUSE_DETECTED', 'PASSWORD_RESET_REQUESTED', 'PASSWORD_RESET', 'PASSWORD_CHANGED',
  'CUSTOMER_REGISTERED', 'CUSTOMER_ONBOARDED', 'PROFILE_UPDATED', 'KYC_STATUS_CHANGED', 'ACCOUNT_OPENED',
  'ACCOUNT_STATUS_CHANGED', 'TRANSFER', 'CASH_DEPOSIT', 'CASH_WITHDRAWAL', 'FD_OPENED', 'BENEFICIARY_ADDED',
  'BENEFICIARY_DELETED', 'LOAN_APPLIED', 'LOAN_APPROVED', 'LOAN_REJECTED', 'EMPLOYEE_CREATED', 'EMPLOYEE_UPDATED',
  'USER_UNLOCKED', 'BRANCH_CREATED', 'BRANCH_UPDATED', 'RATE_CHANGED', 'JOB_TRIGGERED',
];

export default function AuditLogPage() {
  const [filters, setFilters] = useState<AuditFilters>({ action: '', outcome: '', actor: '', page: 0 });
  const actor = useDebounced(filters.actor, 300);
  const logs = useAuditLogs({ ...filters, actor });
  const update = (patch: Partial<AuditFilters>) => setFilters((f) => ({ ...f, ...patch, page: patch.page ?? 0 }));

  return (
    <>
      <PageHeader title="Audit log" description="Every sensitive action, who did it, from where, and whether it succeeded. Entries cannot be edited or deleted." />
      <Card>
        <div className="flex flex-wrap items-end gap-3 border-b border-rule px-5 py-4">
          <Select label="Action" value={filters.action} onChange={(e) => update({ action: e.target.value })} className="w-60">
            <option value="">All actions</option>
            {ACTIONS.map((a) => <option key={a} value={a}>{titleCase(a)}</option>)}
          </Select>
          <Select label="Outcome" value={filters.outcome} onChange={(e) => update({ outcome: e.target.value as AuditOutcome | '' })} className="w-40">
            <option value="">Any</option>
            <option value="SUCCESS">Succeeded</option>
            <option value="FAILURE">Failed</option>
          </Select>
          <Input label="Who" placeholder="Email contains" value={filters.actor} onChange={(e) => update({ actor: e.target.value })} className="w-60" />
        </div>
        {logs.isPending ? <LoadingBlock /> : !logs.data?.content.length ? <EmptyState title="No matching entries" /> : (
          <>
            <div className="overflow-x-auto">
              <table className="w-full min-w-[860px] text-sm">
                <thead>
                  <tr className="border-b border-rule text-left text-xs uppercase tracking-[0.08em] text-ink-faint">
                    <th scope="col" className="py-2.5 pl-5 pr-3 font-medium">When</th>
                    <th scope="col" className="px-3 py-2.5 font-medium">Who</th>
                    <th scope="col" className="px-3 py-2.5 font-medium">Action</th>
                    <th scope="col" className="px-3 py-2.5 font-medium">On</th>
                    <th scope="col" className="px-3 py-2.5 font-medium">Details</th>
                    <th scope="col" className="py-2.5 pl-3 pr-5 font-medium">From</th>
                  </tr>
                </thead>
                <tbody>
                  {logs.data.content.map((log) => (
                    <tr key={log.id} className="ledger-row align-top">
                      <td className="whitespace-nowrap py-3 pl-5 pr-3 text-ink-soft">{formatDateTime(log.createdAt)}</td>
                      <td className="px-3 py-3">
                        <p className="max-w-[14rem] truncate">{log.actorEmail ?? 'System'}</p>
                        {log.actorRole && <p className="text-xs text-ink-faint">{titleCase(log.actorRole)}</p>}
                      </td>
                      <td className="px-3 py-3">
                        <div className="flex flex-col items-start gap-1">
                          <span className="font-medium">{titleCase(log.action)}</span>
                          {log.outcome === 'FAILURE' && <Tag tone="debit">Failed</Tag>}
                        </div>
                      </td>
                      <td className="px-3 py-3 text-ink-soft">{log.entityType ? <>{titleCase(log.entityType)} <Code>{log.entityId ?? ''}</Code></> : '-'}</td>
                      <td className="max-w-[22rem] px-3 py-3 text-ink-soft">{log.details ?? '-'}</td>
                      <td className="py-3 pl-3 pr-5"><Code className="text-ink-faint">{log.ipAddress ?? '-'}</Code></td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <Pagination page={logs.data} onChange={(page) => update({ page })} />
          </>
        )}
      </Card>
    </>
  );
}
