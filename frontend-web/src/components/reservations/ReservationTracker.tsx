import { motion } from 'framer-motion'
import { Check, X } from 'lucide-react'
import type { Reservation } from '../../types'
import { cn } from '../../utils/cn'

type StepState = 'done' | 'current' | 'todo' | 'stopped'

const STEPS = ['Solicitada', 'Pagada', 'En obra', 'Devolución'] as const

/** Where a reservation stands on its way from the request to the machine coming back. */
function stepStates(reservation: Reservation): StepState[] {
  const paid = reservation.paymentStatus === 'PAID' || reservation.paymentStatus === 'REFUNDED'
  const { status } = reservation
  const states: StepState[] = [
    'done',
    paid ? 'done' : 'current',
    status === 'COMPLETED' ? 'done' : status === 'IN_PROGRESS' ? 'current' : 'todo',
    status === 'COMPLETED' ? 'done' : 'todo',
  ]
  if (status === 'CANCELLED' || status === 'REJECTED') {
    return states.map((state) => (state === 'done' ? 'done' : 'stopped'))
  }
  return states
}

/** Four-step tracker: Solicitada ➔ Pagada ➔ En obra ➔ Devolución, with the line filling up to the last step done. */
export function ReservationTracker({ reservation }: { reservation: Reservation }) {
  const states = stepStates(reservation)
  const lastDone = states.lastIndexOf('done')
  const progress = lastDone / (STEPS.length - 1)
  const stopped = states.includes('stopped')

  return (
    <div className="mt-4" aria-label="Seguimiento de la reserva">
      <div className="relative mx-9">
        <div className="absolute top-3 right-0 left-0 h-0.5 rounded-full bg-slate-200" aria-hidden="true" />
        <motion.div
          className={cn('absolute top-3 left-0 h-0.5 rounded-full', stopped ? 'bg-slate-400' : 'bg-status-green')}
          initial={{ width: 0 }}
          whileInView={{ width: `${progress * 100}%` }}
          viewport={{ once: true }}
          transition={{ duration: 0.9, ease: [0.16, 1, 0.3, 1] }}
          aria-hidden="true"
        />
        <ol className="relative flex justify-between">
          {STEPS.map((label, index) => {
            const state = states[index]
            return (
              <li key={label} className="flex w-0 flex-col items-center" aria-current={state === 'current' ? 'step' : undefined}>
                <span
                  className={cn(
                    'flex size-6 items-center justify-center rounded-full ring-4 ring-white transition-colors',
                    state === 'done' && 'bg-status-green text-white',
                    state === 'current' && 'bg-brand text-ink',
                    state === 'todo' && 'border-2 border-slate-300 bg-white',
                    state === 'stopped' && 'bg-slate-200 text-slate-500',
                  )}
                >
                  {state === 'done' && <Check className="size-3.5" strokeWidth={3} aria-hidden="true" />}
                  {state === 'current' && <span className="size-2 animate-pulse rounded-full bg-ink motion-reduce:animate-none" aria-hidden="true" />}
                  {state === 'stopped' && <X className="size-3.5" strokeWidth={3} aria-hidden="true" />}
                </span>
                <span
                  className={cn(
                    'mt-1.5 text-center text-xs font-semibold whitespace-nowrap',
                    state === 'done' || state === 'current' ? 'text-ink' : 'text-slate-400',
                  )}
                >
                  {label}
                  <span className="sr-only">
                    {state === 'done' ? ' (completado)' : state === 'current' ? ' (en curso)' : state === 'stopped' ? ' (no se realizará)' : ' (pendiente)'}
                  </span>
                </span>
              </li>
            )
          })}
        </ol>
      </div>
    </div>
  )
}
