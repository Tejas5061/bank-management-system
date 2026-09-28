import { ChevronLeft, ChevronRight } from 'lucide-react';
import type { Page } from '@/types/api';
import { Button } from './Button';

export function Pagination<T>({ page, onChange }: { page: Page<T>; onChange: (page: number) => void }) {
  if (page.totalPages <= 1) return null;
  const from = page.page * page.size + 1;
  const to = Math.min(page.totalElements, from + page.content.length - 1);
  return (
    <nav className="flex items-center justify-between gap-3 border-t border-rule px-5 py-3 text-sm text-ink-soft" aria-label="Pagination">
      <span className="figures">{from}–{to} of {page.totalElements}</span>
      <div className="flex gap-2">
        <Button variant="secondary" size="sm" disabled={page.first} onClick={() => onChange(page.page - 1)} aria-label="Previous page">
          <ChevronLeft className="size-4" /> Previous
        </Button>
        <Button variant="secondary" size="sm" disabled={page.last} onClick={() => onChange(page.page + 1)} aria-label="Next page">
          Next <ChevronRight className="size-4" />
        </Button>
      </div>
    </nav>
  );
}
