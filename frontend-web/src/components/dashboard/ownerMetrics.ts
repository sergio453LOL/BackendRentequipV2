import type { Reservation } from '../../types'
import { addDays, daysBetween, parseIsoDate } from '../../utils/format'

/** Reservations that earn money for the owner (the request was accepted). */
const EARNING = new Set(['CONFIRMED', 'IN_PROGRESS', 'COMPLETED'])

export interface MonthRevenue {
  /** First day of the month. */
  month: Date
  amount: number
  isCurrent: boolean
  isFuture: boolean
}

/** Rental days of a reservation that fall inside [from, to], both included. */
function overlapDays(reservation: Reservation, from: Date, to: Date): number {
  const start = parseIsoDate(reservation.startDate)
  const end = parseIsoDate(reservation.endDate)
  const first = start > from ? start : from
  const last = end < to ? end : to
  return last < first ? 0 : daysBetween(first, last) + 1
}

/**
 * Rental income per calendar month, counting each rented day in the month it happens (days × daily rate),
 * from `monthsBack` months ago to `monthsAhead` months ahead.
 */
export function revenueByMonth(reservations: Reservation[], today: Date, monthsBack = 2, monthsAhead = 3): MonthRevenue[] {
  const earning = reservations.filter((reservation) => EARNING.has(reservation.status))
  return Array.from({ length: monthsBack + monthsAhead + 1 }, (_, index) => {
    const offset = index - monthsBack
    const month = new Date(today.getFullYear(), today.getMonth() + offset, 1)
    const monthEnd = new Date(month.getFullYear(), month.getMonth() + 1, 0)
    const amount = earning.reduce((sum, reservation) => sum + overlapDays(reservation, month, monthEnd) * reservation.dailyRate, 0)
    return { month, amount, isCurrent: offset === 0, isFuture: offset > 0 }
  })
}

/** Share of machine-days booked (confirmed or in progress) over the next `days` days. */
export function fleetOccupancy(reservations: Reservation[], machineCount: number, today: Date, days = 30): number {
  if (machineCount === 0) return 0
  const windowEnd = addDays(today, days - 1)
  const booked = reservations
    .filter((reservation) => reservation.status === 'CONFIRMED' || reservation.status === 'IN_PROGRESS')
    .reduce((sum, reservation) => sum + overlapDays(reservation, today, windowEnd), 0)
  return Math.min(1, booked / (machineCount * days))
}
