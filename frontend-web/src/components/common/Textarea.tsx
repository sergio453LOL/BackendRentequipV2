import { useId, type ComponentPropsWithRef } from 'react'
import { cn } from '../../utils/cn'
import { FieldShell } from './FieldShell'
import { fieldClasses } from './fieldClasses'

interface TextareaProps extends ComponentPropsWithRef<'textarea'> {
  label: string
  error?: string
  hint?: string
}

export function Textarea({ label, error, hint, id, className, required, rows = 3, ...rest }: TextareaProps) {
  const generatedId = useId()
  const textareaId = id ?? generatedId
  const messageId = `${textareaId}-message`

  return (
    <FieldShell
      inputId={textareaId}
      messageId={messageId}
      label={label}
      required={required}
      error={error}
      hint={hint}
      className={className}
    >
      <textarea
        id={textareaId}
        rows={rows}
        required={required}
        aria-invalid={error ? true : undefined}
        aria-describedby={error || hint ? messageId : undefined}
        className={cn(fieldClasses(Boolean(error)), 'resize-y py-2')}
        {...rest}
      />
    </FieldShell>
  )
}
