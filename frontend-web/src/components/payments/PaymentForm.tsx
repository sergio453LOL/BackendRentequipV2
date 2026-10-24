import { CreditCard, Lock, ShieldCheck } from 'lucide-react'
import { useState } from 'react'
import { z } from 'zod'
import { useMutation } from '../../hooks/useMutation'
import { useZodForm } from '../../hooks/useZodForm'
import {
  createCardToken,
  detectCardBrand,
  formatCardNumber,
  formatExpiry,
  isValidExpiry,
  passesLuhn,
  TEST_CARDS,
} from '../../services/cardTokenizer'
import { paymentService } from '../../services/paymentService'
import type { Payment, Reservation } from '../../types'
import { formatCurrency } from '../../utils/format'
import { Button } from '../common/Button'
import { Input } from '../common/Input'

const cardSchema = z.object({
  holderName: z.string().trim().min(3, 'Ingresa el nombre como figura en la tarjeta.'),
  number: z.string().refine(passesLuhn, 'El número de tarjeta no es válido.'),
  expiry: z.string().refine((value) => isValidExpiry(value), 'Fecha inválida o vencida (MM/AA).'),
  cvc: z.string().regex(/^\d{3,4}$/, 'El CVC tiene 3 o 4 dígitos.'),
})

type CardFormValues = z.infer<typeof cardSchema>

const BRAND_LABELS = { visa: 'VISA', mastercard: 'Mastercard', amex: 'AMEX', unknown: '' } as const

interface PaymentFormProps {
  reservation: Reservation
  onPaid: (payments: Payment[]) => void
  /** Optional secondary action, e.g. "Pagar después" in the booking flow. */
  onSkip?: () => void
}

/**
 * Card checkout. The card is tokenized in the browser and only the token is sent to the backend, which
 * captures the rental and holds the security deposit.
 */
export function PaymentForm({ reservation, onPaid, onSkip }: PaymentFormProps) {
  const [isTokenizing, setIsTokenizing] = useState(false)
  const {
    register,
    handleSubmit,
    setValue,
    watch,
    formState: { errors },
  } = useZodForm(cardSchema, { defaultValues: { holderName: '', number: '', expiry: '', cvc: '' } })

  const brand = detectCardBrand(watch('number'))
  const money = (amount: number) => formatCurrency(amount, reservation.currency)

  const payment = useMutation((paymentToken: string) => paymentService.pay(reservation.id, paymentToken), {
    successMessage: 'Pago realizado con éxito',
    errorTitle: 'El pago no se pudo procesar',
    onSuccess: onPaid,
  })

  const numberField = register('number')
  const expiryField = register('expiry')
  const isProcessing = isTokenizing || payment.isLoading

  async function onSubmit(values: CardFormValues) {
    setIsTokenizing(true)
    const token = await createCardToken(values)
    setIsTokenizing(false)
    await payment.mutate(token)
  }

  return (
    <form onSubmit={handleSubmit(onSubmit)} noValidate className="space-y-5">
      <dl className="space-y-2 rounded-lg bg-slate-50 p-4 text-sm">
        <div className="flex justify-between">
          <dt className="text-slate-600">Alquiler · se cobra ahora</dt>
          <dd className="font-semibold text-slate-900">{money(reservation.rentalAmount)}</dd>
        </div>
        <div className="flex justify-between">
          <dt className="text-slate-600">Garantía · se retiene y se libera al devolver el equipo</dt>
          <dd className="font-semibold text-slate-900">{money(reservation.securityDeposit)}</dd>
        </div>
        <div className="flex justify-between border-t border-slate-200 pt-2 text-base">
          <dt className="font-semibold text-slate-900">Total</dt>
          <dd className="font-semibold text-slate-900">{money(reservation.totalAmount)}</dd>
        </div>
      </dl>

      <fieldset className="space-y-4" disabled={isProcessing}>
        <legend className="sr-only">Datos de la tarjeta</legend>
        <Input label="Titular de la tarjeta" autoComplete="cc-name" placeholder="ANA PAZ" required error={errors.holderName?.message} {...register('holderName')} />
        <div className="relative">
          <Input
            label="Número de tarjeta"
            inputMode="numeric"
            autoComplete="cc-number"
            placeholder="4242 4242 4242 4242"
            required
            leftIcon={<CreditCard className="size-4" />}
            error={errors.number?.message}
            {...numberField}
            onChange={(event) => {
              setValue('number', formatCardNumber(event.target.value), { shouldValidate: Boolean(errors.number) })
            }}
          />
          {brand !== 'unknown' && (
            <span className="absolute top-9 right-3 rounded bg-slate-900 px-1.5 py-0.5 text-[10px] font-semibold tracking-wide text-white">
              {BRAND_LABELS[brand]}
            </span>
          )}
        </div>
        <div className="grid grid-cols-2 gap-4">
          <Input
            label="Vencimiento"
            inputMode="numeric"
            autoComplete="cc-exp"
            placeholder="MM/AA"
            required
            error={errors.expiry?.message}
            {...expiryField}
            onChange={(event) => {
              setValue('expiry', formatExpiry(event.target.value), { shouldValidate: Boolean(errors.expiry) })
            }}
          />
          <Input label="CVC" inputMode="numeric" autoComplete="cc-csc" placeholder="123" maxLength={4} required error={errors.cvc?.message} {...register('cvc')} />
        </div>
      </fieldset>

      <details className="rounded-lg border border-dashed border-slate-300 px-4 py-3 text-xs text-slate-600">
        <summary className="cursor-pointer font-medium text-slate-700">Modo de prueba: tarjetas de ejemplo</summary>
        <p className="mt-2">Usa cualquier fecha futura y cualquier CVC. Ningún cobro es real.</p>
        <ul className="mt-2 space-y-1">
          {TEST_CARDS.map((card) => (
            <li key={card.number}>
              <button
                type="button"
                onClick={() => setValue('number', card.number, { shouldValidate: true })}
                className="num text-accent-ink hover:underline"
              >
                {card.number}
              </button>{' '}
              · {card.label}
            </li>
          ))}
        </ul>
      </details>

      <div className="flex flex-col-reverse gap-3 sm:flex-row sm:items-center sm:justify-between">
        <p className="flex items-center gap-1.5 text-xs text-slate-500">
          <ShieldCheck className="size-4 text-slate-500" aria-hidden="true" />
          Tu tarjeta se tokeniza en el navegador; RentEquip nunca recibe el número.
        </p>
        <div className="flex gap-2">
          {onSkip && (
            <Button variant="ghost" onClick={onSkip} disabled={isProcessing}>
              Pagar después
            </Button>
          )}
          <Button type="submit" isLoading={isProcessing} leftIcon={<Lock className="size-4" />}>
            Pagar {money(reservation.totalAmount)}
          </Button>
        </div>
      </div>
    </form>
  )
}
