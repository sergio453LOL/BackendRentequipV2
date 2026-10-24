import { Plus, Search } from 'lucide-react'
import type { ReactNode } from 'react'
import { Link } from 'react-router-dom'
import { Button } from '../components/common/Button'
import { ErrorState } from '../components/common/ErrorState'
import { Pagination } from '../components/common/Pagination'
import { COMPACT_TIMELINE_DAYS, FleetTimeline, TIMELINE_DAYS, TimelineLegend } from '../components/equipment/FleetTimeline'
import { useAuth } from '../hooks/useAuth'
import { useFetch } from '../hooks/useFetch'
import { useMediaQuery } from '../hooks/useMediaQuery'
import { useMutation } from '../hooks/useMutation'
import { usePagination } from '../hooks/usePagination'
import { PATHS } from '../routes/paths'
import { canPublish } from '../routes/roles'
import { ENDPOINTS } from '../services/endpoints'
import { reservationService } from '../services/reservationService'
import type { CompanyDashboard, EquipmentSummary, PageResponse, Reservation } from '../types'
import { cn } from '../utils/cn'
import { addDays, daysBetween, formatCurrency, formatDateRange, formatNumber, parseIsoDate, todayIso } from '../utils/format'

const LIST_LIMIT = 4
const OWNER_RESERVATIONS_PAGE = 100

function startOfToday(): Date {
  return parseIsoDate(todayIso())
}

function plural(count: number, one: string, many: string): string {
  return `${formatNumber(count)} ${count === 1 ? one : many}`
}

function Card({ title, aside, children, className }: { title: string; aside?: ReactNode; children: ReactNode; className?: string }) {
  const id = `card-${title.toLowerCase().replace(/\W+/g, '-')}`
  return (
    <section aria-labelledby={id} className={cn('min-w-0 rounded-lg border border-line bg-sheet', className)}>
      <header className="flex min-h-12 flex-wrap items-center justify-between gap-x-3 gap-y-1 px-5 pt-4">
        <h2 id={id} className="text-base font-semibold text-ink">
          {title}
        </h2>
        {aside}
      </header>
      <div className="px-5 pt-2 pb-4">{children}</div>
    </section>
  )
}

function ViewAll({ to, children = 'Ver todas', className }: { to: string; children?: ReactNode; className?: string }) {
  return (
    <Link to={to} className={cn('text-sm font-medium text-slate-600 hover:text-ink hover:underline', className)}>
      {children}
    </Link>
  )
}

interface Kpi {
  label: string
  value: string
  isZero?: boolean
  caption: ReactNode
}

function KpiStrip({ items }: { items: Kpi[] }) {
  return (
    <dl className="grid grid-cols-2 overflow-hidden rounded-lg border border-line lg:grid-cols-4">
      {items.map((item, index) => (
        <div
          key={item.label}
          className={cn(
            'flex flex-col gap-1 px-5 py-4',
            index % 2 === 1 && 'border-l border-line',
            index >= 2 && 'border-t border-line lg:border-t-0',
            index === 2 && 'lg:border-l',
          )}
        >
          <dt className="text-[13px] font-medium text-slate-600">{item.label}</dt>
          <dd className={cn('num text-[28px] leading-9 font-normal', item.isZero ? 'text-slate-500' : 'text-ink')}>{item.value}</dd>
          <dd className="text-xs text-slate-500">{item.caption}</dd>
        </div>
      ))}
    </dl>
  )
}

function PendingRequests({ requests, onChanged }: { requests: Reservation[]; onChanged: () => void }) {
  const confirm = useMutation((reservation: Reservation) => reservationService.confirm(reservation.id), {
    successMessage: (reservation) => `Reserva confirmada: ${reservation.equipmentName}`,
    errorTitle: 'No pudimos confirmar la reserva',
    onSuccess: onChanged,
  })

  if (requests.length === 0) {
    return <p className="rounded-md border border-dashed border-slate-300 px-3 py-2.5 text-sm text-slate-600">Ninguna solicitud espera tu respuesta.</p>
  }

  return (
    <ul>
      {requests.slice(0, LIST_LIMIT).map((request) => (
        <li key={request.id} className="flex min-h-16 flex-wrap items-center gap-x-4 gap-y-2 border-t border-rule py-2.5 first:border-t-0">
          <div className="min-w-0 flex-1 basis-56">
            <p className="flex items-baseline justify-between gap-3 text-sm">
              <span className="truncate font-medium text-ink">{request.equipmentName}</span>
              <span className="num shrink-0 text-ink">{formatCurrency(request.rentalAmount, request.currency)}</span>
            </p>
            <p className="truncate text-[13px] text-slate-500">
              {request.renterCompanyName} · {formatDateRange(request.startDate, request.endDate)} · {plural(request.totalDays, 'día', 'días')}
            </p>
          </div>
          <div className="flex shrink-0 items-center gap-1">
            <Button size="sm" variant="secondary" onClick={() => void confirm.mutate(request)} isLoading={confirm.isLoading}>
              Confirmar
            </Button>
            <Link
              to={`${PATHS.reservations}?role=OWNER&status=PENDING`}
              className="flex h-8 items-center rounded-md px-2 text-sm text-slate-600 hover:bg-raised hover:text-ink"
            >
              Rechazar…
            </Link>
          </div>
        </li>
      ))}
    </ul>
  )
}

function UpcomingHandovers({ handovers, today }: { handovers: Reservation[]; today: Date }) {
  if (handovers.length === 0) {
    return <p className="rounded-md border border-dashed border-slate-300 px-3 py-2.5 text-sm text-slate-600">No tienes entregas confirmadas en las próximas 4 semanas.</p>
  }
  return (
    <ul>
      {handovers.slice(0, LIST_LIMIT).map((handover) => {
        const inDays = daysBetween(today, parseIsoDate(handover.startDate))
        return (
          <li key={handover.id} className="flex min-h-16 items-center gap-4 border-t border-rule py-2.5 first:border-t-0">
            <div className="w-16 shrink-0">
              <p className="num text-sm font-medium text-ink">{parseIsoDate(handover.startDate).toLocaleDateString('es-PE', { day: 'numeric', month: 'short' })}</p>
              <p className="text-xs text-slate-500">{inDays === 0 ? 'hoy' : inDays === 1 ? 'mañana' : `en ${inDays} días`}</p>
            </div>
            <div className="min-w-0 flex-1">
              <p className="truncate text-sm font-medium text-ink">{handover.equipmentName}</p>
              <p className="truncate text-[13px] text-slate-500">
                {handover.renterCompanyName} · {plural(handover.totalDays, 'día', 'días')}
              </p>
            </div>
            <span
              className={cn(
                'inline-flex h-5 flex-none items-center rounded-full px-2 text-xs font-medium whitespace-nowrap',
                handover.paymentStatus === 'PAID' ? 'bg-[#dcfae5] text-[#04250e]' : 'bg-active text-slate-700',
              )}
            >
              {handover.paymentStatus === 'PAID' ? 'Pagada' : 'Pago pendiente'}
            </span>
          </li>
        )
      })}
    </ul>
  )
}

export default function DashboardPage() {
  const { user } = useAuth()
  const today = startOfToday()
  const windowEnd = addDays(today, TIMELINE_DAYS - 1)
  const { page, size, setPage, setSize } = usePagination()
  const isWide = useMediaQuery('(min-width: 640px)')
  const timelineDays = isWide ? TIMELINE_DAYS : COMPACT_TIMELINE_DAYS

  const metrics = useFetch<CompanyDashboard>(ENDPOINTS.myDashboard)
  const fleet = useFetch<PageResponse<EquipmentSummary>>(user ? ENDPOINTS.companyEquipment(user.companyId) : null, { page, size })
  const owned = useFetch<PageResponse<Reservation>>(ENDPOINTS.reservations, { role: 'OWNER', size: OWNER_RESERVATIONS_PAGE })

  if (!user) return null
  const publisher = canPublish(user.role)

  const ownerReservations = owned.data?.content ?? []
  const requests = ownerReservations
    .filter((reservation) => reservation.status === 'PENDING')
    .sort((a, b) => a.startDate.localeCompare(b.startDate))
  const handovers = ownerReservations
    .filter((reservation) => {
      const start = parseIsoDate(reservation.startDate)
      return reservation.status === 'CONFIRMED' && start >= today && start <= windowEnd
    })
    .sort((a, b) => a.startDate.localeCompare(b.startDate))
  const bookedMachines = new Set(
    ownerReservations
      .filter((reservation) => ['CONFIRMED', 'IN_PROGRESS'].includes(reservation.status))
      .filter((reservation) => parseIsoDate(reservation.endDate) >= today && parseIsoDate(reservation.startDate) <= windowEnd)
      .map((reservation) => reservation.equipmentId),
  ).size

  const nextRequest = requests[0]
  const finding = !owned.data
    ? 'Cargando tus reservas…'
    : nextRequest
      ? `${plural(requests.length, 'solicitud espera', 'solicitudes esperan')} tu respuesta; la más próxima empieza el ${parseIsoDate(nextRequest.startDate).toLocaleDateString('es-PE', { day: 'numeric', month: 'long' })}.`
      : handovers[0]
        ? `Sin solicitudes pendientes. Próxima entrega: ${handovers[0].equipmentName}, el ${parseIsoDate(handovers[0].startDate).toLocaleDateString('es-PE', { day: 'numeric', month: 'long' })}.`
        : 'Sin solicitudes pendientes ni entregas en las próximas 4 semanas.'

  const data = metrics.data
  const kpis: Kpi[] = data
    ? [
        {
          label: 'Solicitudes por revisar',
          value: formatNumber(data.pendingRequestsAsOwner),
          isZero: data.pendingRequestsAsOwner === 0,
          caption: data.pendingRequestsAsOwner > 0 ? <ViewAll className="text-xs text-ink underline" to={`${PATHS.reservations}?role=OWNER&status=PENDING`}>Revisar todas</ViewAll> : 'Nada pendiente',
        },
        {
          label: 'Equipos publicados',
          value: formatNumber(data.publishedEquipment),
          isZero: data.publishedEquipment === 0,
          caption: `${plural(bookedMachines, 'reservado', 'reservados')} en las próximas 4 semanas`,
        },
        {
          label: 'Ingresos estimados',
          value: formatCurrency(data.estimatedRevenue, data.currency),
          isZero: data.estimatedRevenue === 0,
          caption: 'Alquileres confirmados, en curso y completados',
        },
        {
          label: 'Mis alquileres activos',
          value: formatNumber(data.activeReservationsAsRenter),
          isZero: data.activeReservationsAsRenter === 0,
          caption:
            data.activeReservationsAsRenter === 0 ? (
              <ViewAll className="text-xs text-ink underline" to={PATHS.catalog}>Buscar maquinaria</ViewAll>
            ) : (
              <ViewAll className="text-xs text-ink underline" to={`${PATHS.reservations}?role=RENTER`}>Ver mis alquileres</ViewAll>
            ),
        },
      ]
    : []

  return (
    <div className="mx-auto max-w-[1200px] space-y-6">
      <header className="flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between">
        <div>
          <h1 className="text-[22px] leading-7 font-semibold text-ink">Panel</h1>
          <p className="mt-1 text-sm text-slate-600">{finding}</p>
        </div>
        {publisher && (
          <Link
            to={PATHS.publishEquipment}
            className="inline-flex h-9 shrink-0 items-center gap-2 self-start rounded-md bg-ink px-3.5 text-sm font-medium text-white transition-colors hover:bg-ink-hover"
          >
            <Plus className="size-4" aria-hidden="true" /> Publicar maquinaria
          </Link>
        )}
      </header>

      {metrics.error ? (
        <ErrorState error={metrics.error} onRetry={metrics.refetch} title="No pudimos cargar tus métricas" />
      ) : data ? (
        <KpiStrip items={kpis} />
      ) : (
        <div className="h-[118px] rounded-lg border border-line bg-raised" aria-busy="true" aria-label="Cargando métricas" />
      )}

      <div className="grid gap-6 xl:grid-cols-[3fr_2fr]">
        <Card title="Solicitudes por revisar" aside={requests.length > LIST_LIMIT && <ViewAll to={`${PATHS.reservations}?role=OWNER&status=PENDING`} />}>
          {owned.error ? (
            <ErrorState error={owned.error} onRetry={owned.refetch} />
          ) : (
            <PendingRequests
              requests={requests}
              onChanged={() => {
                owned.refetch()
                metrics.refetch()
              }}
            />
          )}
        </Card>
        <Card title="Próximas entregas" aside={<ViewAll to={`${PATHS.reservations}?role=OWNER&status=CONFIRMED`} />}>
          {owned.error ? <ErrorState error={owned.error} onRetry={owned.refetch} /> : <UpcomingHandovers handovers={handovers} today={today} />}
        </Card>
      </div>

      <Card title={`Ocupación de mi flota · próximas ${timelineDays / 7} semanas`} aside={<TimelineLegend />}>
        {fleet.error && <ErrorState error={fleet.error} onRetry={fleet.refetch} />}
        {!fleet.error && fleet.data && fleet.data.totalElements === 0 && (
          <div className="flex flex-col items-start gap-3 rounded-md border border-dashed border-slate-300 px-4 py-4 sm:flex-row sm:items-center sm:justify-between">
            <p className="text-sm text-slate-600">
              {publisher
                ? 'Aún no publicas maquinaria. Cuando lo hagas, aquí verás qué días está reservada cada máquina.'
                : 'Tu empresa aún no publica maquinaria. Mientras tanto, explora lo que ofrecen otras empresas.'}
            </p>
            <Link
              to={publisher ? PATHS.publishEquipment : PATHS.catalog}
              className="inline-flex h-8 shrink-0 items-center gap-1.5 rounded-md border border-slate-300 px-3 text-sm font-medium text-ink hover:bg-raised"
            >
              {publisher ? <Plus className="size-4" aria-hidden="true" /> : <Search className="size-4" aria-hidden="true" />}
              {publisher ? 'Publicar maquinaria' : 'Explorar catálogo'}
            </Link>
          </div>
        )}
        {!fleet.error && fleet.data && fleet.data.totalElements > 0 && (
          <>
            <FleetTimeline equipment={fleet.data.content} reservations={ownerReservations} from={today} days={timelineDays} compact={!isWide} />
            {fleet.data.totalPages > 1 && (
              <div className="mt-3">
                <Pagination pageData={fleet.data} onPageChange={setPage} onSizeChange={setSize} isDisabled={fleet.isLoading} />
              </div>
            )}
          </>
        )}
        {!fleet.error && !fleet.data && <div className="h-56 rounded-md bg-raised" aria-busy="true" aria-label="Cargando tu flota" />}
      </Card>
    </div>
  )
}
