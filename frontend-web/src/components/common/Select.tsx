import { ChevronDown } from 'lucide-react'
import { useId, type ComponentPropsWithRef } from 'react'
import { cn } from '../../utils/cn'
import { FieldShell } from './FieldShell'
import { fieldClasses } from './fieldClasses'

export interface SelectOption {
  value: string
  label: string
}

interface SelectProps extends ComponentPropsWithRef<'select'> {
  label: string
  options: SelectOption[]
  placeholder?: string
  error?: string
  hint?: string
}

export function Select({ label, options, placeholder, error, hint, id, className, required, ...rest }: SelectProps) {
  const generatedId = useId()
  const selectId = id ?? generatedId
  const messageId = `${selectId}-message`

  return (
    <FieldShell
      inputId={selectId}
      messageId={messageId}
      label={label}
      required={required}
      error={error}
      hint={hint}
      className={className}
    >
      <div className="relative">
        <select
          id={selectId}
          required={required}
          aria-invalid={error ? true : undefined}
          aria-describedby={error || hint ? messageId : undefined}
          className={cn(fieldClasses(Boolean(error)), 'h-9 appearance-none pr-9')}
          {...rest}
        >
          {placeholder !== undefined && <option value="">{placeholder}</option>}
          {options.map((option) => (
            <option key={option.value} value={option.value}>
              {option.label}
            </option>
          ))}
        </select>
        <ChevronDown
          className="pointer-events-none absolute top-1/2 right-3 size-4 -translate-y-1/2 text-slate-400"
          aria-hidden="true"
        />
      </div>
    </FieldShell>
  )
}
