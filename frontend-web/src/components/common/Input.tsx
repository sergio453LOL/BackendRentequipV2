import { useId, type ComponentPropsWithRef, type ReactNode } from 'react'
import { cn } from '../../utils/cn'
import { fieldClasses } from './fieldClasses'
import { FieldShell } from './FieldShell'

interface InputProps extends ComponentPropsWithRef<'input'> {
  label: string
  error?: string
  hint?: string
  leftIcon?: ReactNode
}

export function Input({ label, error, hint, leftIcon, id, className, required, ...rest }: InputProps) {
  const generatedId = useId()
  const inputId = id ?? generatedId
  const messageId = `${inputId}-message`

  return (
    <FieldShell
      inputId={inputId}
      messageId={messageId}
      label={label}
      required={required}
      error={error}
      hint={hint}
      className={className}
    >
      <div className="relative">
        {leftIcon != null && (
          <span className="pointer-events-none absolute inset-y-0 left-3 flex items-center text-slate-400">
            {leftIcon}
          </span>
        )}
        <input
          id={inputId}
          required={required}
          aria-invalid={error ? true : undefined}
          aria-describedby={error || hint ? messageId : undefined}
          className={cn(fieldClasses(Boolean(error)), 'h-10', leftIcon != null && 'pl-10')}
          {...rest}
        />
      </div>
    </FieldShell>
  )
}

