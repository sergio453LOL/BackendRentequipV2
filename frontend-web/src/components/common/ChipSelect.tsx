import { Check } from 'lucide-react'
import { useId } from 'react'
import { cn } from '../../utils/cn'

export interface ChipOption {
  value: number
  label: string
}

interface ChipSelectProps {
  label: string
  options: ChipOption[]
  value: number[]
  onChange: (value: number[]) => void
  error?: string
  hint?: string
  required?: boolean
}

/** Multi-select rendered as toggle chips, for short option lists such as categories. */
export function ChipSelect({ label, options, value, onChange, error, hint, required }: ChipSelectProps) {
  const groupId = useId()
  const message = error ?? hint

  function toggle(option: number) {
    onChange(value.includes(option) ? value.filter((item) => item !== option) : [...value, option])
  }

  return (
    <fieldset className="flex flex-col gap-2" aria-describedby={message ? `${groupId}-message` : undefined}>
      <legend className="mb-1.5 text-sm font-medium text-slate-700">
        {label}
        {required && (
          <span className="ml-0.5 text-red-600" aria-hidden="true">
            *
          </span>
        )}
      </legend>
      <div className="flex flex-wrap gap-2">
        {options.map((option) => {
          const isSelected = value.includes(option.value)
          return (
            <button
              key={option.value}
              type="button"
              role="checkbox"
              aria-checked={isSelected}
              onClick={() => toggle(option.value)}
              className={cn(
                'flex items-center gap-1.5 rounded-full border px-3 py-1.5 text-sm font-medium transition',
                isSelected
                  ? 'border-ink bg-active text-ink'
                  : 'border-slate-300 bg-white text-slate-600 hover:border-slate-400',
              )}
            >
              {isSelected && <Check className="size-3.5" aria-hidden="true" />}
              {option.label}
            </button>
          )
        })}
      </div>
      {message && (
        <p id={`${groupId}-message`} className={cn('text-xs', error ? 'text-red-600' : 'text-slate-500')}>
          {message}
        </p>
      )}
    </fieldset>
  )
}
