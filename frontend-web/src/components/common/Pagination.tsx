import { ChevronLeft, ChevronRight } from 'lucide-react'
import { useId, type ReactNode } from 'react'
import { PAGE_SIZE_OPTIONS } from '../../hooks/usePagination'
import type { PageResponse } from '../../types'
import { cn } from '../../utils/cn'
import { formatNumber } from '../../utils/format'

interface PaginationProps {
  pageData: Pick<PageResponse<unknown>, 'page' | 'size' | 'totalElements' | 'totalPages'>
  onPageChange: (page: number) => void
  onSizeChange: (size: number) => void
  isDisabled?: boolean
}

type PageItem = number | 'gap'

/** First, last, current and its neighbours; the rest collapse into an ellipsis. */
function buildPageItems(current: number, total: number): PageItem[] {
  const pages = new Set([0, total - 1, current - 1, current, current + 1])
  const sorted = [...pages].filter((page) => page >= 0 && page < total).sort((a, b) => a - b)
  return sorted.flatMap((page, index): PageItem[] =>
    index > 0 && page - sorted[index - 1] > 1 ? ['gap', page] : [page],
  )
}

export function Pagination({ pageData, onPageChange, onSizeChange, isDisabled = false }: PaginationProps) {
  const sizeSelectId = useId()
  const { page, size, totalElements, totalPages } = pageData
  if (totalElements === 0) return null

  const firstItem = page * size + 1
  const lastItem = Math.min((page + 1) * size, totalElements)
  const pageItems = buildPageItems(page, totalPages)

  return (
    <nav
      aria-label="Paginación"
      className="flex flex-col items-center justify-between gap-4 border-t border-slate-200 pt-4 md:flex-row"
    >
      <p className="text-sm text-slate-600" aria-live="polite">
        Mostrando <span className="font-semibold text-slate-900">{formatNumber(firstItem)}</span>–
        <span className="font-semibold text-slate-900">{formatNumber(lastItem)}</span> de{' '}
        <span className="font-semibold text-slate-900">{formatNumber(totalElements)}</span> resultados
      </p>

      <div className="flex flex-wrap items-center justify-center gap-4">
        <div className="flex items-center gap-2 text-sm text-slate-600">
          <label htmlFor={sizeSelectId}>Por página</label>
          <select
            id={sizeSelectId}
            value={size}
            disabled={isDisabled}
            onChange={(event) => onSizeChange(Number(event.target.value))}
            className="h-9 rounded-lg border border-slate-300 bg-white px-2 text-sm focus:border-brand-500 focus:ring-2 focus:ring-brand-200 focus:outline-none"
          >
            {PAGE_SIZE_OPTIONS.map((option) => (
              <option key={option} value={option}>
                {option}
              </option>
            ))}
          </select>
        </div>

        <ul className="flex items-center gap-1">
          <li>
            <PageButton label="Página anterior" disabled={isDisabled || page === 0} onClick={() => onPageChange(page - 1)}>
              <ChevronLeft className="size-4" />
            </PageButton>
          </li>
          {pageItems.map((item, index) =>
            item === 'gap' ? (
              <li key={`gap-${index}`} className="px-1 text-slate-400" aria-hidden="true">
                …
              </li>
            ) : (
              <li key={item} className="hidden sm:block">
                <PageButton
                  label={`Página ${item + 1}`}
                  isCurrent={item === page}
                  disabled={isDisabled}
                  onClick={() => onPageChange(item)}
                >
                  {item + 1}
                </PageButton>
              </li>
            ),
          )}
          <li className="px-2 text-sm text-slate-600 sm:hidden">
            {page + 1} / {totalPages}
          </li>
          <li>
            <PageButton
              label="Página siguiente"
              disabled={isDisabled || page >= totalPages - 1}
              onClick={() => onPageChange(page + 1)}
            >
              <ChevronRight className="size-4" />
            </PageButton>
          </li>
        </ul>
      </div>
    </nav>
  )
}

interface PageButtonProps {
  label: string
  onClick: () => void
  disabled?: boolean
  isCurrent?: boolean
  children: ReactNode
}

function PageButton({ label, onClick, disabled, isCurrent = false, children }: PageButtonProps) {
  return (
    <button
      type="button"
      aria-label={label}
      aria-current={isCurrent ? 'page' : undefined}
      disabled={disabled || isCurrent}
      onClick={onClick}
      className={cn(
        'flex size-9 items-center justify-center rounded-lg text-sm font-medium transition-colors',
        isCurrent
          ? 'bg-brand-500 text-slate-900'
          : 'text-slate-600 hover:bg-slate-100 disabled:cursor-not-allowed disabled:opacity-40',
      )}
    >
      {children}
    </button>
  )
}
