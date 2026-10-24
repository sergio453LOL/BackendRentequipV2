import { CalendarX2, Check, CreditCard, FileDown, FilterX, Flag, Play, Search, X } from 'lucide-react'
import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { Button } from '../components/common/Button'
import { ConfirmDialog } from '../components/common/ConfirmDialog'
import { EmptyState } from '../components/common/EmptyState'
import { ErrorState } from '../components/common/ErrorState'
import { PageHeader } from '../components/common/PageHeader'
import { Pagination } from '../components/common/Pagination'
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
import { api } from '../services/api'
import { ENDPOINTS } from '../services/endpoints'
import { paymentService } from '../services/paymentService'
import { reservationService } from '../services/reservationService'
import type { PageResponse, Reservation, ReservationRole, ReservationStatus } from '../types'
import { cn } from '../utils/cn'
import { downloadReceipt } from '../utils/downloadReceipt'
import { formatNumber } from '../utils/format'
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

const ALL = 'ALL'

/** Reservations per status for the current side (renter or owner): the totals behind the filter cards. */
function useStatusCounts(role: ReservationRole, reloadKey: number): Record<string, number> | null {
  const key = `${role}|${reloadKey}`
  const [result, setResult] = useState<{ key: string; counts: Record<string, number> } | null>(null)

  useEffect(() => {
    const controller = new AbortController()
    Promise.all(
      [ALL, ...RESERVATION_STATUSES].map(async (status) => {
        const { data } = await api.get<PageResponse<Reservation>>(ENDPOINTS.reservations, {
          params: { role, status: status === ALL ? undefined : status, page: 0, size: 1 },
          signal: controller.signal,
        })
        return [status, data.totalElements] as const
      }),
    )
      .then((entries) => setResult({ key, counts: Object.fromEntries(entries) }))
      .catch(() => undefined)
    return () => controller.abort()
  }, [key, role])

  return result?.key === key ? result.counts : null
}

const STATUS_CARD_LABELS: Record<ReservationStatus | typeof ALL, string> = {
  ALL: 'Todas',
  PENDING: 'Pendientes',
  CONFIRMED: 'Confirmadas',
  IN_PROGRESS: 'En curso',
  COMPLETED: 'Completadas',
  CANCELLED: 'Canceladas',
  REJECTED: 'Rechazadas',
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
  const { page, size, setPage, setSize } = usePagination()
  const canManage = user?.role !== 'OPERATOR'

  const { data, error, isLoading, refetch: refetchList } = useFetch<PageResponse<Reservation>>(ENDPOINTS.reservations, {
    role,
    status,
    page,
    size,
  })
  const [countsVersion, setCountsVersion] = useState(0)
  const counts = useStatusCounts(role, countsVersion)
  const refetch = () => {
    refetchList()
    setCountsVersion((version) => version + 1)
  }

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
    <div className="mx-auto max-w-[1200px] space-y-5">
      <PageHeader
        title="Reservas"
        description={
          role === 'RENTER'
            ? 'Lo que tu empresa alquila a otras: pagos, comprobantes y estado de cada solicitud.'
            : 'Lo que otras empresas reservan de tu flota: confirma, entrega y cierra cada alquiler.'
        }
        breadcrumbs={[{ label: 'Reservas' }]}
      />

      <div className="border-b border-line">
        <div role="tablist" aria-label="Tipo de reserva" className="-mb-px flex gap-6">
          {(['RENTER', 'OWNER'] as const).map((tab) => (
            <button
              key={tab}
              type="button"
              role="tab"
              aria-selected={role === tab}
              onClick={() => setRoleParam(tab)}
              className={cn(
                'h-10 border-b-2 text-sm transition-colors',
                role === tab ? 'border-accent font-semibold text-ink' : 'border-transparent text-slate-600 hover:text-ink',
              )}
            >
              {RESERVATION_ROLE_LABELS[tab]}
            </button>
          ))}
        </div>
      </div>

      <div role="group" aria-label="Filtrar por estado" className="-mx-4 flex gap-2 overflow-x-auto px-4 pb-1 sm:mx-0 sm:px-0">
        {([ALL, ...RESERVATION_STATUSES] as const).map((value) => {
          const isSelected = (status ?? ALL) === value
          const count = counts?.[value]
          return (
            <button
              key={value}
              type="button"
              aria-pressed={isSelected}
              onClick={() => setStatusParam(value === ALL ? '' : value)}
              className={cn(
                'flex h-[61px] min-w-[120px] shrink-0 flex-col justify-center rounded-lg border px-3 text-left transition-colors sm:flex-1',
                isSelected ? 'border-[1.5px] border-accent bg-accent-tint/50' : 'border-line bg-sheet hover:border-slate-300',
              )}
            >
              <span className="truncate text-[13px] text-slate-600">{STATUS_CARD_LABELS[value]}</span>
              <span className={cn('num text-base font-semibold', count === 0 ? 'text-slate-500' : 'text-ink')}>
                {count === undefined ? '–' : formatNumber(count)}
              </span>
            </button>
          )
        })}
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
                  className="inline-flex h-9 items-center gap-2 rounded-md bg-ink px-3.5 text-sm font-medium text-white hover:bg-ink-hover"
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
          <ul aria-busy={isLoading} className={cn('divide-y divide-rule rounded-lg border border-line transition-opacity', isLoading && 'opacity-60')}>
            {data.content.map((reservation) => (
              <li key={reservation.id}>
                <ReservationCard
                  reservation={reservation}
                  role={role}
                  actions={[
                    role === 'RENTER' &&
                      reservation.paymentStatus === 'UNPAID' &&
                      (reservation.status === 'PENDING' || reservation.status === 'CONFIRMED') && (
                        <Button key="pay" size="sm" variant="secondary" onClick={() => setPayingReservation(reservation)} leftIcon={<CreditCard className="size-4" />}>
                          Pagar
                        </Button>
                      ),
                    reservation.paymentStatus !== 'UNPAID' && (
                      <Button
                        key="receipt"
                        size="sm"
                        variant="ghost"
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
                        variant={action === 'reject' || action === 'cancel' ? 'ghost' : 'secondary'}
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
