import {
  Bar,
  BarChart,
  CartesianGrid,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';
import { formatDayShort, formatINR, formatINRCompact, formatMonth } from '@/lib/format';

/*
 * Chart palette - validated with the dataviz skill's validator (light surface): lightness band,
 * chroma floor, CVD separation, normal-vision floor and 3:1 contrast all pass. Violet stays
 * reserved for status stamps; red/green stay reserved for debit/credit figures in text.
 */
const SERIES = {
  one: '#00917a',
  two: '#c2650f',
  three: '#4468d6',
} as const;

const GRID = '#e3eae6';
const AXIS_TEXT = '#7b8c8b';

function Legend({ items }: { items: { label: string; color: string }[] }) {
  return (
    <ul className="flex flex-wrap gap-x-5 gap-y-1 text-sm text-ink-soft">
      {items.map((item) => (
        <li key={item.label} className="flex items-center gap-2">
          <span className="size-2.5 rounded-[3px]" style={{ background: item.color }} aria-hidden="true" />
          {item.label}
        </li>
      ))}
    </ul>
  );
}

function TooltipCard({ title, rows }: { title: string; rows: { label: string; value: string; color: string }[] }) {
  return (
    <div className="rounded-lg border border-rule bg-surface px-3 py-2 text-sm shadow-[var(--shadow-lift)]">
      <p className="mb-1 font-medium text-ink">{title}</p>
      {rows.map((row) => (
        <p key={row.label} className="flex items-center gap-2 text-ink-soft">
          <span className="size-2 rounded-full" style={{ background: row.color }} aria-hidden="true" />
          <span>{row.label}</span>
          <span className="figures ml-auto pl-4 font-medium text-ink">{row.value}</span>
        </p>
      ))}
    </div>
  );
}

// ---------------------------------------------------------------- customer: money in vs out

export function CashflowChart({ data }: { data: { month: string; income: number; spending: number }[] }) {
  return (
    <figure>
      <Legend items={[{ label: 'Money in', color: SERIES.one }, { label: 'Money out', color: SERIES.two }]} />
      <div className="mt-4 h-60" aria-hidden="true">
        <ResponsiveContainer width="100%" height="100%">
          <BarChart data={data} barGap={2} barCategoryGap="28%" margin={{ top: 4, right: 4, left: 0, bottom: 0 }}>
            <CartesianGrid vertical={false} stroke={GRID} />
            <XAxis dataKey="month" tickFormatter={formatMonth} tickLine={false} axisLine={{ stroke: GRID }} tick={{ fill: AXIS_TEXT, fontSize: 12 }} />
            <YAxis tickFormatter={(v: number) => formatINRCompact(v)} tickLine={false} axisLine={false} width={64} tick={{ fill: AXIS_TEXT, fontSize: 12 }} />
            <Tooltip
              cursor={{ fill: '#eef2ef' }}
              content={(props) =>
                props.active && props.payload?.length ? (
                  <TooltipCard
                    title={formatMonth(String(props.label))}
                    rows={[
                      { label: 'Money in', value: formatINR(Number(props.payload[0]?.value ?? 0)), color: SERIES.one },
                      { label: 'Money out', value: formatINR(Number(props.payload[1]?.value ?? 0)), color: SERIES.two },
                    ]}
                  />
                ) : null
              }
            />
            <Bar dataKey="income" name="Money in" fill={SERIES.one} maxBarSize={24} radius={[4, 4, 0, 0]} />
            <Bar dataKey="spending" name="Money out" fill={SERIES.two} maxBarSize={24} radius={[4, 4, 0, 0]} />
          </BarChart>
        </ResponsiveContainer>
      </div>
      <table className="sr-only">
        <caption>Money in and out by month</caption>
        <thead><tr><th>Month</th><th>Money in</th><th>Money out</th></tr></thead>
        <tbody>
          {data.map((d) => (
            <tr key={d.month}><td>{formatMonth(d.month)}</td><td>{formatINR(d.income)}</td><td>{formatINR(d.spending)}</td></tr>
          ))}
        </tbody>
      </table>
    </figure>
  );
}

// ---------------------------------------------------------------- admin: daily volume

export function VolumeChart({ data }: { data: { date: string; credits: number; debits: number; transactions: number }[] }) {
  return (
    <figure>
      <Legend items={[{ label: 'Credits', color: SERIES.one }, { label: 'Debits', color: SERIES.two }]} />
      <div className="mt-4 h-64" aria-hidden="true">
        <ResponsiveContainer width="100%" height="100%">
          <BarChart data={data} barGap={1} barCategoryGap="20%" margin={{ top: 8, right: 12, left: 0, bottom: 0 }}>
            <CartesianGrid vertical={false} stroke={GRID} />
            <XAxis dataKey="date" tickFormatter={formatDayShort} minTickGap={28} tickLine={false} axisLine={{ stroke: GRID }} tick={{ fill: AXIS_TEXT, fontSize: 12 }} />
            <YAxis tickFormatter={(v: number) => formatINRCompact(v)} tickLine={false} axisLine={false} width={64} tick={{ fill: AXIS_TEXT, fontSize: 12 }} />
            <Tooltip
              cursor={{ fill: '#eef2ef' }}
              content={(props) => {
                if (!props.active || !props.payload?.length) return null;
                const point = props.payload[0]?.payload as { transactions: number } | undefined;
                return (
                  <TooltipCard
                    title={`${formatDayShort(String(props.label))} · ${point?.transactions ?? 0} entries`}
                    rows={[
                      { label: 'Credits', value: formatINR(Number(props.payload[0]?.value ?? 0)), color: SERIES.one },
                      { label: 'Debits', value: formatINR(Number(props.payload[1]?.value ?? 0)), color: SERIES.two },
                    ]}
                  />
                );
              }}
            />
            <Bar dataKey="credits" name="Credits" fill={SERIES.one} maxBarSize={10} radius={[2, 2, 0, 0]} />
            <Bar dataKey="debits" name="Debits" fill={SERIES.two} maxBarSize={10} radius={[2, 2, 0, 0]} />
          </BarChart>
        </ResponsiveContainer>
      </div>
      <table className="sr-only">
        <caption>Daily credits and debits</caption>
        <thead><tr><th>Date</th><th>Credits</th><th>Debits</th><th>Entries</th></tr></thead>
        <tbody>
          {data.map((d) => (
            <tr key={d.date}><td>{d.date}</td><td>{formatINR(d.credits)}</td><td>{formatINR(d.debits)}</td><td>{d.transactions}</td></tr>
          ))}
        </tbody>
      </table>
    </figure>
  );
}

// ---------------------------------------------------------------- single-series magnitude bars

/**
 * Ranked horizontal bars for one measure (spending by category, deposits by type). One series,
 * one hue; labels and values are text, not bar colour. Plain HTML: no chart library needed.
 */
export function BarList({ items, caption }: { items: { label: string; value: number; note?: string }[]; caption: string }) {
  const max = Math.max(1, ...items.map((i) => i.value));
  return (
    <table className="w-full text-sm">
      <caption className="sr-only">{caption}</caption>
      <tbody>
        {items.map((item) => (
          <tr key={item.label} className="group">
            <th scope="row" className="w-2/5 py-2 pr-3 text-left font-normal text-ink">
              {item.label}
              {item.note && <span className="block text-xs text-ink-faint">{item.note}</span>}
            </th>
            <td className="py-2">
              <div className="h-2.5 rounded-r-[4px] rounded-l-[1px] bg-[#00917a] transition-opacity group-hover:opacity-80" style={{ width: item.value > 0 ? `${Math.max(2, (item.value / max) * 100)}%` : 0 }} />
            </td>
            <td className="figures w-28 py-2 pl-3 text-right font-medium text-ink">{formatINR(item.value)}</td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}
