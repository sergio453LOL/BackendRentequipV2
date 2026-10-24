import { Link } from 'react-router-dom'
import { buildPath } from '../../routes/paths'
import type { EquipmentSummary } from '../../types'
import { formatCurrency } from '../../utils/format'
import { StatusBadge } from '../common/StatusBadge'
import { EquipmentImage } from './EquipmentImage'

/** A machine in the catalogue: its photo first, then what it is, where it is and what it costs a day. */
export function EquipmentCard({ equipment }: { equipment: EquipmentSummary }) {
  return (
    <Link
      to={buildPath.equipmentDetail(equipment.id)}
      className="group flex h-full flex-col overflow-hidden rounded-lg border border-line bg-sheet transition-colors hover:border-slate-300 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ink"
    >
      <div className="relative overflow-hidden bg-raised">
        <EquipmentImage
          src={equipment.primaryImageUrl}
          alt={equipment.name}
          className="aspect-[4/3] w-full transition-transform duration-300 group-hover:scale-[1.03]"
        />
        {/* "Disponible" on every card is noise: only a machine that cannot be booked carries a pill. */}
        {equipment.status !== 'AVAILABLE' && (
          <span className="absolute top-2 left-2">
            <StatusBadge kind="equipment" status={equipment.status} />
          </span>
        )}
      </div>
      <div className="flex flex-1 flex-col gap-1 px-4 pt-3 pb-4">
        <h3 className="line-clamp-2 text-[15px] leading-5 font-medium text-ink group-hover:underline">{equipment.name}</h3>
        <p className="truncate text-[13px] text-slate-500">
          {equipment.city} · {equipment.ownerCompanyName}
        </p>
        <p className="num mt-auto pt-2 text-base font-semibold text-ink">
          {formatCurrency(equipment.dailyRate, equipment.currency)}
          <span className="text-[13px] font-normal text-slate-500"> / día</span>
        </p>
      </div>
    </Link>
  )
}
