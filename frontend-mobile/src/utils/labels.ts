import type { EquipmentStatus, HandoverStage, ReservationPaymentStatus, ReservationStatus } from '../types/api'

export const RESERVATION_STATUS_LABELS: Record<ReservationStatus, string> = {
  PENDING: 'Pendiente',
  CONFIRMED: 'Confirmada',
  IN_PROGRESS: 'En curso',
  COMPLETED: 'Completada',
  CANCELLED: 'Cancelada',
  REJECTED: 'Rechazada',
}

export const EQUIPMENT_STATUS_LABELS: Record<EquipmentStatus, string> = {
  AVAILABLE: 'Disponible',
  RENTED: 'Alquilado',
  MAINTENANCE: 'Mantenimiento',
  INACTIVE: 'Inactivo',
}

export const PAYMENT_STATUS_LABELS: Record<ReservationPaymentStatus, string> = {
  UNPAID: 'Pago pendiente',
  PAID: 'Pagado',
  REFUNDED: 'Reembolsado',
}

export const HANDOVER_STAGE_LABELS: Record<HandoverStage, string> = {
  DELIVERY: 'Entrega',
  RETURN: 'Devolución',
}
