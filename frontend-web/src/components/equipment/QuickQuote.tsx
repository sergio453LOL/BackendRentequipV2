import { Minus, Plus } from 'lucide-react'
import { useState } from 'react'
import type { Equipment } from '../../types'
import { formatCurrency } from '../../utils/format'

const MIN_DAYS = 1
const MAX_DAYS = 90

/**
 * Rough total before asking for dates: days × daily rate plus the refundable deposit, the same way the
 * backend bills a reservation.
 */
export function QuickQuote({ equipment }: { equipment: Equipment }) {
  const [days, setDays] = useState(3)
  const rental = days * equipment.dailyRate
  const total = rental + equipment.securityDeposit
  const change = (delta: number) => setDays((current) => Math.min(MAX_DAYS, Math.max(MIN_DAYS, current + delta)))

  return (
    <section aria-labelledby="quote-title" className="rounded-lg bg-slate-50 p-4 ring-1 ring-slate-200 ring-inset">
      <div className="flex items-center justify-between gap-3">
        <h3 id="quote-title" className="text-sm font-bold text-ink">
          Cotizador rápido
        </h3>
        <div className="flex items-center rounded-md border border-slate-300 bg-white">
          <button
            type="button"
            onClick={() => change(-1)}
            disabled={days <= MIN_DAYS}
            aria-label="Un día menos"
            className="flex size-8 items-center justify-center text-slate-600 transition-colors hover:text-ink disabled:opacity-40"
          >
            <Minus className="size-4" />
          </button>
          <label htmlFor="quote-days" className="sr-only">
            Días de alquiler
          </label>
          <input
            id="quote-days"
            type="number"
            inputMode="numeric"
            min={MIN_DAYS}
            max={MAX_DAYS}
            value={days}
            onChange={(event) => {
              const value = Number(event.target.value)
              if (Number.isFinite(value)) setDays(Math.min(MAX_DAYS, Math.max(MIN_DAYS, Math.round(value))))
            }}
            className="num h-8 w-10 border-x border-slate-200 text-center text-sm font-semibold text-ink [appearance:textfield] focus:outline-none [&::-webkit-inner-spin-button]:appearance-none"
          />
          <button
            type="button"
            onClick={() => change(1)}
            disabled={days >= MAX_DAYS}
            aria-label="Un día más"
            className="flex size-8 items-center justify-center text-slate-600 transition-colors hover:text-ink disabled:opacity-40"
          >
            <Plus className="size-4" />
          </button>
        </div>
      </div>

      <dl className="mt-3 space-y-1.5 text-sm" aria-live="polite">
        <div className="flex justify-between gap-3">
          <dt className="text-slate-600">
            Alquiler ({days} {days === 1 ? 'día' : 'días'} × {formatCurrency(equipment.dailyRate, equipment.currency)})
          </dt>
          <dd className="num font-semibold text-ink">{formatCurrency(rental, equipment.currency)}</dd>
        </div>
        <div className="flex justify-between gap-3">
          <dt className="text-slate-600">Garantía reembolsable</dt>
          <dd className="num font-semibold text-ink">{formatCurrency(equipment.securityDeposit, equipment.currency)}</dd>
        </div>
        <div className="flex justify-between gap-3 border-t border-slate-200 pt-2">
          <dt className="font-bold text-ink">Total a pagar</dt>
          <dd className="num text-base font-bold text-ink">{formatCurrency(total, equipment.currency)}</dd>
        </div>
      </dl>
    </section>
  )
}
