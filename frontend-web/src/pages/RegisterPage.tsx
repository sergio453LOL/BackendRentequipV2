import { ArrowLeft, ArrowRight, Building2, Check, HardHat, LocateFixed, UserRound, type LucideIcon } from 'lucide-react'
import { useState } from 'react'
import type { Path } from 'react-hook-form'
import { Link } from 'react-router-dom'
import { z } from 'zod'
import { Button } from '../components/common/Button'
import { Input } from '../components/common/Input'
import { Select } from '../components/common/Select'
import { useAuth } from '../hooks/useAuth'
import { useToast } from '../hooks/useToast'
import { useZodForm } from '../hooks/useZodForm'
import { PATHS } from '../routes/paths'
import { ApiError } from '../utils/apiError'
import { findCity, PERU_CITIES } from '../utils/cities'
import { cn } from '../utils/cn'
import { applyServerFieldErrors } from '../utils/formErrors'

const PASSWORD_RULES = [
  { label: 'Entre 8 y 64 caracteres', test: (value: string) => value.length >= 8 && value.length <= 64 },
  { label: 'Una letra minúscula', test: (value: string) => /[a-z]/.test(value) },
  { label: 'Una letra mayúscula', test: (value: string) => /[A-Z]/.test(value) },
  { label: 'Un número', test: (value: string) => /\d/.test(value) },
] as const

const optionalPhone = z.string().trim().max(20, 'Máximo 20 caracteres.').regex(/^[\d\s+()-]*$/, 'Solo números y + ( ) -')

const registerSchema = z
  .object({
    companyName: z.string().trim().min(2, 'Mínimo 2 caracteres.').max(150, 'Máximo 150 caracteres.'),
    taxId: z.string().trim().regex(/^\d{11}$/, 'El RUC debe tener exactamente 11 dígitos.'),
    companyEmail: z.string().trim().pipe(z.email('Ingresa un correo válido.')),
    companyPhone: optionalPhone,
    city: z.string().min(1, 'Selecciona la ciudad.'),
    address: z.string().trim().max(255, 'Máximo 255 caracteres.'),
    latitude: z.string(),
    longitude: z.string(),
    firstName: z.string().trim().min(1, 'Ingresa tu nombre.').max(80, 'Máximo 80 caracteres.'),
    lastName: z.string().trim().min(1, 'Ingresa tu apellido.').max(80, 'Máximo 80 caracteres.'),
    email: z.string().trim().pipe(z.email('Ingresa un correo válido.')),
    phone: optionalPhone,
    password: z
      .string()
      .refine((value) => PASSWORD_RULES.every((rule) => rule.test(value)), 'La contraseña no cumple los requisitos.'),
    confirmPassword: z.string().min(1, 'Repite la contraseña.'),
  })
  .refine((values) => values.password === values.confirmPassword, {
    path: ['confirmPassword'],
    message: 'Las contraseñas no coinciden.',
  })

type RegisterFormValues = z.infer<typeof registerSchema>

const STEPS = [
  {
    title: 'Empresa',
    icon: Building2,
    fields: ['companyName', 'taxId', 'companyEmail', 'companyPhone', 'city', 'address'],
  },
  {
    title: 'Administrador',
    icon: UserRound,
    fields: ['firstName', 'lastName', 'email', 'phone', 'password', 'confirmPassword'],
  },
] as const satisfies ReadonlyArray<{ title: string; icon: LucideIcon; fields: ReadonlyArray<Path<RegisterFormValues>> }>

/** The backend validates the nested `company` object; its field paths map to the flat form. */
const SERVER_FIELD_MAP: Partial<Record<string, Path<RegisterFormValues>>> = {
  'company.name': 'companyName',
  'company.taxId': 'taxId',
  'company.email': 'companyEmail',
  'company.phone': 'companyPhone',
  'company.city': 'city',
  'company.address': 'address',
  'company.latitude': 'city',
  'company.longitude': 'city',
}

type LocationStatus = 'idle' | 'locating' | 'detected' | 'denied' | 'unsupported'

export default function RegisterPage() {
  const { register: registerAccount } = useAuth()
  const toast = useToast()
  const [step, setStep] = useState(0)
  const [formError, setFormError] = useState<string | null>(null)
  const [locationStatus, setLocationStatus] = useState<LocationStatus>('idle')

  const {
    register,
    handleSubmit,
    trigger,
    setValue,
    setError,
    watch,
    formState: { errors, isSubmitting },
  } = useZodForm(registerSchema, {
    defaultValues: {
      companyName: '',
      taxId: '',
      companyEmail: '',
      companyPhone: '',
      city: '',
      address: '',
      latitude: '',
      longitude: '',
      firstName: '',
      lastName: '',
      email: '',
      phone: '',
      password: '',
      confirmPassword: '',
    },
  })

  const password = watch('password')
  const cityField = register('city')

  async function goToNextStep() {
    const isStepValid = await trigger([...STEPS[0].fields])
    if (isStepValid) setStep(1)
  }

  function detectLocation() {
    if (!('geolocation' in navigator)) {
      setLocationStatus('unsupported')
      return
    }
    setLocationStatus('locating')
    navigator.geolocation.getCurrentPosition(
      (position) => {
        setValue('latitude', position.coords.latitude.toFixed(7))
        setValue('longitude', position.coords.longitude.toFixed(7))
        setLocationStatus('detected')
      },
      () => setLocationStatus('denied'),
      { timeout: 10_000 },
    )
  }

  async function onSubmit(values: RegisterFormValues) {
    setFormError(null)
    const city = findCity(values.city)
    const latitude = values.latitude ? Number(values.latitude) : city?.latitude
    const longitude = values.longitude ? Number(values.longitude) : city?.longitude
    if (latitude === undefined || longitude === undefined) {
      setStep(0)
      setError('city', { message: 'Selecciona una ciudad válida.' })
      return
    }

    try {
      await registerAccount({
        company: {
          name: values.companyName,
          taxId: values.taxId,
          email: values.companyEmail,
          phone: values.companyPhone || undefined,
          address: values.address || undefined,
          city: values.city,
          latitude,
          longitude,
        },
        firstName: values.firstName,
        lastName: values.lastName,
        email: values.email,
        phone: values.phone || undefined,
        password: values.password,
      })
      toast.success('¡Cuenta creada!', `Bienvenido a RentEquip, ${values.companyName}.`)
    } catch (error) {
      const appliedToFields = applyServerFieldErrors(error, setError, SERVER_FIELD_MAP)
      const companyFieldFailed = ApiError.from(error).details.some((detail) => detail.field.startsWith('company.'))
      if (companyFieldFailed) setStep(0)
      setFormError(appliedToFields ? 'Revisa los campos marcados.' : ApiError.from(error).message)
    }
  }

  return (
    <main className="flex min-h-screen items-center justify-center bg-gradient-to-br from-slate-900 via-slate-800 to-brand-900 p-4 py-10">
      <div className="w-full max-w-2xl rounded-2xl bg-white p-6 shadow-2xl sm:p-8">
        <div className="mb-6 flex flex-col items-center gap-3 text-center">
          <span className="flex size-12 items-center justify-center rounded-xl bg-brand-500">
            <HardHat className="size-7" aria-hidden="true" />
          </span>
          <h1 className="text-2xl font-bold text-slate-900">Registra tu empresa</h1>
          <p className="text-sm text-slate-500">Publica tu maquinaria y alquila equipos de otras constructoras.</p>
        </div>

        <ol className="mb-8 flex items-center gap-3" aria-label="Progreso del registro">
          {STEPS.map(({ title, icon: Icon }, index) => {
            const isDone = index < step
            const isCurrent = index === step
            return (
              <li key={title} className="flex flex-1 items-center gap-3" aria-current={isCurrent ? 'step' : undefined}>
                <span
                  className={cn(
                    'flex size-9 shrink-0 items-center justify-center rounded-full text-sm font-semibold transition-colors',
                    isDone && 'bg-emerald-500 text-white',
                    isCurrent && 'bg-brand-500 text-slate-900',
                    !isDone && !isCurrent && 'bg-slate-100 text-slate-400',
                  )}
                >
                  {isDone ? <Check className="size-4" /> : <Icon className="size-4" />}
                </span>
                <span className={cn('text-sm font-medium', isCurrent ? 'text-slate-900' : 'text-slate-500')}>
                  <span className="sr-only">Paso {index + 1}: </span>
                  {title}
                </span>
                {index < STEPS.length - 1 && <span className="h-px flex-1 bg-slate-200" aria-hidden="true" />}
              </li>
            )
          })}
        </ol>

        {formError && (
          <p role="alert" className="mb-5 rounded-lg bg-red-50 px-4 py-3 text-sm text-red-700">
            {formError}
          </p>
        )}

        <form
          noValidate
          onSubmit={
            step === 0
              ? (event) => {
                  event.preventDefault()
                  void goToNextStep()
                }
              : handleSubmit(onSubmit)
          }
        >
          <fieldset className={cn('grid gap-5 sm:grid-cols-2', step !== 0 && 'hidden')}>
            <legend className="sr-only">Datos de la empresa</legend>
            <Input className="sm:col-span-2" label="Razón social" required placeholder="Constructora Andina S.A.C." error={errors.companyName?.message} {...register('companyName')} />
            <Input label="RUC" required inputMode="numeric" maxLength={11} placeholder="20123456789" hint="11 dígitos" error={errors.taxId?.message} {...register('taxId')} />
            <Input label="Teléfono de la empresa" type="tel" placeholder="01 234 5678" error={errors.companyPhone?.message} {...register('companyPhone')} />
            <Input className="sm:col-span-2" label="Correo de la empresa" type="email" required placeholder="contacto@empresa.com" error={errors.companyEmail?.message} {...register('companyEmail')} />
            <Select
              label="Ciudad"
              required
              placeholder="Selecciona una ciudad"
              options={PERU_CITIES.map((city) => ({ value: city.name, label: city.name }))}
              error={errors.city?.message}
              {...cityField}
              onChange={(event) => {
                void cityField.onChange(event)
                setValue('latitude', '')
                setValue('longitude', '')
                setLocationStatus('idle')
              }}
            />
            <Input label="Dirección" placeholder="Av. Javier Prado 123" error={errors.address?.message} {...register('address')} />
            <div className="flex flex-col gap-2 rounded-xl bg-slate-50 p-4 text-sm sm:col-span-2 sm:flex-row sm:items-center sm:justify-between">
              <p className="text-slate-600">
                {locationStatus === 'detected'
                  ? '📍 Usaremos tu ubicación actual para mostrar tus equipos en el mapa.'
                  : locationStatus === 'denied'
                    ? 'No pudimos obtener tu ubicación. Usaremos el centro de la ciudad elegida.'
                    : locationStatus === 'unsupported'
                      ? 'Tu navegador no permite detectar la ubicación. Usaremos el centro de la ciudad.'
                      : 'Por defecto usamos el centro de la ciudad. Puedes usar tu ubicación exacta.'}
              </p>
              <Button
                variant="secondary"
                size="sm"
                onClick={detectLocation}
                isLoading={locationStatus === 'locating'}
                leftIcon={<LocateFixed className="size-4" />}
              >
                Usar mi ubicación
              </Button>
            </div>
          </fieldset>

          <fieldset className={cn('grid gap-5 sm:grid-cols-2', step !== 1 && 'hidden')}>
            <legend className="sr-only">Datos del administrador</legend>
            <Input label="Nombres" required autoComplete="given-name" error={errors.firstName?.message} {...register('firstName')} />
            <Input label="Apellidos" required autoComplete="family-name" error={errors.lastName?.message} {...register('lastName')} />
            <Input label="Correo personal" type="email" required autoComplete="email" hint="Lo usarás para iniciar sesión" error={errors.email?.message} {...register('email')} />
            <Input label="Celular" type="tel" autoComplete="tel" placeholder="987 654 321" error={errors.phone?.message} {...register('phone')} />
            <Input label="Contraseña" type="password" required autoComplete="new-password" error={errors.password?.message} {...register('password')} />
            <Input label="Repite la contraseña" type="password" required autoComplete="new-password" error={errors.confirmPassword?.message} {...register('confirmPassword')} />
            <ul className="grid grid-cols-2 gap-1.5 text-xs sm:col-span-2" aria-label="Requisitos de la contraseña">
              {PASSWORD_RULES.map((rule) => {
                const passes = rule.test(password)
                return (
                  <li key={rule.label} className={cn('flex items-center gap-1.5', passes ? 'text-emerald-600' : 'text-slate-400')}>
                    <Check className="size-3.5" aria-hidden="true" />
                    {rule.label}
                    <span className="sr-only">{passes ? '(cumple)' : '(pendiente)'}</span>
                  </li>
                )
              })}
            </ul>
          </fieldset>

          <div className="mt-8 flex flex-col-reverse gap-3 sm:flex-row sm:justify-between">
            {step === 0 ? (
              <Link to={PATHS.login} className="text-center text-sm font-medium text-slate-600 hover:text-slate-900 sm:self-center">
                Ya tengo una cuenta
              </Link>
            ) : (
              <Button variant="secondary" onClick={() => setStep(0)} disabled={isSubmitting} leftIcon={<ArrowLeft className="size-4" />}>
                Anterior
              </Button>
            )}
            {step === 0 ? (
              <Button onClick={goToNextStep}>
                Siguiente <ArrowRight className="size-4" aria-hidden="true" />
              </Button>
            ) : (
              <Button type="submit" isLoading={isSubmitting}>
                Crear cuenta
              </Button>
            )}
          </div>
        </form>
      </div>
    </main>
  )
}
