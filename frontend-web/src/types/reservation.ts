import type { IsoDate, IsoDateTime, PageParams } from './common'
import type { ReservationPaymentStatus } from './payment'

export type ReservationStatus =
  | 'PENDING'
  | 'CONFIRMED'
  | 'IN_PROGRESS'
  | 'COMPLETED'
  | 'CANCELLED'
  | 'REJECTED'

/** Side of the marketplace the company is asking about when listing its reservations. */
export type ReservationRole = 'RENTER' | 'OWNER'

export interface Reservation {
  id: number
  equipmentId: number
  equipmentName: string
  renterCompanyId: number
  renterCompanyName: string
  startDate: IsoDate
  endDate: IsoDate
  totalDays: number
  dailyRate: number
  rentalAmount: number
  securityDeposit: number
  totalAmount: number
  currency: string
  status: ReservationStatus
  notes: string | null
  paymentStatus: ReservationPaymentStatus
  createdAt: IsoDateTime
}

export interface ReservationCreateRequest {
  equipmentId: number
  startDate: IsoDate
  endDate: IsoDate
  notes?: string
}

export interface ReservationStatusUpdateRequest {
  status: ReservationStatus
  reason?: string
}

export interface ReservationListParams extends PageParams {
  role?: ReservationRole
  status?: ReservationStatus
}

export interface Availability {
  equipmentId: number
  startDate: IsoDate
  endDate: IsoDate
  available: boolean
}

/** A period in which a machine cannot be booked (no renter data). */
export interface BlockedWindow {
  startDate: IsoDate
  endDate: IsoDate
  status: ReservationStatus
}
