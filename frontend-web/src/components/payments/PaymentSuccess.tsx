import { CheckCircle2, FileDown } from 'lucide-react'
import { useState } from 'react'
import { useToast } from '../../hooks/useToast'
import type { Payment, Reservation } from '../../types'
import { downloadReceipt } from '../../utils/downloadReceipt'
import { formatCurrency } from '../../utils/format'
import { Button } from '../common/Button'

interface PaymentSuccessProps {
  reservation: Reservation
  payments: Payment[]
  onDone: () => void
  doneLabel?: string
}

export function PaymentSuccess({ reservation, payments, onDone, doneLabel = 'Listo' }: PaymentSuccessProps) {
  const toast = useToast()
  const [isGenerating, setIsGenerating] = useState(false)

  async function handleDownload() {
    setIsGenerating(true)
    try {
      await downloadReceipt(reservation, payments)
    } catch {
      toast.error('No pudimos generar el comprobante', 'Inténtalo nuevamente en unos segundos.')
    } finally {
      setIsGenerating(false)
    }
  }

  return (
    <div className="flex flex-col items-center gap-4 py-4 text-center">
      <CheckCircle2 className="size-14 text-emerald-500" aria-hidden="true" />
      <div className="space-y-1">
        <h3 className="text-lg font-semibold text-slate-900">¡Pago confirmado!</h3>
        <p className="text-sm text-slate-600">
          Cobramos {formatCurrency(reservation.rentalAmount, reservation.currency)} del alquiler y retuvimos{' '}
          {formatCurrency(reservation.securityDeposit, reservation.currency)} de garantía. El propietario ya puede
          confirmar tu reserva.
        </p>
      </div>
      <div className="flex flex-col gap-2 sm:flex-row">
        <Button variant="secondary" onClick={handleDownload} isLoading={isGenerating} leftIcon={<FileDown className="size-4" />}>
          Descargar comprobante PDF
        </Button>
        <Button onClick={onDone}>{doneLabel}</Button>
      </div>
    </div>
  )
}
