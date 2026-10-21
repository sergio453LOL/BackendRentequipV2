import { StyleSheet, Text, View } from 'react-native'
import type { EquipmentStatus, ReservationPaymentStatus, ReservationStatus } from '../types/api'
import { EQUIPMENT_STATUS_LABELS, PAYMENT_STATUS_LABELS, RESERVATION_STATUS_LABELS } from '../utils/labels'

type Tone = 'green' | 'amber' | 'blue' | 'gray' | 'red'

const TONES: Record<Tone, { background: string; text: string }> = {
  green: { background: '#ECFDF5', text: '#047857' },
  amber: { background: '#FFFBEB', text: '#92400E' },
  blue: { background: '#F0F9FF', text: '#0369A1' },
  gray: { background: '#F1F5F9', text: '#475569' },
  red: { background: '#FEF2F2', text: '#B91C1C' },
}

export function Badge({ label, tone }: { label: string; tone: Tone }) {
  const palette = TONES[tone]
  return (
    <View style={[styles.badge, { backgroundColor: palette.background }]}>
      <Text style={[styles.text, { color: palette.text }]}>{label}</Text>
    </View>
  )
}

const RESERVATION_TONES: Record<ReservationStatus, Tone> = {
  PENDING: 'amber',
  CONFIRMED: 'blue',
  IN_PROGRESS: 'green',
  COMPLETED: 'gray',
  CANCELLED: 'red',
  REJECTED: 'red',
}

const EQUIPMENT_TONES: Record<EquipmentStatus, Tone> = {
  AVAILABLE: 'green',
  RENTED: 'blue',
  MAINTENANCE: 'amber',
  INACTIVE: 'gray',
}

const PAYMENT_TONES: Record<ReservationPaymentStatus, Tone> = { UNPAID: 'amber', PAID: 'green', REFUNDED: 'gray' }

export const ReservationStatusBadge = ({ status }: { status: ReservationStatus }) => (
  <Badge label={RESERVATION_STATUS_LABELS[status]} tone={RESERVATION_TONES[status]} />
)

export const EquipmentStatusBadge = ({ status }: { status: EquipmentStatus }) => (
  <Badge label={EQUIPMENT_STATUS_LABELS[status]} tone={EQUIPMENT_TONES[status]} />
)

export const PaymentStatusBadge = ({ status }: { status: ReservationPaymentStatus }) => (
  <Badge label={PAYMENT_STATUS_LABELS[status]} tone={PAYMENT_TONES[status]} />
)

const styles = StyleSheet.create({
  badge: { alignSelf: 'flex-start', borderRadius: 999, paddingHorizontal: 10, paddingVertical: 3 },
  text: { fontSize: 12, fontWeight: '700' },
})
