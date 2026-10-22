import { ArrowRight, CalendarClock, ClipboardList, Coins, PackageOpen, Plus, Search, Truck } from 'lucide-react'
import { Link } from 'react-router-dom'
import { ErrorState } from '../components/common/ErrorState'
import { EmptyState } from '../components/common/EmptyState'
import { PageHeader } from '../components/common/PageHeader'
import { Pagination } from '../components/common/Pagination'
import { SkeletonLoader } from '../components/common/SkeletonLoader'
import { StatCard } from '../components/common/StatCard'
import { StatusBadge } from '../components/common/StatusBadge'
import { EquipmentImage } from '../components/equipment/EquipmentImage'
import { useAuth } from '../hooks/useAuth'
import { useFetch } from '../hooks/useFetch'
import { usePagination } from '../hooks/usePagination'
import { buildPath, PATHS } from '../routes/paths'
import { canPublish } from '../routes/roles'
import { ENDPOINTS } from '../services/endpoints'
import type { CompanyDashboard, EquipmentSummary, PageResponse } from '../types'
import { cn } from '../utils/cn'
import { formatCurrency, formatNumber } from '../utils/format'

function DashboardStats() {
  const { data, error, isLoading, refetch } = useFetch<CompanyDashboard>(ENDPOINTS.myDashboard)

  if (error) return <ErrorState error={error} onRetry={refetch} title="No pudimos cargar tus métricas" />
  if (isLoading || !data) {
    return (
      <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        {[0, 1, 2, 3].map((index) => (
          <div key={index} className="h-36 animate-pulse rounded-2xl bg-slate-200" />
        ))}
      </div>
    )
  }

  return (
    <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
      <StatCard
        label="Ingresos estimados"
        value={formatCurrency(data.estimatedRevenue, data.currency)}
        icon={Coins}
        tone="green"
        hint="Reservas confirmadas, en curso y completadas"
      />
      <StatCard
        label="Equipos publicados"
        value={formatNumber(data.publishedEquipment)}
        icon={Truck}
        tone="brand"
        hint={`${formatNumber(data.activeReservationsAsOwner)} alquilados ahora`}
      />
      <StatCard
        label="Solicitudes por revisar"
        value={formatNumber(data.pendingRequestsAsOwner)}
        icon={ClipboardList}
        tone={data.pendingRequestsAsOwner > 0 ? 'brand' : 'slate'}
        action={
          data.pendingRequestsAsOwner > 0 && (
            <Link
              to={`${PATHS.reservations}?role=OWNER&status=PENDING`}
              className="flex items-center gap-1 font-semibold text-brand-700 hover:underline"
            >
              Revisar <ArrowRight className="size-3" aria-hidden="true" />
            </Link>
          )
        }
      />
      <StatCard
        label="Mis alquileres activos"
        value={formatNumber(data.activeReservationsAsRenter)}
        icon={CalendarClock}
        tone="blue"
        action={
          <Link to={`${PATHS.reservations}?role=RENTER`} className="font-semibold text-sky-700 hover:underline">
            Ver
          </Link>
        }
      />
    </div>
  )
}

const PRIMARY_LINK =
  'inline-flex items-center gap-2 rounded-lg bg-brand-500 px-4 py-2 text-sm font-semibold text-slate-900 transition-colors hover:bg-brand-400'

function PublishLink() {
  return (
    <Link to={PATHS.publishEquipment} className={PRIMARY_LINK}>
      <Plus className="size-4" aria-hidden="true" /> Publicar maquinaria
    </Link>
  )
}

function MyEquipmentSection({ companyId, canPublishEquipment }: { companyId: number; canPublishEquipment: boolean }) {
  const { page, size, setPage, setSize } = usePagination(10)
  const { data, error, isLoading, refetch } = useFetch<PageResponse<EquipmentSummary>>(
    ENDPOINTS.companyEquipment(companyId),
    { page, size },
  )

  return (
    <section aria-labelledby="my-equipment-title" className="space-y-4 rounded-2xl border border-slate-200 bg-white p-4 shadow-sm sm:p-6">
      <h2 id="my-equipment-title" className="text-lg font-semibold text-slate-900">
        Mis maquinarias
      </h2>

      {error && <ErrorState error={error} onRetry={refetch} />}
      {!error && !data && isLoading && <SkeletonLoader variant="list" count={4} />}
      {!error && data && data.totalElements === 0 && (
        <EmptyState
          icon={PackageOpen}
          title="Aún no publicas maquinaria"
          description={
            canPublishEquipment
              ? 'Publica tu primer equipo y empieza a recibir solicitudes de otras constructoras.'
              : 'Cuando tu empresa publique equipos aparecerán aquí. Mientras tanto, explora lo que ofrecen otras empresas.'
          }
          action={
            canPublishEquipment ? (
              <PublishLink />
            ) : (
              <Link to={PATHS.catalog} className={PRIMARY_LINK}>
                <Search className="size-4" aria-hidden="true" /> Explorar catálogo
              </Link>
            )
          }
        />
      )}
      {!error && data && data.totalElements > 0 && (
        <>
          <ul className={cn('divide-y divide-slate-100 transition-opacity', isLoading && 'opacity-60')}>
            {data.content.map((equipment) => (
              <li key={equipment.id}>
                <Link
                  to={buildPath.equipmentDetail(equipment.id)}
                  className="-mx-2 flex items-center gap-4 rounded-lg px-2 py-3 hover:bg-slate-50"
                >
                  <EquipmentImage src={equipment.primaryImageUrl} alt={equipment.name} className="size-14 shrink-0 rounded-lg" />
                  <div className="min-w-0 flex-1">
                    <p className="truncate font-medium text-slate-900">{equipment.name}</p>
                    <p className="truncate text-sm text-slate-500">
                      {equipment.brand} · {equipment.model} · {equipment.city}
                    </p>
                  </div>
                  <div className="hidden text-right sm:block">
                    <p className="font-semibold text-slate-900">{formatCurrency(equipment.dailyRate, equipment.currency)}</p>
                    <p className="text-xs text-slate-500">por día</p>
                  </div>
                  <StatusBadge kind="equipment" status={equipment.status} />
                </Link>
              </li>
            ))}
          </ul>
          <Pagination pageData={data} onPageChange={setPage} onSizeChange={setSize} isDisabled={isLoading} />
        </>
      )}
    </section>
  )
}

export default function DashboardPage() {
  const { user } = useAuth()
  if (!user) return null

  return (
    <div className="mx-auto max-w-6xl space-y-8">
      <PageHeader
        title={`Hola, ${user.companyName}`}
        description="Este es el resumen de tu actividad en RentEquip."
        actions={canPublish(user.role) && <PublishLink />}
      />
      <DashboardStats />
      <MyEquipmentSection companyId={user.companyId} canPublishEquipment={canPublish(user.role)} />
    </div>
  )
}
