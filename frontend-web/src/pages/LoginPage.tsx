import { Lock, Mail } from 'lucide-react'
import { useState } from 'react'
import { Link, useLocation } from 'react-router-dom'
import { z } from 'zod'
import { Button } from '../components/common/Button'
import { Input } from '../components/common/Input'
import { AuthLayout } from '../components/layout/AuthLayout'
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
    <AuthLayout
      title="Inicia sesión en RentEquip"
      description="Alquila maquinaria pesada entre constructoras del Perú, o pon a trabajar la tuya."
    >

        {locationState.sessionExpired && !formError && (
          <p role="alert" className="mb-4 rounded-md border border-[#fde68a] bg-[#fffbeb] px-4 py-3 text-sm text-[#92400e]">
            Tu sesión expiró. Vuelve a iniciar sesión.
          </p>
        )}
        {formError && (
          <p role="alert" className="mb-4 rounded-md bg-[#ffedea] px-4 py-3 text-sm text-[#831c1e]">
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

        <p className="mt-6 text-sm text-slate-600">
          ¿Tu empresa aún no está registrada?{' '}
          <Link to={PATHS.register} className="font-medium text-ink underline underline-offset-2">
            Crear cuenta
          </Link>
        </p>
    </AuthLayout>
  )
}
