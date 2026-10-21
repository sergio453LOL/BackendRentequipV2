import type { LucideIcon } from 'lucide-react'
import type { ReactNode } from 'react'

interface EmptyStateProps {
  icon: LucideIcon
  title: string
  description: string
  /** Call to action that gets the user out of the empty state, e.g. a button or a link. */
  action?: ReactNode
}

export function EmptyState({ icon: Icon, title, description, action }: EmptyStateProps) {
  return (
    <div className="flex flex-col items-center gap-4 rounded-2xl border-2 border-dashed border-slate-200 bg-white px-6 py-12 text-center">
      <span className="flex size-14 items-center justify-center rounded-full bg-brand-50 text-brand-600">
        <Icon className="size-7" aria-hidden="true" />
      </span>
      <div className="max-w-sm space-y-1">
        <h3 className="text-base font-semibold text-slate-900">{title}</h3>
        <p className="text-sm text-slate-500">{description}</p>
      </div>
      {action}
    </div>
  )
}
