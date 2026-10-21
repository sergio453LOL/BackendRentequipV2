import type { Payment, Reservation } from '../types'
import { reservationCode } from './format'

/**
 * Builds the PDF receipt in the browser and starts the download. The PDF library is large, so it is only
 * loaded the first time somebody asks for a receipt.
 */
export async function downloadReceipt(reservation: Reservation, payments: Payment[]): Promise<void> {
  const { createReceiptBlob } = await import('../components/receipts/renderReceipt')
  const blob = await createReceiptBlob(reservation, payments)
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = `comprobante-${reservationCode(reservation.id)}.pdf`
  document.body.appendChild(link)
  link.click()
  link.remove()
  setTimeout(() => URL.revokeObjectURL(url), 1_000)
}
