import { cn } from '../../utils/cn'
import { MODE_LABELS, type WorkspaceMode } from '../../utils/workspaceMode'

interface ModeSwitchProps {
  mode: WorkspaceMode
  onChange: (mode: WorkspaceMode) => void
  tone?: 'light' | 'dark'
  className?: string
}

/** Segmented switch between renting machines in (arrendatario) and renting the fleet out (arrendador). */
export function ModeSwitch({ mode, onChange, tone = 'light', className }: ModeSwitchProps) {
  return (
    <div
      role="radiogroup"
      aria-label="Modo de trabajo"
      className={cn('grid grid-cols-2 gap-1 rounded-lg p-1', tone === 'dark' ? 'bg-white/10' : 'bg-slate-100', className)}
    >
      {(['renter', 'owner'] as const).map((option) => {
        const isActive = mode === option
        return (
          <button
            key={option}
            type="button"
            role="radio"
            aria-checked={isActive}
            onClick={() => !isActive && onChange(option)}
            className={cn(
              'flex h-9 min-w-0 flex-col items-center justify-center rounded-md px-3 text-[13px] leading-4 font-semibold transition-all duration-200',
              isActive
                ? 'bg-brand text-ink shadow-sm'
                : tone === 'dark'
                  ? 'text-slate-300 hover:bg-white/10 hover:text-white'
                  : 'text-slate-600 hover:bg-white hover:text-ink',
            )}
          >
            <span className="truncate">{MODE_LABELS[option].title}</span>
          </button>
        )
      })}
    </div>
  )
}
