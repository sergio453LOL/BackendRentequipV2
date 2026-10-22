import type { IsoDate } from '../types/api'

const MS_PER_DAY = 86_400_000
const EARTH_RADIUS_KM = 6371

export function formatCurrency(amount: number, currency = 'PEN'): string {
  const symbol = currency === 'PEN' ? 'S/' : currency
  return `${symbol} ${amount.toLocaleString('es-PE', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`
}

function parseIsoDate(date: IsoDate): Date {
  const [year, month, day] = date.split('-').map(Number)
  return new Date(year, month - 1, day)
}

const MONTHS = ['ene', 'feb', 'mar', 'abr', 'may', 'jun', 'jul', 'ago', 'set', 'oct', 'nov', 'dic']

/** `2026-10-24` -> `24 oct 2026`, independent of the device's Intl support. */
export function formatDate(date: IsoDate): string {
  const parsed = parseIsoDate(date)
  return `${parsed.getDate()} ${MONTHS[parsed.getMonth()]} ${parsed.getFullYear()}`
}

export function toIsoDate(date: Date): IsoDate {
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${date.getFullYear()}-${month}-${day}`
}

export function todayIso(): IsoDate {
  return toIsoDate(new Date())
}

export function rentalDays(start: IsoDate, end: IsoDate): number {
  return Math.round((parseIsoDate(end).getTime() - parseIsoDate(start).getTime()) / MS_PER_DAY) + 1
}

export interface Coordinates {
  latitude: number
  longitude: number
}

/** Great-circle distance, the same formula the backend uses to sort the catalogue. */
export function distanceKm(from: Coordinates, to: Coordinates): number {
  const toRad = (degrees: number) => (degrees * Math.PI) / 180
  const dLat = toRad(to.latitude - from.latitude)
  const dLon = toRad(to.longitude - from.longitude)
  const a =
    Math.sin(dLat / 2) ** 2 + Math.cos(toRad(from.latitude)) * Math.cos(toRad(to.latitude)) * Math.sin(dLon / 2) ** 2
  return 2 * EARTH_RADIUS_KM * Math.asin(Math.sqrt(a))
}

export function formatDistance(km: number): string {
  return km < 1 ? `${Math.round(km * 1000)} m` : `${km < 10 ? km.toFixed(1) : Math.round(km)} km`
}
