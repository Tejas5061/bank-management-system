import { useState } from 'react';
import { useMarkNotifications, useNotifications } from '@/api/common';
import { Button } from '@/components/ui/Button';
import { Card, EmptyState, PageHeader } from '@/components/ui/Card';
import { Pagination } from '@/components/ui/Pagination';
import { LoadingBlock } from '@/components/ui/Spinner';
import { Tag } from '@/components/ui/Status';
import { Tabs } from '@/components/ui/Tabs';
import { formatDateTime, titleCase } from '@/lib/format';
import { cn } from '@/lib/util';

export default function NotificationsPage() {
  const [filter, setFilter] = useState<'all' | 'unread'>('all');
  const [page, setPage] = useState(0);
  const notifications = useNotifications(filter === 'unread', page);
  const mark = useMarkNotifications();

  return (
    <>
      <PageHeader
        title="Notifications"
        description="Alerts for money movements, security events, KYC and loans. Important ones are also emailed."
        action={<Button variant="secondary" onClick={() => mark.all.mutate()} loading={mark.all.isPending}>Mark all as read</Button>}
      />
      <div className="mb-4">
        <Tabs label="Filter notifications" value={filter} onChange={(f) => { setFilter(f); setPage(0); }}
          options={[{ value: 'all', label: 'All' }, { value: 'unread', label: 'Unread' }]} />
      </div>
      <Card>
        {notifications.isPending ? <LoadingBlock /> : !notifications.data?.content.length ? (
          <EmptyState title={filter === 'unread' ? 'You are all caught up' : 'No notifications yet'} />
        ) : (
          <>
            <ul>
              {notifications.data.content.map((n) => (
                <li key={n.id} className={cn('ledger-row flex gap-4 px-5 py-4', !n.read && 'bg-kosh-50/60')}>
                  <span className={cn('mt-2 size-2 shrink-0 rounded-full', n.read ? 'bg-transparent' : 'bg-stamp')} aria-hidden="true" />
                  <div className="min-w-0 flex-1">
                    <div className="flex flex-wrap items-center gap-2">
                      <p className="font-medium text-ink">{n.title}</p>
                      <Tag tone={n.type === 'SECURITY' ? 'caution' : 'neutral'}>{titleCase(n.type)}</Tag>
                    </div>
                    <p className="mt-0.5 text-sm text-ink-soft">{n.message}</p>
                    <p className="mt-1 text-xs text-ink-faint">{formatDateTime(n.createdAt)}</p>
                  </div>
                  {!n.read && (
                    <button type="button" onClick={() => mark.one.mutate(n.id)} className="h-fit text-sm font-medium text-kosh-600 hover:underline">
                      Mark read
                    </button>
                  )}
                </li>
              ))}
            </ul>
            <Pagination page={notifications.data} onChange={setPage} />
          </>
        )}
      </Card>
    </>
  );
}
