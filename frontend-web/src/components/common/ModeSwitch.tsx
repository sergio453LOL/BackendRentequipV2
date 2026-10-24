import { motion } from 'framer-motion'
import { useId } from 'react'
import { cn } from '../../utils/cn'
import { MODE_LABELS, type WorkspaceMode } from '../../utils/workspaceMode'

interface ModeSwitchProps {
  mode: WorkspaceMode
  onChange: (mode: WorkspaceMode) => void
  tone?: 'light' | 'dark'
  className?: string
}

/**
 * Sliding tab between the two perspectives: renting machines in (arrendatario / comprador) and renting
 * the fleet out (arrendador / vendedor). The amber pill slides to the active side.
 */
export function ModeSwitch({ mode, onChange, tone = 'light', className }: ModeSwitchProps) {
  // One indicator per switch instance, so the header and the phone menu animate independently.
  const indicatorId = useId()
  return (
    <div
      role="radiogroup"
      aria-label="Perspectiva"
      className={cn('relative grid grid-cols-2 rounded-xl p-1', tone === 'dark' ? 'bg-white/10' : 'bg-slate-100 ring-1 ring-slate-200/80 ring-inset', className)}
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
              'relative flex h-10 min-w-0 flex-col items-center justify-center rounded-lg px-3 leading-tight transition-colors duration-200',
              isActive ? 'text-ink' : tone === 'dark' ? 'text-slate-300 hover:text-white' : 'text-slate-500 hover:text-ink',
            )}
          >
            {isActive && (
              <motion.span
                layoutId={indicatorId}
                className="absolute inset-0 rounded-lg bg-brand shadow-[0_4px_14px_-4px_rgba(245,158,11,0.7)]"
                transition={{ type: 'spring', stiffness: 420, damping: 34 }}
              />
            )}
            <span className="relative truncate text-[13px] font-bold">{MODE_LABELS[option].title}</span>
            <span className={cn('relative truncate text-[11px] font-medium', isActive ? 'text-ink/70' : 'opacity-80')}>
              {MODE_LABELS[option].caption}
            </span>
          </button>
        )
      })}
    </div>
  )
}
