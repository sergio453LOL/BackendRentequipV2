import { Building2, CalendarDays, CreditCard, MessageSquareText, Undo2 } from 'lucide-react'
import type { ReactNode } from 'react'
import { Link } from 'react-router-dom'
import { buildPath } from '../../routes/paths'
import type { Reservation, ReservationRole } from '../../types'
import { formatCurrency, formatDate } from '../../utils/format'
import { StatusBadge } from '../common/StatusBadge'

interface ReservationCardProps {
  reservation: Reservation
  role: ReservationRole
  actions?: ReactNode
}

export function ReservationCard({ reservation, role, actions }: ReservationCardProps) {
  return (
    <article className="flex flex-col gap-4 rounded-2xl border border-slate-200 bg-white p-5 shadow-sm md:flex-row md:items-center">
      <div className="min-w-0 flex-1 space-y-2">
        <div className="flex flex-wrap items-center gap-2">
          <Link
            to={buildPath.equipmentDetail(reservation.equipmentId)}
            className="truncate font-semibold text-slate-900 hover:text-brand-700"
          >
            {reservation.equipmentName}
          </Link>
          <StatusBadge kind="reservation" status={reservation.status} />
          <PaymentBadge reservation={reservation} />
          <span className="text-xs text-slate-400">#{reservation.id}</span>
        </div>
        <div className="flex flex-wrap gap-x-5 gap-y-1 text-sm text-slate-500">
          <span className="flex items-center gap-1.5">
            <CalendarDays className="size-4" aria-hidden="true" />
            {formatDate(reservation.startDate)} – {formatDate(reservation.endDate)} ({reservation.totalDays}{' '}
            {reservation.totalDays === 1 ? 'día' : 'días'})
          </span>
          {role === 'OWNER' && (
            <span className="flex items-center gap-1.5">
              <Building2 className="size-4" aria-hidden="true" />
              {reservation.renterCompanyName}
            </span>
          )}
        </div>
        {reservation.notes && (
          <p className="flex items-start gap-1.5 text-sm text-slate-600">
            <MessageSquareText className="mt-0.5 size-4 shrink-0 text-slate-400" aria-hidden="true" />
            <span className="line-clamp-2">{reservation.notes}</span>
          </p>
        )}
      </div>

      <div className="flex flex-col gap-3 md:items-end">
        <p className="text-right">
          <span className="block text-lg font-bold text-slate-900">
            {formatCurrency(reservation.totalAmount, reservation.currency)}
          </span>
          <span className="text-xs text-slate-500">
            incluye {formatCurrency(reservation.securityDeposit, reservation.currency)} de garantía
          </span>
        </p>
        {actions && <div className="flex flex-wrap gap-2 md:justify-end">{actions}</div>}
      </div>
    </article>
  )
}

function PaymentBadge({ reservation }: { reservation: Reservation }) {
  if (reservation.paymentStatus === 'PAID') {
    return (
      <span className="inline-flex items-center gap-1 rounded-full bg-emerald-50 px-2.5 py-0.5 text-xs font-medium text-emerald-700 ring-1 ring-emerald-600/20 ring-inset">
        <CreditCard className="size-3" aria-hidden="true" /> Pagado
      </span>
    )
  }
  if (reservation.paymentStatus === 'REFUNDED') {
    return (
      <span className="inline-flex items-center gap-1 rounded-full bg-slate-100 px-2.5 py-0.5 text-xs font-medium text-slate-600 ring-1 ring-slate-500/20 ring-inset">
        <Undo2 className="size-3" aria-hidden="true" /> Reembolsado
      </span>
    )
  }
  if (reservation.status === 'PENDING' || reservation.status === 'CONFIRMED') {
    return (
      <span className="inline-flex items-center gap-1 rounded-full bg-amber-50 px-2.5 py-0.5 text-xs font-medium text-amber-800 ring-1 ring-amber-600/20 ring-inset">
        <CreditCard className="size-3" aria-hidden="true" /> Pago pendiente
      </span>
    )
  }
  return null
}
