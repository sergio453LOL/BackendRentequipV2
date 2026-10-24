import { useFetch } from '../../hooks/useFetch'
import { ENDPOINTS } from '../../services/endpoints'
import type { BlockedWindow } from '../../types'
import { cn } from '../../utils/cn'
import { addDays, formatDateRange, parseIsoDate, toIsoDate, todayIso } from '../../utils/format'

const WEEKDAYS = ['L', 'M', 'M', 'J', 'V', 'S', 'D']
const MONTHS_SHOWN = 2

type DayState = 'past' | 'free' | 'booked' | 'pending'

const DAY_CLASSES: Record<DayState, string> = {
  past: 'text-slate-300',
  free: 'text-ink hover:bg-slate-100',
  booked: 'bg-red-50 text-red-800 line-through decoration-red-300',
  pending: 'bg-amber-50 text-amber-800 ring-1 ring-inset ring-amber-300',
}

/** Monday-first weeks of one month, padded with nulls so the first day lands under its weekday. */
function monthGrid(year: number, month: number): (Date | null)[] {
  const first = new Date(year, month, 1)
  const daysInMonth = new Date(year, month + 1, 0).getDate()
  const offset = (first.getDay() + 6) % 7
  return [...Array<null>(offset).fill(null), ...Array.from({ length: daysInMonth }, (_, day) => new Date(year, month, day + 1))]
}

/** "Noviembre de 2026": only the first letter in capitals. */
function monthTitle(month: Date): string {
  const label = month.toLocaleDateString('es-PE', { month: 'long', year: 'numeric' })
  return label.charAt(0).toUpperCase() + label.slice(1)
}

function stateOf(day: Date, today: Date, windows: BlockedWindow[]): DayState {
  if (day < today) return 'past'
  const iso = toIsoDate(day)
  const window = windows.find((item) => item.startDate <= iso && iso <= item.endDate)
  if (!window) return 'free'
  return window.status === 'PENDING' ? 'pending' : 'booked'
}

/**
 * This month and the next for one machine: occupied days struck through in red, days held by a request
 * still waiting for the owner in amber, the rest free. Built from the blocked-dates feed, which never
 * says who rented the machine.
 */
export function AvailabilityCalendar({ equipmentId }: { equipmentId: number }) {
  const today = parseIsoDate(todayIso())
  const from = new Date(today.getFullYear(), today.getMonth(), 1)
  const until = addDays(new Date(today.getFullYear(), today.getMonth() + MONTHS_SHOWN, 1), -1)
  const { data, error, refetch } = useFetch<BlockedWindow[]>(ENDPOINTS.equipmentBlockedDates(equipmentId), {
    from: toIsoDate(from),
    until: toIsoDate(until),
  })
  const windows = data ?? []
  const months = Array.from({ length: MONTHS_SHOWN }, (_, index) => new Date(today.getFullYear(), today.getMonth() + index, 1))

  return (
    <section aria-labelledby="availability-title" className="space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h2 id="availability-title" className="text-lg font-bold text-ink">
          Disponibilidad
        </h2>
        <ul className="flex flex-wrap items-center gap-4 text-xs font-medium text-slate-600" aria-label="Leyenda">
          <li className="flex items-center gap-1.5">
            <span className="size-3 rounded-sm border border-slate-200 bg-white" aria-hidden="true" /> Libre
          </li>
          <li className="flex items-center gap-1.5">
            <span className="size-3 rounded-sm bg-amber-50 ring-1 ring-amber-300 ring-inset" aria-hidden="true" /> Por confirmar
          </li>
          <li className="flex items-center gap-1.5">
            <span className="size-3 rounded-sm bg-red-50 ring-1 ring-red-200 ring-inset" aria-hidden="true" /> Ocupado
          </li>
        </ul>
      </div>

      {error ? (
        <p className="rounded-lg border border-dashed border-slate-300 px-4 py-3 text-sm text-slate-600">
          No pudimos cargar el calendario.{' '}
          <button type="button" onClick={refetch} className="font-semibold text-ink underline">
            Reintentar
          </button>
        </p>
      ) : (
        <div className={cn('grid gap-4 sm:grid-cols-2', !data && 'opacity-60')} aria-busy={!data}>
          {months.map((month) => (
            <div key={month.getMonth()} className="rounded-xl border border-slate-200 bg-white p-4">
              <p className="mb-3 text-sm font-bold text-ink">
                {monthTitle(month)}
              </p>
              <div className="grid grid-cols-7 gap-1 text-center" aria-hidden="true">
                {WEEKDAYS.map((weekday, index) => (
                  <span key={index} className="pb-1 text-xs font-semibold text-slate-500">
                    {weekday}
                  </span>
                ))}
                {monthGrid(month.getFullYear(), month.getMonth()).map((day, index) => {
                  if (!day) return <span key={`pad-${index}`} />
                  const state = stateOf(day, today, windows)
                  const isToday = day.getTime() === today.getTime()
                  return (
                    <span
                      key={day.getDate()}
                      className={cn(
                        'num flex h-8 items-center justify-center rounded-md text-sm font-medium transition-colors',
                        DAY_CLASSES[state],
                        isToday && 'font-bold ring-2 ring-brand ring-inset',
                      )}
                    >
                      {day.getDate()}
                    </span>
                  )
                })}
              </div>
            </div>
          ))}
          {/* The grid is visual; screen readers get the busy periods as a sentence. */}
          <p className="sr-only">
            {windows.length === 0
              ? 'Libre todos los días de este mes y el siguiente.'
              : windows
                  .map((item) => `${item.status === 'PENDING' ? 'Por confirmar' : 'Ocupado'}: ${formatDateRange(item.startDate, item.endDate)}`)
                  .join('; ')}
          </p>
        </div>
      )}
    </section>
  )
}
