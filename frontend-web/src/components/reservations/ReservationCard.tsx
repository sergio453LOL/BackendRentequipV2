import type { ReactNode } from 'react'
import { Link } from 'react-router-dom'
import { buildPath } from '../../routes/paths'
import type { Reservation, ReservationRole } from '../../types'
import { formatCurrency, formatDateRange, reservationCode } from '../../utils/format'
import { StatusBadge } from '../common/StatusBadge'

interface ReservationCardProps {
  reservation: Reservation
  role: ReservationRole
  actions?: ReactNode
}

/** One reservation as a row: what and when on the left, the money on the right, its actions under it. */
export function ReservationCard({ reservation, role, actions }: ReservationCardProps) {
  const days = `${reservation.totalDays} ${reservation.totalDays === 1 ? 'día' : 'días'}`
  return (
    <article className="flex flex-col gap-3 px-5 py-4 md:flex-row md:items-start md:gap-6">
      <div className="min-w-0 flex-1 space-y-1">
        <div className="flex flex-wrap items-center gap-2">
          <Link
            to={buildPath.equipmentDetail(reservation.equipmentId)}
            className="truncate text-[15px] font-semibold text-ink hover:text-accent-ink"
          >
            {reservation.equipmentName}
          </Link>
          <StatusBadge kind="reservation" status={reservation.status} />
          <PaymentBadge reservation={reservation} />
        </div>
        <p className="text-[13px] text-slate-600">
          <span className="num">{formatDateRange(reservation.startDate, reservation.endDate)}</span> · {days}
          {role === 'OWNER' && <> · {reservation.renterCompanyName}</>}
          <span className="text-slate-500"> · <span className="whitespace-nowrap">{reservationCode(reservation.id)}</span></span>
        </p>
        {reservation.notes && <p className="line-clamp-2 max-w-[70ch] text-[13px] text-slate-500">“{reservation.notes}”</p>}
      </div>

      <div className="flex flex-col gap-2 md:items-end">
        <p className="md:text-right">
          <span className="num block text-lg font-bold text-ink">{formatCurrency(reservation.totalAmount, reservation.currency)}</span>
          <span className="num text-xs text-slate-500">
            incluye {formatCurrency(reservation.securityDeposit, reservation.currency)} de garantía
          </span>
        </p>
        {actions && <div className="flex flex-wrap items-center gap-1 md:justify-end">{actions}</div>}
      </div>
    </article>
  )
}

function PaymentBadge({ reservation }: { reservation: Reservation }) {
  const isClosed = reservation.status === 'CANCELLED' || reservation.status === 'REJECTED'
  if (isClosed && reservation.paymentStatus === 'UNPAID') return null
  return <StatusBadge kind="payment" status={reservation.paymentStatus} />
}
