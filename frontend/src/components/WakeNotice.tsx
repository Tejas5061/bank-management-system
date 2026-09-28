import { Spinner } from '@/components/ui/Spinner';
import { useServerWake } from '@/lib/wakeup';
import { cn } from '@/lib/util';

/** Explains a slow first load on the public demo (see lib/wakeup). Renders nothing otherwise. */
export function WakeNotice({ className }: { className?: string }) {
  const state = useServerWake();
  if (state === 'awake') return null;

  return (
    <div role="status" className={cn('rounded-lg border border-rule bg-surface px-4 py-3 text-sm', className)}>
      <p className="flex items-center gap-2 font-medium text-ink">
        {state === 'waking' && <Spinner className="size-4 text-kosh-600" />}
        {state === 'waking' ? 'Waking up the demo server' : 'The demo server is taking longer than usual'}
      </p>
      <p className="mt-1 text-ink-soft">
        {state === 'waking'
          ? 'It runs on a free host that sleeps when nobody is using it, so the first visit takes a minute or two. After that, pages load quickly.'
          : 'Refresh the page in a minute or two.'}
      </p>
    </div>
  );
}
