import { Building2, MapPin } from 'lucide-react'
import { Link } from 'react-router-dom'
import { buildPath } from '../../routes/paths'
import type { EquipmentSummary } from '../../types'
import { formatCurrency } from '../../utils/format'
import { StatusBadge } from '../common/StatusBadge'
import { EquipmentImage } from './EquipmentImage'

export function EquipmentCard({ equipment }: { equipment: EquipmentSummary }) {
  return (
    <Link
      to={buildPath.equipmentDetail(equipment.id)}
      className="group flex flex-col overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-sm transition hover:-translate-y-0.5 hover:shadow-lg focus-visible:outline-2 focus-visible:outline-brand-500"
    >
      <div className="relative">
        <EquipmentImage
          src={equipment.primaryImageUrl}
          alt={equipment.name}
          className="aspect-[4/3] w-full transition-transform duration-300 group-hover:scale-105"
        />
        <span className="absolute top-3 left-3">
          <StatusBadge kind="equipment" status={equipment.status} />
        </span>
      </div>
      <div className="flex flex-1 flex-col gap-3 p-4">
        <div>
          <h3 className="line-clamp-1 font-semibold text-slate-900 group-hover:text-brand-700">{equipment.name}</h3>
          <p className="text-sm text-slate-500">
            {equipment.brand} · {equipment.model}
          </p>
        </div>
        <div className="space-y-1 text-xs text-slate-500">
          <p className="flex items-center gap-1.5">
            <MapPin className="size-3.5" aria-hidden="true" />
            {equipment.city}
          </p>
          <p className="flex items-center gap-1.5">
            <Building2 className="size-3.5" aria-hidden="true" />
            <span className="line-clamp-1">{equipment.ownerCompanyName}</span>
          </p>
        </div>
        <p className="mt-auto text-lg font-bold text-slate-900">
          {formatCurrency(equipment.dailyRate, equipment.currency)}
          <span className="text-sm font-normal text-slate-500"> / día</span>
        </p>
      </div>
    </Link>
  )
}
