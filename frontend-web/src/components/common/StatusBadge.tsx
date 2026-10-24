import type { EquipmentStatus, ReservationStatus } from '../../types'
import { cn } from '../../utils/cn'
import { EQUIPMENT_STATUS_LABELS, RESERVATION_STATUS_LABELS } from '../../utils/labels'

type Tone = 'green' | 'amber' | 'ink' | 'slate' | 'red'

// Pale tint with dark ink of the same hue; a confirmed booking is the dark neutral, a finished one the pale.
const TONE_CLASSES: Record<Tone, string> = {
  green: 'bg-[#dcfae5] text-[#04250e]',
  amber: 'bg-[#fffbeb] text-[#92400e] ring-1 ring-inset ring-[#fde68a]',
  ink: 'bg-slate-800 text-white',
  slate: 'bg-active text-slate-700',
  red: 'bg-[#ffedea] text-[#831c1e]',
}

const RESERVATION_TONES: Record<ReservationStatus, Tone> = {
  PENDING: 'amber',
  CONFIRMED: 'ink',
  IN_PROGRESS: 'green',
  COMPLETED: 'slate',
  CANCELLED: 'red',
  REJECTED: 'red',
}

const EQUIPMENT_TONES: Record<EquipmentStatus, Tone> = {
  AVAILABLE: 'green',
  RENTED: 'ink',
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
    <span className={cn('inline-flex h-5 flex-none items-center rounded-full px-2 text-xs font-medium whitespace-nowrap', TONE_CLASSES[tone])}>
      {label}
    </span>
  )
}
