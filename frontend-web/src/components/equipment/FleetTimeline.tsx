import type { CSSProperties } from 'react'
import { Link } from 'react-router-dom'
import { buildPath } from '../../routes/paths'
import type { EquipmentSummary, Reservation } from '../../types'
import { cn } from '../../utils/cn'
import { addDays, daysBetween, formatCurrency, formatDateRange, parseIsoDate } from '../../utils/format'
import { EquipmentImage } from './EquipmentImage'

export const TIMELINE_DAYS = 28
/** On a phone two weeks fit across the screen without scrolling sideways. */
export const COMPACT_TIMELINE_DAYS = 14

/** Bookings that hold the machine on the calendar; cancelled, rejected and finished ones free it. */
const HOLDING_STATUSES = new Set(['PENDING', 'CONFIRMED', 'IN_PROGRESS'])

type BarKind = 'confirmed' | 'pending'

const BAR_CLASSES: Record<BarKind, string> = {
  confirmed: 'bg-accent-tint text-accent-ink',
  pending: 'border border-dashed border-accent bg-white text-accent-ink',
}

export function TimelineLegend() {
  return (
    <ul className="flex items-center gap-4 text-xs text-slate-500" aria-label="Leyenda">
      <li className="flex items-center gap-1.5">
        <span className={cn('h-2.5 w-4 rounded-[3px]', BAR_CLASSES.confirmed)} aria-hidden="true" />
        Reservada
      </li>
      <li className="flex items-center gap-1.5">
        <span className={cn('h-2.5 w-4 rounded-[3px]', BAR_CLASSES.pending)} aria-hidden="true" />
        Por confirmar
      </li>
    </ul>
  )
}

interface FleetTimelineProps {
  equipment: EquipmentSummary[]
  reservations: Reservation[]
  from: Date
  days?: number
  /** Photo-only machine column, for a phone. */
  compact?: boolean
}

/**
 * The owner's fleet against the next four weeks: one row per machine, days across, each booking as a
 * bar. A confirmed booking is a filled amber bar; a request still waiting for an answer is dashed.
 */
export function FleetTimeline({ equipment, reservations, from, days: dayCount = TIMELINE_DAYS, compact = false }: FleetTimelineProps) {
  const days = Array.from({ length: dayCount }, (_, index) => addDays(from, index))
  const grid: CSSProperties = { gridTemplateColumns: `${compact ? 48 : 220}px repeat(${dayCount}, minmax(0, 1fr))` }

  return (
    <div>
      <div>
        <div className="grid h-8 items-end" style={grid} aria-hidden="true">
          <span />
          {days.map((day, index) => {
            // Leave room after "Hoy" so the first Monday label never collides with it.
            const isMonday = day.getDay() === 1 && index >= 3
            return (
              <span key={index} className="relative h-full">
                {(index === 0 || isMonday) && (
                  <span className={cn('absolute bottom-1.5 left-0 text-xs whitespace-nowrap', index === 0 ? 'font-medium text-ink' : 'text-slate-500')}>
                    {index === 0 ? 'Hoy' : day.toLocaleDateString('es-PE', { day: 'numeric', month: 'short' })}
                  </span>
                )}
              </span>
            )
          })}
        </div>

        <ul>
          {equipment.map((machine) => {
            const bookings = reservations.filter(
              (reservation) => reservation.equipmentId === machine.id && HOLDING_STATUSES.has(reservation.status),
            )
            return (
              <li key={machine.id} className="relative grid h-14 items-center border-t border-rule" style={grid}>
                <Link
                  to={buildPath.equipmentDetail(machine.id)}
                  className="flex min-w-0 items-center gap-2.5 pr-3 hover:[&_p:first-child]:underline"
                  style={{ gridRow: 1, gridColumn: 1 }}
                >
                  <EquipmentImage src={machine.primaryImageUrl} alt="" className="size-9 shrink-0 rounded-md" />
                  <span className={cn('min-w-0', compact && 'sr-only')}>
                    <p className="truncate text-sm font-medium text-ink" title={machine.name}>
                      {machine.brand} {machine.model}
                    </p>
                    <p className="num truncate text-xs text-slate-500">{formatCurrency(machine.dailyRate, machine.currency)} / día</p>
                  </span>
                </Link>

                {days.map((day, index) => {
                  const isWeekend = day.getDay() === 0 || day.getDay() === 6
                  return (
                    <span
                      key={index}
                      aria-hidden="true"
                      className={cn('h-full', isWeekend && 'bg-raised', index === 0 && 'border-l border-accent')}
                      style={{ gridRow: 1, gridColumn: index + 2 }}
                    />
                  )
                })}

                {bookings.map((reservation) => {
                  const start = Math.max(0, daysBetween(from, parseIsoDate(reservation.startDate)))
                  const end = Math.min(dayCount - 1, daysBetween(from, parseIsoDate(reservation.endDate)))
                  if (end < 0 || start > dayCount - 1) return null
                  const kind: BarKind = reservation.status === 'PENDING' ? 'pending' : 'confirmed'
                  const span = end - start + 1
                  const range = formatDateRange(reservation.startDate, reservation.endDate)
                  return (
                    <span
                      key={reservation.id}
                      title={`${reservation.renterCompanyName} · ${range} · ${kind === 'pending' ? 'por confirmar' : 'reservada'}`}
                      className={cn('z-10 mx-px flex h-6 min-w-0 items-center overflow-hidden rounded px-1.5 text-xs font-medium whitespace-nowrap', BAR_CLASSES[kind])}
                      style={{ gridRow: 1, gridColumn: `${start + 2} / span ${span}` }}
                    >
                      {!compact && <span className="truncate">{span >= 7 ? `${reservation.renterCompanyName} · ${range}` : range}</span>}
                    </span>
                  )
                })}
              </li>
            )
          })}
        </ul>
      </div>
    </div>
  )
}
