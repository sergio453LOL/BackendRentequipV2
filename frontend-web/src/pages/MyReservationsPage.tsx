import { CalendarX2, Check, CreditCard, FileDown, FilterX, Flag, Play, Search, X } from 'lucide-react'
import { useState } from 'react'
import { Link } from 'react-router-dom'
import { Button } from '../components/common/Button'
import { ConfirmDialog } from '../components/common/ConfirmDialog'
import { EmptyState } from '../components/common/EmptyState'
import { ErrorState } from '../components/common/ErrorState'
import { PageHeader } from '../components/common/PageHeader'
import { Pagination } from '../components/common/Pagination'
import { Select } from '../components/common/Select'
import { SkeletonLoader } from '../components/common/SkeletonLoader'
import { PaymentModal } from '../components/payments/PaymentModal'
import { ReservationCard } from '../components/reservations/ReservationCard'
import { useAuth } from '../hooks/useAuth'
import { useFetch } from '../hooks/useFetch'
import { useMutation } from '../hooks/useMutation'
import { useToast } from '../hooks/useToast'
import { usePagination } from '../hooks/usePagination'
import { useQueryParam } from '../hooks/useQueryParam'
import { PATHS } from '../routes/paths'
import { ENDPOINTS } from '../services/endpoints'
import { paymentService } from '../services/paymentService'
import { reservationService } from '../services/reservationService'
import type { PageResponse, Reservation, ReservationRole, ReservationStatus } from '../types'
import { cn } from '../utils/cn'
import { downloadReceipt } from '../utils/downloadReceipt'
import { RESERVATION_ROLE_LABELS, RESERVATION_STATUS_LABELS, RESERVATION_STATUSES } from '../utils/labels'

type ReservationAction = 'confirm' | 'reject' | 'cancel' | 'start' | 'complete'

interface ActionConfig {
  label: string
  title: string
  message: string
  success: string
  variant: 'primary' | 'danger'
  askForReason: boolean
}

const ACTIONS: Record<ReservationAction, ActionConfig> = {
  confirm: {
    label: 'Confirmar',
    title: 'Confirmar reserva',
    message: 'El equipo quedará reservado para esta empresa en las fechas indicadas.',
    success: 'Reserva confirmada',
    variant: 'primary',
    askForReason: false,
  },
  reject: {
    label: 'Rechazar',
    title: 'Rechazar solicitud',
    message: 'La empresa solicitante será notificada y las fechas quedarán libres.',
    success: 'Solicitud rechazada',
    variant: 'danger',
    askForReason: true,
  },
  cancel: {
    label: 'Cancelar reserva',
    title: 'Cancelar reserva',
    message: 'Esta acción no se puede deshacer. La otra empresa será notificada.',
    success: 'Reserva cancelada',
    variant: 'danger',
    askForReason: true,
  },
  start: {
    label: 'Iniciar alquiler',
    title: 'Iniciar alquiler',
    message: 'Confirma que el equipo fue entregado y el alquiler está en curso.',
    success: 'Alquiler iniciado',
    variant: 'primary',
    askForReason: false,
  },
  complete: {
    label: 'Finalizar',
    title: 'Finalizar alquiler',
    message: 'Confirma que el equipo fue devuelto. La reserva quedará completada.',
    success: 'Alquiler finalizado',
    variant: 'primary',
    askForReason: false,
  },
}

const ACTION_ICONS = { confirm: Check, reject: X, cancel: CalendarX2, start: Play, complete: Flag } as const

/** Mirrors the backend's ReservationStatusPolicy so the UI only offers transitions that can succeed. */
function availableActions(reservation: Reservation, role: ReservationRole, canManage: boolean): ReservationAction[] {
  const actions: ReservationAction[] = []
  if (role === 'OWNER' && canManage) {
    if (reservation.status === 'PENDING') actions.push('confirm', 'reject')
    if (reservation.status === 'CONFIRMED') actions.push('start')
    if (reservation.status === 'IN_PROGRESS') actions.push('complete')
  }
  if (reservation.status === 'PENDING' || reservation.status === 'CONFIRMED') actions.push('cancel')
  return actions
}

function runAction(action: ReservationAction, reservation: Reservation, reason: string): Promise<Reservation> {
  switch (action) {
    case 'confirm':
      return reservationService.confirm(reservation.id)
    case 'reject':
      return reservationService.updateStatus(reservation.id, 'REJECTED', reason)
    case 'cancel':
      return reservationService.cancel(reservation.id, reason)
    case 'start':
      return reservationService.updateStatus(reservation.id, 'IN_PROGRESS')
    case 'complete':
      return reservationService.updateStatus(reservation.id, 'COMPLETED')
  }
}

function isReservationRole(value: string): value is ReservationRole {
  return value === 'RENTER' || value === 'OWNER'
}

function isReservationStatus(value: string): value is ReservationStatus {
  return (RESERVATION_STATUSES as string[]).includes(value)
}

export default function MyReservationsPage() {
  const { user } = useAuth()
  const [roleParam, setRoleParam] = useQueryParam('role', 'RENTER')
  const [statusParam, setStatusParam] = useQueryParam('status')
  const role: ReservationRole = isReservationRole(roleParam) ? roleParam : 'RENTER'
  const status = isReservationStatus(statusParam) ? statusParam : undefined
  const { page, size, setPage, setSize } = usePagination(10)
  const canManage = user?.role !== 'OPERATOR'

  const { data, error, isLoading, refetch } = useFetch<PageResponse<Reservation>>(ENDPOINTS.reservations, {
    role,
    status,
    page,
    size,
  })

  const toast = useToast()
  const [pending, setPending] = useState<{ action: ReservationAction; reservation: Reservation } | null>(null)
  const [payingReservation, setPayingReservation] = useState<Reservation | null>(null)
  const [downloadingId, setDownloadingId] = useState<number | null>(null)

  async function handleDownloadReceipt(reservation: Reservation) {
    setDownloadingId(reservation.id)
    try {
      await downloadReceipt(reservation, await paymentService.list(reservation.id))
    } catch {
      toast.error('No pudimos generar el comprobante', 'Inténtalo nuevamente en unos segundos.')
    } finally {
      setDownloadingId(null)
    }
  }
  const pendingConfig = pending ? ACTIONS[pending.action] : null

  const performAction = useMutation(runAction, {
    successMessage: () => (pendingConfig ? pendingConfig.success : 'Reserva actualizada'),
    errorTitle: 'No se pudo actualizar la reserva',
    onSuccess: () => {
      setPending(null)
      refetch()
    },
  })

  return (
    <div className="mx-auto max-w-5xl space-y-6">
      <PageHeader
        title="Mis reservas"
        description="Sigue tus solicitudes y gestiona las reservas de tus equipos."
        breadcrumbs={[{ label: 'Mis reservas' }]}
      />

      <div className="flex flex-col gap-4 md:flex-row md:items-end md:justify-between">
        <div role="tablist" aria-label="Tipo de reserva" className="inline-flex rounded-xl bg-slate-100 p-1">
          {(['RENTER', 'OWNER'] as const).map((tab) => (
            <button
              key={tab}
              type="button"
              role="tab"
              aria-selected={role === tab}
              onClick={() => setRoleParam(tab)}
              className={cn(
                'rounded-lg px-4 py-2 text-sm font-medium transition',
                role === tab ? 'bg-white text-slate-900 shadow-sm' : 'text-slate-500 hover:text-slate-900',
              )}
            >
              {RESERVATION_ROLE_LABELS[tab]}
            </button>
          ))}
        </div>
        <Select
          className="md:w-56"
          label="Estado"
          placeholder="Todos los estados"
          value={status ?? ''}
          onChange={(event) => setStatusParam(event.target.value)}
          options={RESERVATION_STATUSES.map((value) => ({ value, label: RESERVATION_STATUS_LABELS[value] }))}
        />
      </div>

      {error && <ErrorState error={error} onRetry={refetch} />}
      {!error && !data && <SkeletonLoader variant="list" count={4} />}

      {!error && data && data.totalElements === 0 && (
        <EmptyState
          icon={CalendarX2}
          title={status ? `No tienes reservas "${RESERVATION_STATUS_LABELS[status].toLowerCase()}"` : 'Todavía no hay reservas'}
          description={
            status
              ? 'Quita el filtro de estado para ver el resto de tu historial.'
              : role === 'RENTER'
                ? 'Explora el catálogo y solicita la maquinaria que necesitas para tu obra.'
                : 'Cuando otras empresas reserven tus equipos, las solicitudes aparecerán aquí.'
          }
          action={
            status ? (
              <Button variant="secondary" onClick={() => setStatusParam('')} leftIcon={<FilterX className="size-4" />}>
                Quitar filtro
              </Button>
            ) : (
              role === 'RENTER' && (
                <Link
                  to={PATHS.catalog}
                  className="inline-flex items-center gap-2 rounded-lg bg-brand-500 px-4 py-2 text-sm font-semibold text-slate-900 hover:bg-brand-400"
                >
                  <Search className="size-4" aria-hidden="true" /> Explorar catálogo
                </Link>
              )
            )
          }
        />
      )}

      {!error && data && data.totalElements > 0 && (
        <>
          <ul aria-busy={isLoading} className={cn('space-y-3 transition-opacity', isLoading && 'opacity-60')}>
            {data.content.map((reservation) => (
              <li key={reservation.id}>
                <ReservationCard
                  reservation={reservation}
                  role={role}
                  actions={[
                    role === 'RENTER' &&
                      reservation.paymentStatus === 'UNPAID' &&
                      (reservation.status === 'PENDING' || reservation.status === 'CONFIRMED') && (
                        <Button key="pay" size="sm" onClick={() => setPayingReservation(reservation)} leftIcon={<CreditCard className="size-4" />}>
                          Pagar
                        </Button>
                      ),
                    reservation.paymentStatus !== 'UNPAID' && (
                      <Button
                        key="receipt"
                        size="sm"
                        variant="secondary"
                        isLoading={downloadingId === reservation.id}
                        onClick={() => void handleDownloadReceipt(reservation)}
                        leftIcon={<FileDown className="size-4" />}
                      >
                        Comprobante
                      </Button>
                    ),
                    ...availableActions(reservation, role, canManage).map((action) => {
                    const Icon = ACTION_ICONS[action]
                    return (
                      <Button
                        key={action}
                        size="sm"
                        variant={action === 'confirm' ? 'primary' : action === 'reject' || action === 'cancel' ? 'secondary' : 'primary'}
                        disabled={performAction.isLoading}
                        onClick={() => setPending({ action, reservation })}
                        leftIcon={<Icon className="size-4" />}
                      >
                        {ACTIONS[action].label}
                      </Button>
                    )
                  }),
                  ]}
                />
              </li>
            ))}
          </ul>
          <Pagination pageData={data} onPageChange={setPage} onSizeChange={setSize} isDisabled={isLoading} />
        </>
      )}

      {payingReservation && (
        <PaymentModal
          reservation={payingReservation}
          onClose={() => setPayingReservation(null)}
          onPaid={refetch}
        />
      )}

      {pending && pendingConfig && (
        <ConfirmDialog
          isOpen
          title={pendingConfig.title}
          message={`${pending.reservation.equipmentName}: ${pendingConfig.message}`}
          confirmLabel={pendingConfig.label}
          variant={pendingConfig.variant}
          askForReason={pendingConfig.askForReason}
          isLoading={performAction.isLoading}
          onConfirm={(reason) => void performAction.mutate(pending.action, pending.reservation, reason)}
          onClose={() => setPending(null)}
        />
      )}
    </div>
  )
}
