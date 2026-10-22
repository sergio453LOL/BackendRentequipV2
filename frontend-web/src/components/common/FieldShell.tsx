import type { ReactNode } from 'react'
import { cn } from '../../utils/cn'

interface FieldShellProps {
  inputId: string
  messageId: string
  label: string
  required?: boolean
  error?: string
  hint?: string
  className?: string
  children: ReactNode
}

/** Label, control and help/error message layout shared by every form field. */
export function FieldShell({ inputId, messageId, label, required, error, hint, className, children }: FieldShellProps) {
  const message = error ?? hint

  return (
    <div className={cn('flex flex-col gap-1.5', className)}>
      <label htmlFor={inputId} className="text-sm font-medium text-slate-700">
        {label}
        {required && (
          <span className="ml-0.5 text-red-600" aria-hidden="true">
            *
          </span>
        )}
      </label>
      {children}
      {message && (
        <p id={messageId} role={error ? 'alert' : undefined} className={cn('text-xs', error ? 'text-red-600' : 'text-slate-500')}>
          {message}
        </p>
      )}
    </div>
  )
}
