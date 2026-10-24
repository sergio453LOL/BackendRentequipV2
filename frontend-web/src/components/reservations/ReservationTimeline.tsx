import { ArrowUpRight, MessageSquareText } from 'lucide-react'
import type { ReactNode } from 'react'
import { Link } from 'react-router-dom'
import { buildPath } from '../../routes/paths'
import type { Reservation, ReservationStatus } from '../../types'
import { cn } from '../../utils/cn'
import { formatCurrency, formatDateRange, parseIsoDate, reservationCode } from '../../utils/format'
import { StatusBadge } from '../common/StatusBadge'
import { ReservationTracker } from './ReservationTracker'

const NODE_CLASSES: Record<ReservationStatus, string> = {
  PENDING: 'bg-status-amber',
  CONFIRMED: 'bg-status-green',
  IN_PROGRESS: 'bg-status-blue',
  COMPLETED: 'bg-slate-400',
  CANCELLED: 'bg-status-red',
  REJECTED: 'bg-status-red',
}

function isClosed(status: ReservationStatus): boolean {
  return status === 'CANCELLED' || status === 'REJECTED'
}

interface ReservationTimelineProps {
  reservations: Reservation[]
  renderActions: (reservation: Reservation) => ReactNode
}

/**
 * The renter's reservations in date order on a vertical rail: when each rental starts on the left, a
 * node in the colour of its status on the rail, and the reservation with its actions on the right.
 */
export function ReservationTimeline({ reservations, renderActions }: ReservationTimelineProps) {
  const ordered = [...reservations].sort((a, b) => a.startDate.localeCompare(b.startDate))

  return (
    <ol className="relative">
      {ordered.map((reservation, index) => {
        const start = parseIsoDate(reservation.startDate)
        const isLast = index === ordered.length - 1
        return (
          <li key={reservation.id} className="grid grid-cols-[52px_28px_minmax(0,1fr)] gap-x-1 sm:grid-cols-[64px_32px_minmax(0,1fr)]">
            <div className="pt-4 text-right">
              <p className="num text-xl leading-6 font-bold text-ink">{start.getDate()}</p>
              <p className="text-xs font-medium text-slate-500 uppercase">
                {start.toLocaleDateString('es-PE', { month: 'short' }).replace('.', '')}
              </p>
            </div>

            <div className="relative flex justify-center" aria-hidden="true">
              <span className={cn('absolute top-0 w-px bg-slate-200', isLast ? 'h-6' : 'bottom-0')} />
              <span className={cn('relative mt-5 size-3 rounded-full ring-4 ring-slate-50', NODE_CLASSES[reservation.status])} />
            </div>

            <article className="mb-4 rounded-2xl border border-slate-200/80 bg-white p-4 transition-all duration-300 hover:-translate-y-0.5 hover:shadow-xl sm:p-5">
              <div className="flex flex-wrap items-start justify-between gap-x-4 gap-y-2">
                <div className="min-w-0">
                  <Link
                    to={buildPath.equipmentDetail(reservation.equipmentId)}
                    className="group inline-flex items-center gap-1 text-base font-semibold text-ink hover:text-accent-ink"
                  >
                    <span className="truncate">{reservation.equipmentName}</span>
                    <ArrowUpRight className="size-4 shrink-0 opacity-0 transition-opacity group-hover:opacity-100" aria-hidden="true" />
                  </Link>
                  <p className="mt-0.5 text-sm text-slate-600">
                    <span className="num">{formatDateRange(reservation.startDate, reservation.endDate)}</span> ·{' '}
                    {reservation.totalDays} {reservation.totalDays === 1 ? 'día' : 'días'} ·{' '}
                    <span className="whitespace-nowrap text-slate-500">{reservationCode(reservation.id)}</span>
                  </p>
                </div>
                <p className="sm:text-right">
                  <span className="num block text-lg font-bold text-ink">{formatCurrency(reservation.totalAmount, reservation.currency)}</span>
                  <span className="num text-xs text-slate-500">
                    incl. {formatCurrency(reservation.securityDeposit, reservation.currency)} de garantía
                  </span>
                </p>
              </div>

              <div className="mt-3 flex flex-wrap items-center gap-2">
                <StatusBadge kind="reservation" status={reservation.status} />
                {/* An unpaid booking that was cancelled or rejected owes nothing: no payment pill. */}
                {!(isClosed(reservation.status) && reservation.paymentStatus === 'UNPAID') && (
                  <StatusBadge kind="payment" status={reservation.paymentStatus} />
                )}
              </div>

              <ReservationTracker reservation={reservation} />

              {reservation.notes && (
                <p className="mt-4 flex items-start gap-1.5 text-sm text-slate-600">
                  <MessageSquareText className="mt-0.5 size-4 shrink-0 text-slate-400" aria-hidden="true" />
                  <span className="line-clamp-2">{reservation.notes}</span>
                </p>
              )}

              <div className="mt-4 flex flex-wrap items-center gap-2 border-t border-slate-100 pt-4">{renderActions(reservation)}</div>
            </article>
          </li>
        )
      })}
    </ol>
  )
}
