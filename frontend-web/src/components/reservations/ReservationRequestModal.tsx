import { CalendarCheck, CalendarX } from 'lucide-react'
import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { z } from 'zod'
import { useDebounce } from '../../hooks/useDebounce'
import { useFetch } from '../../hooks/useFetch'
import { useMutation } from '../../hooks/useMutation'
import { useZodForm } from '../../hooks/useZodForm'
import { PATHS } from '../../routes/paths'
import { ENDPOINTS } from '../../services/endpoints'
import { reservationService } from '../../services/reservationService'
import type { Availability, Equipment, Payment, Reservation } from '../../types'
import { applyServerFieldErrors } from '../../utils/formErrors'
import { formatCurrency, rentalDays, todayIso } from '../../utils/format'
import { Button } from '../common/Button'
import { Input } from '../common/Input'
import { Modal } from '../common/Modal'
import { Spinner } from '../common/Spinner'
import { Textarea } from '../common/Textarea'
import { PaymentForm } from '../payments/PaymentForm'
import { PaymentSuccess } from '../payments/PaymentSuccess'

const MAX_RENTAL_DAYS = 365
const ISO_DATE = /^\d{4}-\d{2}-\d{2}$/

const reservationSchema = z
  .object({
    startDate: z
      .string()
      .regex(ISO_DATE, 'Elige la fecha de inicio.')
      .refine((value) => value >= todayIso(), 'La fecha de inicio no puede estar en el pasado.'),
    endDate: z.string().regex(ISO_DATE, 'Elige la fecha de fin.'),
    notes: z.string().max(500, 'Máximo 500 caracteres.'),
  })
  .refine((values) => values.endDate >= values.startDate, {
    path: ['endDate'],
    message: 'La fecha de fin debe ser igual o posterior a la de inicio.',
  })
  .refine((values) => rentalDays(values.startDate, values.endDate) <= MAX_RENTAL_DAYS, {
    path: ['endDate'],
    message: `Un alquiler no puede superar ${MAX_RENTAL_DAYS} días.`,
  })

type ReservationFormValues = z.infer<typeof reservationSchema>

type CheckoutStep =
  | { name: 'details' }
  | { name: 'payment'; reservation: Reservation }
  | { name: 'paid'; reservation: Reservation; payments: Payment[] }

const STEP_TITLES: Record<CheckoutStep['name'], string> = {
  details: 'Solicitar reserva',
  payment: 'Pago seguro',
  paid: 'Pago completado',
}

const STEP_NUMBERS: Record<CheckoutStep['name'], number> = { details: 1, payment: 2, paid: 3 }

interface ReservationRequestModalProps {
  equipment: Equipment
  isOpen: boolean
  onClose: () => void
}

export function ReservationRequestModal({ equipment, isOpen, onClose }: ReservationRequestModalProps) {
  const navigate = useNavigate()
  const [step, setStep] = useState<CheckoutStep>({ name: 'details' })
  const {
    register,
    handleSubmit,
    watch,
    reset,
    setError,
    formState: { errors },
  } = useZodForm(reservationSchema, { defaultValues: { startDate: '', endDate: '', notes: '' } })

  const startDate = watch('startDate')
  const endDate = watch('endDate')
  const notesLength = watch('notes').length
  const datesAreValid =
    ISO_DATE.test(startDate) && ISO_DATE.test(endDate) && startDate >= todayIso() && endDate >= startDate
  const days = datesAreValid ? rentalDays(startDate, endDate) : 0

  const debouncedRange = useDebounce(datesAreValid ? `${startDate}|${endDate}` : null, 300)
  const [checkStart, checkEnd] = debouncedRange?.split('|') ?? []
  const availability = useFetch<Availability>(
    debouncedRange && isOpen ? ENDPOINTS.availability : null,
    { equipmentId: equipment.id, startDate: checkStart, endDate: checkEnd },
  )
  const isCheckingAvailability =
    datesAreValid && (availability.isLoading || debouncedRange !== `${startDate}|${endDate}`)
  const isUnavailable = datesAreValid && !isCheckingAvailability && availability.data?.available === false

  const createReservation = useMutation(reservationService.create, {
    successMessage: 'Solicitud creada. Completa el pago para asegurarla.',
    errorTitle: 'No se pudo enviar la solicitud',
    onSuccess: (reservation) => setStep({ name: 'payment', reservation }),
    onError: (error) => applyServerFieldErrors(error, setError),
  })

  async function onSubmit(values: ReservationFormValues) {
    await createReservation.mutate({
      equipmentId: equipment.id,
      startDate: values.startDate,
      endDate: values.endDate,
      notes: values.notes.trim() || undefined,
    })
  }

  const rentalAmount = days * equipment.dailyRate

  /** Once the reservation exists, closing the modal takes the user to it, paid or not. */
  function handleClose() {
    if (step.name === 'details') {
      onClose()
      return
    }
    reset()
    setStep({ name: 'details' })
    onClose()
    navigate(`${PATHS.reservations}?role=RENTER`)
  }

  if (step.name !== 'details') {
    return (
      <Modal isOpen={isOpen} onClose={handleClose} title={STEP_TITLES[step.name]}>
        <p className="mb-4 text-xs font-medium tracking-wide text-slate-500 uppercase">Paso {STEP_NUMBERS[step.name]} de 3</p>
        {step.name === 'payment' ? (
          <PaymentForm
            reservation={step.reservation}
            onSkip={handleClose}
            onPaid={(payments) =>
              setStep({ name: 'paid', reservation: { ...step.reservation, paymentStatus: 'PAID' }, payments })
            }
          />
        ) : (
          <PaymentSuccess reservation={step.reservation} payments={step.payments} onDone={handleClose} doneLabel="Ver mis reservas" />
        )}
      </Modal>
    )
  }

  return (
    <Modal
      isOpen={isOpen}
      onClose={onClose}
      title={STEP_TITLES.details}
      isDismissable={!createReservation.isLoading}
      footer={
        <>
          <Button variant="secondary" onClick={onClose} disabled={createReservation.isLoading}>
            Cancelar
          </Button>
          <Button
            type="submit"
            form="reservation-form"
            isLoading={createReservation.isLoading}
            disabled={isUnavailable || isCheckingAvailability}
          >
            Enviar solicitud
          </Button>
        </>
      }
    >
      <p className="mb-4 text-xs font-medium tracking-wide text-slate-500 uppercase">Paso 1 de 3</p>
      <form id="reservation-form" onSubmit={handleSubmit(onSubmit)} noValidate className="space-y-5">
        <p className="text-sm text-slate-600">
          Estás solicitando <span className="font-semibold text-slate-900">{equipment.name}</span>. El propietario
          debe confirmar la reserva.
        </p>

        <div className="grid gap-4 sm:grid-cols-2">
          <Input label="Fecha de inicio" type="date" min={todayIso()} required error={errors.startDate?.message} {...register('startDate')} />
          <Input
            label="Fecha de fin"
            type="date"
            min={startDate || todayIso()}
            required
            error={errors.endDate?.message}
            {...register('endDate')}
          />
        </div>

        {datesAreValid && (
          <div aria-live="polite" className="text-sm">
            {isCheckingAvailability && (
              <p className="flex items-center gap-2 text-slate-500">
                <Spinner size="sm" label="Verificando disponibilidad" /> Verificando disponibilidad…
              </p>
            )}
            {!isCheckingAvailability && availability.data?.available === true && (
              <p className="flex items-center gap-2 font-medium text-emerald-700">
                <CalendarCheck className="size-4" aria-hidden="true" /> Disponible en esas fechas
              </p>
            )}
            {isUnavailable && (
              <p className="flex items-center gap-2 font-medium text-red-700">
                <CalendarX className="size-4" aria-hidden="true" /> Ya está reservada en esas fechas. Prueba con otro rango.
              </p>
            )}
            {!isCheckingAvailability && availability.error && (
              <p className="text-amber-700">No pudimos verificar la disponibilidad: {availability.error.message}</p>
            )}
          </div>
        )}

        <Textarea
          label="Notas para el propietario"
          placeholder="Ej.: dirección de la obra, horario de entrega, operador requerido…"
          maxLength={500}
          hint={`${notesLength}/500`}
          error={errors.notes?.message}
          {...register('notes')}
        />

        <dl className="space-y-2 rounded-xl bg-slate-50 p-4 text-sm">
          <div className="flex justify-between">
            <dt className="text-slate-500">
              {formatCurrency(equipment.dailyRate, equipment.currency)} × {days} {days === 1 ? 'día' : 'días'}
            </dt>
            <dd className="font-medium text-slate-900">{formatCurrency(rentalAmount, equipment.currency)}</dd>
          </div>
          <div className="flex justify-between">
            <dt className="text-slate-500">Garantía (reembolsable)</dt>
            <dd className="font-medium text-slate-900">{formatCurrency(equipment.securityDeposit, equipment.currency)}</dd>
          </div>
          <div className="flex justify-between border-t border-slate-200 pt-2 text-base">
            <dt className="font-semibold text-slate-900">Total estimado</dt>
            <dd className="font-bold text-slate-900">
              {formatCurrency(rentalAmount + equipment.securityDeposit, equipment.currency)}
            </dd>
          </div>
        </dl>
      </form>
    </Modal>
  )
}
