import { cn } from '../../utils/cn'

/** Base look of every text-like form control, with the red variant for invalid fields. */
export function fieldClasses(hasError: boolean): string {
  return cn(
    'w-full rounded-lg border bg-white px-3 text-sm text-slate-900 shadow-sm transition',
    'placeholder:text-slate-400 focus:outline-none focus:ring-2',
    'disabled:cursor-not-allowed disabled:bg-slate-100',
    hasError
      ? 'border-red-400 focus:border-red-500 focus:ring-red-200'
      : 'border-slate-300 focus:border-brand-500 focus:ring-brand-200',
  )
}
