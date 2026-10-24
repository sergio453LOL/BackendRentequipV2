import { motion } from 'framer-motion'
import { cn } from '../../utils/cn'
import { formatCurrency } from '../../utils/format'
import type { MonthRevenue } from './ownerMetrics'

const PLOT_HEIGHT = 180

/** Round tick step so the axis reads 0 / 2,500 / 5,000… with the top tick above the highest bar. */
function niceStep(max: number): number {
  if (max <= 0) return 1000
  const raw = max / 4
  const magnitude = 10 ** Math.floor(Math.log10(raw))
  const step = [1, 2, 2.5, 5, 10].map((factor) => factor * magnitude).find((candidate) => candidate >= raw) ?? raw
  return step
}

/**
 * Income per month as columns: past months in slate, the current one in amber, months ahead (already
 * booked, not yet earned) hatched, so a forecast never reads as income already made.
 */
export function RevenueChart({ months, currency }: { months: MonthRevenue[]; currency: string }) {
  const max = Math.max(...months.map((item) => item.amount), 0)
  const step = niceStep(max)
  const top = (Math.floor(max / step) + 1) * step
  const ticks = Array.from({ length: Math.round(top / step) + 1 }, (_, index) => index * step)
  const short = (value: number) => formatCurrency(value, currency).replace(/\.00$/, '')

  return (
    <div className="pt-4">
      <div className="flex gap-3">
        {/* y axis */}
        <div className="relative w-16 shrink-0" style={{ height: PLOT_HEIGHT }} aria-hidden="true">
          {ticks.map((tick) => (
            <span key={tick} className="num absolute right-0 -translate-y-1/2 text-xs text-slate-500" style={{ bottom: `${(tick / top) * 100}%` }}>
              {short(tick)}
            </span>
          ))}
        </div>

        <div className="relative flex-1" style={{ height: PLOT_HEIGHT }}>
          {ticks.map((tick) => (
            <span
              key={tick}
              aria-hidden="true"
              className={cn('absolute right-0 left-0 border-t', tick === 0 ? 'border-slate-300' : 'border-dashed border-slate-200')}
              style={{ bottom: `${(tick / top) * 100}%` }}
            />
          ))}
          <ul className="relative flex h-full items-end justify-around gap-3">
            {months.map((item, index) => {
              const height = top > 0 ? (item.amount / top) * 100 : 0
              return (
                <li key={item.month.toISOString()} className="group relative flex h-full w-full max-w-16 flex-col items-center justify-end">
                  <span className="num pointer-events-none absolute z-10 -translate-y-1 rounded-md bg-ink px-2 py-1 text-xs font-semibold whitespace-nowrap text-white opacity-0 shadow-lg transition-opacity group-hover:opacity-100" style={{ bottom: `${height}%` }}>
                    {formatCurrency(item.amount, currency)}
                  </span>
                  <motion.span
                    initial={{ height: 0 }}
                    whileInView={{ height: `${height}%` }}
                    viewport={{ once: true }}
                    transition={{ duration: 0.8, delay: index * 0.07, ease: [0.16, 1, 0.3, 1] }}
                    className={cn(
                      'block w-full rounded-t-md',
                      item.isCurrent && 'bg-brand',
                      !item.isCurrent && !item.isFuture && 'bg-slate-700',
                      item.isFuture && 'bg-[repeating-linear-gradient(135deg,#fde68a_0,#fde68a_4px,#fef3c7_4px,#fef3c7_8px)] ring-1 ring-amber-300 ring-inset',
                    )}
                    aria-label={`${item.month.toLocaleDateString('es-PE', { month: 'long' })}: ${formatCurrency(item.amount, currency)}${item.isFuture ? ' programado' : ''}`}
                  />
                </li>
              )
            })}
          </ul>
        </div>
      </div>
      <ul className="mt-2 ml-[76px] flex justify-around gap-3" aria-hidden="true">
        {months.map((item) => (
          <li key={item.month.toISOString()} className={cn('w-full max-w-16 text-center text-xs capitalize', item.isCurrent ? 'font-bold text-ink' : 'text-slate-500')}>
            {item.month.toLocaleDateString('es-PE', { month: 'short' }).replace('.', '')}
          </li>
        ))}
      </ul>
      <ul className="mt-4 flex flex-wrap gap-4 text-xs text-slate-600" aria-label="Leyenda">
        <li className="flex items-center gap-1.5"><span className="size-3 rounded-sm bg-slate-700" aria-hidden="true" /> Cobrado</li>
        <li className="flex items-center gap-1.5"><span className="size-3 rounded-sm bg-brand" aria-hidden="true" /> Mes actual</li>
        <li className="flex items-center gap-1.5">
          <span className="size-3 rounded-sm bg-[repeating-linear-gradient(135deg,#fde68a_0,#fde68a_3px,#fef3c7_3px,#fef3c7_6px)] ring-1 ring-amber-300 ring-inset" aria-hidden="true" /> Programado
        </li>
      </ul>
    </div>
  )
}

/** Thin animated ring for a 0–1 ratio, with the percentage in the middle. */
export function OccupancyRing({ ratio }: { ratio: number }) {
  const radius = 42
  const circumference = 2 * Math.PI * radius
  return (
    <div className="relative size-28 shrink-0">
      <svg viewBox="0 0 100 100" className="size-full -rotate-90" aria-hidden="true">
        <circle cx="50" cy="50" r={radius} fill="none" stroke="#f1f5f9" strokeWidth="9" />
        <motion.circle
          cx="50"
          cy="50"
          r={radius}
          fill="none"
          stroke="#f59e0b"
          strokeWidth="9"
          strokeLinecap="round"
          strokeDasharray={circumference}
          initial={{ strokeDashoffset: circumference }}
          whileInView={{ strokeDashoffset: circumference * (1 - ratio) }}
          viewport={{ once: true }}
          transition={{ duration: 1.1, ease: [0.16, 1, 0.3, 1] }}
        />
      </svg>
      <span className="num absolute inset-0 flex items-center justify-center text-2xl font-bold text-ink">{Math.round(ratio * 100)}%</span>
    </div>
  )
}
