import { X } from 'lucide-react';
import { useEffect, useRef, type ReactNode } from 'react';
import { cn } from '@/lib/util';

/**
 * Native <dialog> with showModal(): the browser supplies the focus trap, Escape to close, inert
 * background and top-layer stacking, so there is no hand-rolled accessibility to get wrong.
 */
export function Dialog({ open, onClose, title, description, children, wide = false }: {
  open: boolean;
  onClose: () => void;
  title: string;
  description?: ReactNode;
  children: ReactNode;
  wide?: boolean;
}) {
  const ref = useRef<HTMLDialogElement>(null);

  useEffect(() => {
    const dialog = ref.current;
    if (!dialog) return;
    if (open && !dialog.open) dialog.showModal();
    if (!open && dialog.open) dialog.close();
  }, [open]);

  return (
    <dialog
      ref={ref}
      onClose={onClose}
      onClick={(event) => {
        if (event.target === ref.current) onClose();
      }}
      className={cn(
        'm-auto w-[calc(100%-2rem)] rounded-[var(--radius-card)] border border-rule bg-surface p-0 text-ink shadow-[var(--shadow-lift)] backdrop:bg-kosh-900/40',
        wide ? 'max-w-2xl' : 'max-w-lg',
      )}
    >
      {open && (
        <div className="flex max-h-[85dvh] flex-col">
          <div className="flex items-start justify-between gap-4 border-b border-rule px-5 py-4">
            <div>
              <h2 className="text-lg font-semibold">{title}</h2>
              {description && <p className="mt-0.5 text-sm text-ink-soft">{description}</p>}
            </div>
            <button type="button" onClick={onClose} className="rounded-md p-1 text-ink-soft hover:bg-paper hover:text-ink" aria-label="Close">
              <X className="size-5" />
            </button>
          </div>
          <div className="overflow-y-auto px-5 py-5">{children}</div>
        </div>
      )}
    </dialog>
  );
}
