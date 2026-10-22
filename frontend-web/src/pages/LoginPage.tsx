import { HardHat, Lock, Mail } from 'lucide-react'
import { useState } from 'react'
import { Link, useLocation } from 'react-router-dom'
import { z } from 'zod'
import { Button } from '../components/common/Button'
import { Input } from '../components/common/Input'
import type { LoginLocationState } from '../contexts/AuthContextDefinition'
import { useAuth } from '../hooks/useAuth'
import { useZodForm } from '../hooks/useZodForm'
import { PATHS } from '../routes/paths'
import { ApiError } from '../utils/apiError'

const loginSchema = z.object({
  email: z.string().trim().min(1, 'Ingresa tu correo.').pipe(z.email('Ingresa un correo válido.')),
  password: z.string().min(1, 'Ingresa tu contraseña.'),
})

type LoginFormValues = z.infer<typeof loginSchema>

function loginErrorMessage(error: unknown): string {
  const apiError = ApiError.from(error)
  return apiError.status === 401 ? 'Correo o contraseña incorrectos.' : apiError.message
}

export default function LoginPage() {
  const { login } = useAuth()
  const location = useLocation()
  const locationState = (location.state ?? {}) as LoginLocationState
  const [formError, setFormError] = useState<string | null>(null)

  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useZodForm(loginSchema, { defaultValues: { email: '', password: '' } })

  async function onSubmit(values: LoginFormValues) {
    setFormError(null)
    try {
      await login(values)
    } catch (error) {
      setFormError(loginErrorMessage(error))
    }
  }

  return (
    <main className="flex min-h-screen items-center justify-center bg-gradient-to-br from-slate-900 via-slate-800 to-brand-900 p-4">
      <div className="w-full max-w-md rounded-2xl bg-white p-6 shadow-2xl sm:p-8">
        <div className="mb-8 flex flex-col items-center gap-3 text-center">
          <span className="flex size-14 items-center justify-center rounded-xl bg-brand-500">
            <HardHat className="size-8" aria-hidden="true" />
          </span>
          <h1 className="text-2xl font-bold text-slate-900">Bienvenido a RentEquip</h1>
          <p className="text-sm text-slate-500">Ingresa para gestionar tu maquinaria y reservas.</p>
        </div>

        {locationState.sessionExpired && !formError && (
          <p role="alert" className="mb-4 rounded-lg bg-brand-50 px-4 py-3 text-sm text-brand-800">
            Tu sesión expiró. Vuelve a iniciar sesión.
          </p>
        )}
        {formError && (
          <p role="alert" className="mb-4 rounded-lg bg-red-50 px-4 py-3 text-sm text-red-700">
            {formError}
          </p>
        )}

        <form onSubmit={handleSubmit(onSubmit)} noValidate className="space-y-5">
          <Input
            label="Correo electrónico"
            type="email"
            autoComplete="email"
            placeholder="tu@empresa.com"
            required
            error={errors.email?.message}
            leftIcon={<Mail className="size-4" />}
            {...register('email')}
          />
          <Input
            label="Contraseña"
            type="password"
            autoComplete="current-password"
            placeholder="••••••••"
            required
            error={errors.password?.message}
            leftIcon={<Lock className="size-4" />}
            {...register('password')}
          />
          <Button type="submit" size="lg" fullWidth isLoading={isSubmitting}>
            Iniciar sesión
          </Button>
        </form>

        <p className="mt-6 text-center text-sm text-slate-600">
          ¿Tu empresa aún no está registrada?{' '}
          <Link to={PATHS.register} className="font-semibold text-brand-700 hover:underline">
            Crear cuenta
          </Link>
        </p>
      </div>
    </main>
  )
}
