import { ArrowLeft, Building2, CalendarPlus, MapPin, SearchX, ShieldCheck, Star } from 'lucide-react'
import { useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { Button } from '../components/common/Button'
import { EmptyState } from '../components/common/EmptyState'
import { ErrorState } from '../components/common/ErrorState'
import { PageHeader } from '../components/common/PageHeader'
import { StatusBadge } from '../components/common/StatusBadge'
import { ImageGallery } from '../components/equipment/ImageGallery'
import { SpecificationList } from '../components/equipment/SpecificationList'
import { ReservationRequestModal } from '../components/reservations/ReservationRequestModal'
import { useAuth } from '../hooks/useAuth'
import { useFetch } from '../hooks/useFetch'
import { PATHS } from '../routes/paths'
import { ENDPOINTS } from '../services/endpoints'
import type { Equipment, EquipmentRating, WithLinks } from '../types'
import { formatCurrency } from '../utils/format'

function parseId(value: string | undefined): number | null {
  const id = Number(value)
  return Number.isInteger(id) && id > 0 ? id : null
}

function NotFoundEquipment() {
  return (
    <EmptyState
      icon={SearchX}
      title="No encontramos esta maquinaria"
      description="Puede que haya sido retirada del catálogo o que el enlace sea incorrecto."
      action={
        <Link
          to={PATHS.catalog}
          className="inline-flex items-center gap-2 rounded-lg bg-brand-500 px-4 py-2 text-sm font-semibold text-slate-900 hover:bg-brand-400"
        >
          <ArrowLeft className="size-4" aria-hidden="true" /> Volver al catálogo
        </Link>
      }
    />
  )
}

function DetailSkeleton() {
  return (
    <div className="grid animate-pulse gap-8 lg:grid-cols-5">
      <div className="aspect-[4/3] rounded-2xl bg-slate-200 lg:col-span-3" />
      <div className="space-y-4 lg:col-span-2">
        <div className="h-8 w-3/4 rounded bg-slate-200" />
        <div className="h-4 w-1/2 rounded bg-slate-200" />
        <div className="h-48 rounded-2xl bg-slate-200" />
      </div>
    </div>
  )
}

export default function EquipmentDetailPage() {
  const { user } = useAuth()
  const equipmentId = parseId(useParams<{ id: string }>().id)
  const [isReserving, setIsReserving] = useState(false)

  const { data: equipment, error, isLoading, refetch } = useFetch<WithLinks<Equipment>>(
    equipmentId ? ENDPOINTS.equipment(equipmentId) : null,
  )
  const rating = useFetch<EquipmentRating>(equipmentId ? ENDPOINTS.equipmentRating : null, { equipmentId })

  if (equipmentId === null || error?.status === 404) return <NotFoundEquipment />
  if (error) return <ErrorState error={error} onRetry={refetch} />
  if (isLoading || !equipment) return <DetailSkeleton />

  const isOwnEquipment = equipment.ownerCompanyId === user?.companyId
  const isAvailable = equipment.status === 'AVAILABLE'
  const reserveBlockedReason = isOwnEquipment
    ? 'Este equipo es de tu empresa.'
    : !isAvailable
      ? 'Este equipo no está disponible para nuevas reservas.'
      : null
  const averageRating = rating.data?.averageRating ?? 0

  return (
    <div className="mx-auto max-w-6xl space-y-6">
      <PageHeader
        title={equipment.name}
        description={`${equipment.brand} · ${equipment.model}${equipment.manufactureYear ? ` · ${equipment.manufactureYear}` : ''}`}
        breadcrumbs={[{ label: 'Catálogo', to: PATHS.catalog }, { label: equipment.name }]}
      />

      <div className="grid gap-8 lg:grid-cols-5">
        <div className="space-y-8 lg:col-span-3">
          <ImageGallery images={equipment.imageUrls} alt={equipment.name} />

          {equipment.description && (
            <section aria-labelledby="description-title" className="space-y-2">
              <h2 id="description-title" className="text-lg font-semibold text-slate-900">
                Descripción
              </h2>
              <p className="whitespace-pre-line text-slate-600">{equipment.description}</p>
            </section>
          )}

          <section aria-labelledby="specs-title" className="space-y-3">
            <h2 id="specs-title" className="text-lg font-semibold text-slate-900">
              Especificaciones técnicas
            </h2>
            <dl className="grid grid-cols-2 gap-4 rounded-xl border border-slate-200 bg-white p-4 text-sm sm:grid-cols-3">
              <SpecItem label="Marca" value={equipment.brand} />
              <SpecItem label="Modelo" value={equipment.model} />
              <SpecItem label="Año" value={equipment.manufactureYear?.toString() ?? '—'} />
              <SpecItem label="N.º de serie" value={equipment.serialNumber ?? '—'} />
              <SpecItem label="Ciudad" value={equipment.city} />
              <SpecItem label="Moneda" value={equipment.currency} />
            </dl>
            {equipment.specifications ? (
              <SpecificationList specifications={equipment.specifications} />
            ) : (
              <p className="text-sm text-slate-500">El propietario no registró especificaciones adicionales.</p>
            )}
          </section>
        </div>

        <aside className="lg:col-span-2">
          <div className="space-y-5 rounded-2xl border border-slate-200 bg-white p-6 shadow-sm lg:sticky lg:top-24">
            <div className="flex items-center justify-between gap-3">
              <StatusBadge kind="equipment" status={equipment.status} />
              {averageRating > 0 && (
                <span className="flex items-center gap-1 text-sm font-medium text-slate-700" aria-label={`Calificación ${averageRating.toFixed(1)} de 5`}>
                  <Star className="size-4 fill-brand-400 text-brand-400" aria-hidden="true" />
                  {averageRating.toFixed(1)}
                </span>
              )}
            </div>

            <div>
              <p className="text-3xl font-bold text-slate-900">
                {formatCurrency(equipment.dailyRate, equipment.currency)}
                <span className="text-base font-normal text-slate-500"> / día</span>
              </p>
              {equipment.weeklyRate !== null && (
                <p className="text-sm text-slate-500">{formatCurrency(equipment.weeklyRate, equipment.currency)} por semana</p>
              )}
            </div>

            <ul className="space-y-2 text-sm text-slate-600">
              <li className="flex items-center gap-2">
                <ShieldCheck className="size-4 text-emerald-600" aria-hidden="true" />
                Garantía reembolsable de {formatCurrency(equipment.securityDeposit, equipment.currency)}
              </li>
              <li className="flex items-center gap-2">
                <MapPin className="size-4 text-slate-400" aria-hidden="true" />
                {[equipment.address, equipment.city].filter(Boolean).join(', ')}
              </li>
              <li className="flex items-center gap-2">
                <Building2 className="size-4 text-slate-400" aria-hidden="true" />
                {equipment.ownerCompanyName}
              </li>
            </ul>

            {equipment.categories.length > 0 && (
              <ul className="flex flex-wrap gap-2" aria-label="Categorías">
                {equipment.categories.map((category) => (
                  <li key={category} className="rounded-full bg-slate-100 px-3 py-1 text-xs font-medium text-slate-600">
                    {category}
                  </li>
                ))}
              </ul>
            )}

            <Button
              size="lg"
              fullWidth
              disabled={reserveBlockedReason !== null}
              onClick={() => setIsReserving(true)}
              leftIcon={<CalendarPlus className="size-5" />}
              title={reserveBlockedReason ?? undefined}
            >
              Solicitar reserva
            </Button>
            {reserveBlockedReason && <p className="text-center text-xs text-slate-500">{reserveBlockedReason}</p>}
          </div>
        </aside>
      </div>

      <ReservationRequestModal equipment={equipment} isOpen={isReserving} onClose={() => setIsReserving(false)} />
    </div>
  )
}

function SpecItem({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <dt className="text-xs text-slate-500">{label}</dt>
      <dd className="font-medium break-words text-slate-900">{value}</dd>
    </div>
  )
}
