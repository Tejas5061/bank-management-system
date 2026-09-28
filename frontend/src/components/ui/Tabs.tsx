import { cn } from '@/lib/util';

export function Tabs<T extends string>({ value, onChange, options, label }: {
  value: T;
  onChange: (value: T) => void;
  options: { value: T; label: string }[];
  label: string;
}) {
  return (
    <div role="tablist" aria-label={label} className="inline-flex rounded-lg border border-rule bg-paper p-1">
      {options.map((option) => (
        <button
          key={option.value}
          type="button"
          role="tab"
          aria-selected={value === option.value}
          onClick={() => onChange(option.value)}
          className={cn(
            'rounded-md px-3.5 py-1.5 text-sm font-medium transition-colors',
            value === option.value ? 'bg-surface text-ink shadow-[var(--shadow-card)]' : 'text-ink-soft hover:text-ink',
          )}
        >
          {option.label}
        </button>
      ))}
    </div>
  );
}
