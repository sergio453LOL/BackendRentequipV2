import type { Payment, PaymentRequest } from '../types'
import { api } from './api'
import { ENDPOINTS } from './endpoints'

export const paymentService = {
  /** Captures the rental and holds the security deposit. Resolves with both payment records. */
  async pay(reservationId: number, paymentToken: string): Promise<Payment[]> {
    const body: PaymentRequest = { paymentToken }
    const { data } = await api.post<Payment[]>(ENDPOINTS.reservationPayments(reservationId), body)
    return data
  },

  async list(reservationId: number): Promise<Payment[]> {
    const { data } = await api.get<Payment[]>(ENDPOINTS.reservationPayments(reservationId))
    return data
  },
}
