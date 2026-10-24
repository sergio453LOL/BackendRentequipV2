import { CalendarClock, PackageOpen, Plus } from 'lucide-react'
import { Link } from 'react-router-dom'
import { EmptyState } from '../components/common/EmptyState'
import { ErrorState } from '../components/common/ErrorState'
import { PageHeader } from '../components/common/PageHeader'
import { Pagination } from '../components/common/Pagination'
import { SkeletonLoader } from '../components/common/SkeletonLoader'
import { StatusBadge } from '../components/common/StatusBadge'
import { EquipmentImage } from '../components/equipment/EquipmentImage'
import { useAuth } from '../hooks/useAuth'
import { useFetch } from '../hooks/useFetch'
import { usePagination } from '../hooks/usePagination'
import { buildPath, PATHS } from '../routes/paths'
import { canPublish } from '../routes/roles'
import { ENDPOINTS } from '../services/endpoints'
import type { EquipmentSummary, PageResponse, Reservation } from '../types'
import { daysBetween, formatCurrency, formatDateRange, formatNumber, parseIsoDate, todayIso } from '../utils/format'

const OCCUPANCY_WINDOW_DAYS = 30
const HOLDING = new Set(['CONFIRMED', 'IN_PROGRESS'])

interface MachineOutlook {
  bookedDays: number
  next: Reservation | undefined
  pendingRequests: number
}

/** Days booked in the next 30, the next confirmed rental and the requests still waiting, for one machine. */
function outlookFor(machineId: number, reservations: Reservation[], today: Date): MachineOutlook {
  const own = reservations.filter((reservation) => reservation.equipmentId === machineId)
  const booked = new Set<number>()
  for (const reservation of own) {
    if (!HOLDING.has(reservation.status)) continue
    const start = Math.max(0, daysBetween(today, parseIsoDate(reservation.startDate)))
    const end = Math.min(OCCUPANCY_WINDOW_DAYS - 1, daysBetween(today, parseIsoDate(reservation.endDate)))
    for (let day = start; day <= end; day++) booked.add(day)
  }
  const next = own
    .filter((reservation) => HOLDING.has(reservation.status) && parseIsoDate(reservation.endDate) >= today)
    .sort((a, b) => a.startDate.localeCompare(b.startDate))[0]
  const pendingRequests = own.filter((reservation) => reservation.status === 'PENDING').length
  return { bookedDays: booked.size, next, pendingRequests }
}

function PublishButton() {
  return (
    <Link
      to={PATHS.publishEquipment}
      className="inline-flex h-10 items-center gap-2 rounded-md bg-action px-4 text-sm font-semibold text-white transition-all duration-200 hover:bg-action-hover hover:shadow-md active:scale-[0.98]"
    >
      <Plus className="size-4" aria-hidden="true" /> Publicar maquinaria
    </Link>
  )
}

function MachineCard({ machine, outlook }: { machine: EquipmentSummary; outlook: MachineOutlook }) {
  const occupancy = outlook.bookedDays / OCCUPANCY_WINDOW_DAYS
  return (
    <article className="group flex flex-col overflow-hidden rounded-2xl border border-slate-200/80 bg-white transition-all duration-300 hover:-translate-y-0.5 hover:shadow-xl">
      <Link to={buildPath.equipmentDetail(machine.id)} className="relative block overflow-hidden" tabIndex={-1} aria-hidden="true">
        <EquipmentImage src={machine.primaryImageUrl} alt="" className="aspect-[16/10] w-full transition-transform duration-500 ease-out group-hover:scale-110" />
        <span className="absolute top-3 left-3">
          <StatusBadge kind="equipment" status={machine.status} />
        </span>
      </Link>
      <div className="flex flex-1 flex-col gap-4 p-4">
        <div className="flex items-start justify-between gap-3">
          <div className="min-w-0">
            <Link to={buildPath.equipmentDetail(machine.id)} className="line-clamp-1 font-semibold text-ink hover:text-accent-ink">
              {machine.name}
            </Link>
            <p className="text-sm text-slate-500">
              {machine.brand} · {machine.model} · {machine.city}
            </p>
          </div>
          <p className="num shrink-0 text-right text-base font-bold text-ink">
            {formatCurrency(machine.dailyRate, machine.currency)}
            <span className="block text-xs font-normal text-slate-500">por día</span>
          </p>
        </div>

        <div>
          <div className="mb-1.5 flex items-baseline justify-between text-xs">
            <span className="font-semibold text-slate-600">Ocupación próximos {OCCUPANCY_WINDOW_DAYS} días</span>
            <span className="num font-semibold text-ink">{Math.round(occupancy * 100)}%</span>
          </div>
          <div className="h-2 overflow-hidden rounded-full bg-slate-100" aria-hidden="true">
            <span className="block h-full rounded-full bg-brand transition-all duration-500" style={{ width: `${Math.round(occupancy * 100)}%` }} />
          </div>
        </div>

        <div className="mt-auto flex flex-wrap items-center justify-between gap-2 border-t border-slate-100 pt-3 text-sm">
          <span className="flex items-center gap-1.5 text-slate-600">
            <CalendarClock className="size-4 text-slate-400" aria-hidden="true" />
            {outlook.next ? `Próximo: ${formatDateRange(outlook.next.startDate, outlook.next.endDate)}` : 'Sin reservas confirmadas'}
          </span>
          {outlook.pendingRequests > 0 && (
            <Link
              to={`${PATHS.reservations}?role=OWNER&status=PENDING`}
              className="rounded-full bg-orange-50 px-2.5 py-0.5 text-xs font-semibold text-action ring-1 ring-orange-200 ring-inset hover:bg-orange-100"
            >
              {outlook.pendingRequests} {outlook.pendingRequests === 1 ? 'solicitud' : 'solicitudes'}
            </Link>
          )}
        </div>
      </div>
    </article>
  )
}

export default function FleetPage() {
  const { user } = useAuth()
  const { page, size, setPage, setSize } = usePagination()
  const today = parseIsoDate(todayIso())
  const fleet = useFetch<PageResponse<EquipmentSummary>>(user ? ENDPOINTS.companyEquipment(user.companyId) : null, { page, size })
  const owned = useFetch<PageResponse<Reservation>>(ENDPOINTS.reservations, { role: 'OWNER', size: 100 })

  if (!user) return null
  const publisher = canPublish(user.role)
  const reservations = owned.data?.content ?? []
  const total = fleet.data?.totalElements

  return (
    <div className="mx-auto max-w-[1200px] space-y-6">
      <PageHeader
        title="Mi flota"
        description={
          total === undefined
            ? 'Tus equipos publicados y qué tan ocupados están.'
            : `${formatNumber(total)} ${total === 1 ? 'equipo publicado' : 'equipos publicados'} · ocupación calculada con las reservas confirmadas.`
        }
        breadcrumbs={[{ label: 'Mi flota' }]}
        actions={publisher && <PublishButton />}
      />

      {fleet.error && <ErrorState error={fleet.error} onRetry={fleet.refetch} />}
      {!fleet.error && !fleet.data && <SkeletonLoader variant="card" count={6} />}
      {!fleet.error && fleet.data && fleet.data.totalElements === 0 && (
        <EmptyState
          icon={PackageOpen}
          title="Aún no publicas maquinaria"
          description={
            publisher
              ? 'Publica tu primer equipo con fotos y tarifas, y empieza a recibir solicitudes de otras constructoras.'
              : 'Pide a un administrador de tu empresa que publique los equipos.'
          }
          action={publisher && <PublishButton />}
        />
      )}
      {!fleet.error && fleet.data && fleet.data.totalElements > 0 && (
        <>
          <ul className="grid gap-5 sm:grid-cols-2 xl:grid-cols-3">
            {fleet.data.content.map((machine) => (
              <li key={machine.id}>
                <MachineCard machine={machine} outlook={outlookFor(machine.id, reservations, today)} />
              </li>
            ))}
          </ul>
          <Pagination pageData={fleet.data} onPageChange={setPage} onSizeChange={setSize} isDisabled={fleet.isLoading} />
        </>
      )}
    </div>
  )
}
