import { cn } from '../../utils/cn'

/** Base look of every text-like form control, with the red variant for invalid fields. */
export function fieldClasses(hasError: boolean): string {
  return cn(
    'w-full rounded-md border bg-white px-3 text-sm text-ink transition',
    'placeholder:text-slate-500 focus:outline-none focus:ring-2',
    'disabled:cursor-not-allowed disabled:bg-raised disabled:text-slate-600',
    hasError
      ? 'border-red-400 focus:border-red-500 focus:ring-red-200'
      : 'border-slate-300 focus:border-ink focus:ring-slate-200',
  )
}
