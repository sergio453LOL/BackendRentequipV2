import { pdf } from '@react-pdf/renderer'
import type { Payment, Reservation } from '../../types'
import { ReceiptDocument } from './ReceiptPDF'

export async function createReceiptBlob(reservation: Reservation, payments: Payment[]): Promise<Blob> {
  return pdf(<ReceiptDocument reservation={reservation} payments={payments} issuedAt={new Date()} />).toBlob()
}
