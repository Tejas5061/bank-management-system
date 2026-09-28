import { forwardRef, useId, type InputHTMLAttributes, type ReactNode, type SelectHTMLAttributes, type TextareaHTMLAttributes } from 'react';
import { cn } from '@/lib/util';

const control =
  'w-full rounded-lg border border-rule-strong bg-surface px-3 text-[15px] text-ink placeholder:text-ink-faint ' +
  'transition-colors focus:border-kosh-500 focus:outline-none focus:ring-3 focus:ring-kosh-500/15 ' +
  'disabled:bg-paper disabled:text-ink-faint aria-[invalid=true]:border-debit aria-[invalid=true]:focus:ring-debit/15';

interface Wrapper {
  label: string;
  error?: string;
  hint?: ReactNode;
  className?: string;
}

function FieldShell({ id, label, error, hint, className, children }: Wrapper & { id: string; children: ReactNode }) {
  return (
    <div className={cn('flex flex-col gap-1.5', className)}>
      <label htmlFor={id} className="text-sm font-medium text-ink">{label}</label>
      {children}
      {error ? (
        <p id={`${id}-error`} className="text-sm text-debit" role="alert">{error}</p>
      ) : hint ? (
        <p id={`${id}-hint`} className="text-xs text-ink-faint">{hint}</p>
      ) : null}
    </div>
  );
}

type InputProps = InputHTMLAttributes<HTMLInputElement> & Wrapper & { prefix?: string };

export const Input = forwardRef<HTMLInputElement, InputProps>(function Input(
  { label, error, hint, className, prefix, id, ...rest }, ref,
) {
  const generated = useId();
  const inputId = id ?? generated;
  return (
    <FieldShell id={inputId} label={label} error={error} hint={hint} className={className}>
      <div className="relative">
        {prefix && <span className="pointer-events-none absolute inset-y-0 left-3 flex items-center text-ink-soft">{prefix}</span>}
        <input
          ref={ref}
          id={inputId}
          aria-invalid={error ? true : undefined}
          aria-describedby={error ? `${inputId}-error` : hint ? `${inputId}-hint` : undefined}
          className={cn(control, 'h-11', prefix && 'pl-8')}
          {...rest}
        />
      </div>
    </FieldShell>
  );
});

type SelectProps = SelectHTMLAttributes<HTMLSelectElement> & Wrapper;

export const Select = forwardRef<HTMLSelectElement, SelectProps>(function Select(
  { label, error, hint, className, id, children, ...rest }, ref,
) {
  const generated = useId();
  const selectId = id ?? generated;
  return (
    <FieldShell id={selectId} label={label} error={error} hint={hint} className={className}>
      <select
        ref={ref}
        id={selectId}
        aria-invalid={error ? true : undefined}
        className={cn(control, 'h-11 appearance-none bg-[url("data:image/svg+xml,%3Csvg xmlns=%27http://www.w3.org/2000/svg%27 viewBox=%270 0 20 20%27 fill=%27%234a5f5f%27%3E%3Cpath d=%27M5.3 7.3a1 1 0 0 1 1.4 0L10 10.6l3.3-3.3a1 1 0 1 1 1.4 1.4l-4 4a1 1 0 0 1-1.4 0l-4-4a1 1 0 0 1 0-1.4z%27/%3E%3C/svg%3E")] bg-[length:18px] bg-[right_10px_center] bg-no-repeat pr-9')}
        {...rest}
      >
        {children}
      </select>
    </FieldShell>
  );
});

type TextareaProps = TextareaHTMLAttributes<HTMLTextAreaElement> & Wrapper;

export const Textarea = forwardRef<HTMLTextAreaElement, TextareaProps>(function Textarea(
  { label, error, hint, className, id, ...rest }, ref,
) {
  const generated = useId();
  const areaId = id ?? generated;
  return (
    <FieldShell id={areaId} label={label} error={error} hint={hint} className={className}>
      <textarea ref={ref} id={areaId} aria-invalid={error ? true : undefined} className={cn(control, 'min-h-24 py-2.5')} {...rest} />
    </FieldShell>
  );
});
