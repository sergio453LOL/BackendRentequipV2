import type { EquipmentStatus, ReservationStatus } from '../../types'
import { cn } from '../../utils/cn'
import { EQUIPMENT_STATUS_LABELS, RESERVATION_STATUS_LABELS } from '../../utils/labels'

type Tone = 'green' | 'amber' | 'blue' | 'slate' | 'red'

const TONE_CLASSES: Record<Tone, string> = {
  green: 'bg-emerald-50 text-emerald-700 ring-emerald-600/20',
  amber: 'bg-amber-50 text-amber-800 ring-amber-600/20',
  blue: 'bg-sky-50 text-sky-700 ring-sky-600/20',
  slate: 'bg-slate-100 text-slate-600 ring-slate-500/20',
  red: 'bg-red-50 text-red-700 ring-red-600/20',
}

const RESERVATION_TONES: Record<ReservationStatus, Tone> = {
  PENDING: 'amber',
  CONFIRMED: 'blue',
  IN_PROGRESS: 'green',
  COMPLETED: 'slate',
  CANCELLED: 'red',
  REJECTED: 'red',
}

const EQUIPMENT_TONES: Record<EquipmentStatus, Tone> = {
  AVAILABLE: 'green',
  RENTED: 'blue',
  MAINTENANCE: 'amber',
  INACTIVE: 'slate',
}

type StatusBadgeProps =
  | { kind: 'reservation'; status: ReservationStatus }
  | { kind: 'equipment'; status: EquipmentStatus }

export function StatusBadge(props: StatusBadgeProps) {
  const [tone, label] =
    props.kind === 'reservation'
      ? [RESERVATION_TONES[props.status], RESERVATION_STATUS_LABELS[props.status]]
      : [EQUIPMENT_TONES[props.status], EQUIPMENT_STATUS_LABELS[props.status]]

  return (
    <span className={cn('inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-medium ring-1 ring-inset', TONE_CLASSES[tone])}>
      {label}
    </span>
  )
}
