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
    <div className="flex flex-col items-start gap-4 rounded-lg border border-dashed border-slate-300 px-6 py-8">
      <div className="flex max-w-xl gap-3">
        <Icon className="mt-0.5 size-5 shrink-0 text-slate-500" strokeWidth={1.75} aria-hidden="true" />
        <div className="space-y-1">
          <h3 className="text-[15px] font-semibold text-ink">{title}</h3>
          <p className="text-sm text-slate-600">{description}</p>
        </div>
      </div>
      {action}
    </div>
  )
}
