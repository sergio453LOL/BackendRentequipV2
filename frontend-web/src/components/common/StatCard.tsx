import type { LucideIcon } from 'lucide-react'
import type { ReactNode } from 'react'
import { cn } from '../../utils/cn'

interface StatCardProps {
  label: string
  value: string
  icon: LucideIcon
  hint?: string
  tone?: 'brand' | 'blue' | 'green' | 'slate'
  action?: ReactNode
}

const TONE_CLASSES = {
  brand: 'bg-brand-100 text-brand-700',
  blue: 'bg-sky-100 text-sky-700',
  green: 'bg-emerald-100 text-emerald-700',
  slate: 'bg-slate-100 text-slate-700',
} as const

export function StatCard({ label, value, icon: Icon, hint, tone = 'brand', action }: StatCardProps) {
  return (
    <article className="flex flex-col gap-4 rounded-2xl border border-slate-200 bg-white p-5 shadow-sm transition-shadow hover:shadow-md">
      <div className="flex items-start justify-between gap-3">
        <p className="text-sm font-medium text-slate-500">{label}</p>
        <span className={cn('flex size-10 shrink-0 items-center justify-center rounded-xl', TONE_CLASSES[tone])}>
          <Icon className="size-5" aria-hidden="true" />
        </span>
      </div>
      <p className="text-3xl font-bold tracking-tight text-slate-900">{value}</p>
      {(hint || action) && (
        <div className="flex items-center justify-between gap-2 text-xs text-slate-500">
          <span>{hint}</span>
          {action}
        </div>
      )}
    </article>
  )
}
