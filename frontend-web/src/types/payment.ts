import type { IsoDateTime } from './common'

export type PaymentStatus = 'PENDING' | 'AUTHORIZED' | 'COMPLETED' | 'FAILED' | 'REFUNDED'

export type PaymentType = 'RENTAL' | 'SECURITY_DEPOSIT' | 'REFUND'

/** Mirrors `PaymentResponse`. The backend does not expose a payments endpoint yet. */
export interface Payment {
  id: number
  reservationId: number
  amount: number
  currency: string
  type: PaymentType
  status: PaymentStatus
  providerReference: string | null
  paidAt: IsoDateTime | null
  failureReason: string | null
  createdAt: IsoDateTime
}

/** Payment situation of a reservation as a whole, derived by the backend from its payments. */
export type ReservationPaymentStatus = 'UNPAID' | 'PAID' | 'REFUNDED'

export interface PaymentRequest {
  paymentToken: string
}
