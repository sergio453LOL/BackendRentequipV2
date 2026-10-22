import type { EquipmentStatus, ReservationRole, ReservationStatus } from '../types'

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
  MAINTENANCE: 'En mantenimiento',
  INACTIVE: 'Inactivo',
}

export const RESERVATION_ROLE_LABELS: Record<ReservationRole, string> = {
  RENTER: 'Mis solicitudes',
  OWNER: 'Reservas de mis equipos',
}

export const RESERVATION_STATUSES = Object.keys(RESERVATION_STATUS_LABELS) as ReservationStatus[]
