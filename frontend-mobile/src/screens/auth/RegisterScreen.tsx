import { zodResolver } from '@hookform/resolvers/zod'
import { useState } from 'react'
import { Controller, useForm, type Path } from 'react-hook-form'
import { KeyboardAvoidingView, Platform, ScrollView, StyleSheet, Text, View } from 'react-native'
import { z } from 'zod'
import { AppButton } from '../../components/AppButton'
import { Chip } from '../../components/Chip'
import { TextField } from '../../components/TextField'
import { useAuth } from '../../hooks/useAuth'
import { useToast } from '../../hooks/useToast'
import { colors, radius, spacing, typography } from '../../theme'
import { ApiError } from '../../utils/apiError'
import { PERU_CITIES } from '../../utils/cities'

const registerSchema = z
  .object({
    companyName: z.string().trim().min(2, 'Mínimo 2 caracteres.').max(150, 'Máximo 150 caracteres.'),
    taxId: z.string().trim().regex(/^\d{11}$/, 'El RUC tiene exactamente 11 dígitos.'),
    companyEmail: z.string().trim().pipe(z.email('Ingresa un correo válido.')),
    city: z.string().min(1, 'Elige la ciudad de tu empresa.'),
    firstName: z.string().trim().min(1, 'Ingresa tu nombre.').max(80),
    lastName: z.string().trim().min(1, 'Ingresa tu apellido.').max(80),
    email: z.string().trim().pipe(z.email('Ingresa un correo válido.')),
    password: z
      .string()
      .min(8, 'Mínimo 8 caracteres.')
      .max(64, 'Máximo 64 caracteres.')
      .regex(/[a-z]/, 'Incluye una letra minúscula.')
      .regex(/[A-Z]/, 'Incluye una letra mayúscula.')
      .regex(/\d/, 'Incluye un número.'),
    confirmPassword: z.string(),
  })
  .refine((values) => values.password === values.confirmPassword, {
    path: ['confirmPassword'],
    message: 'Las contraseñas no coinciden.',
  })

type RegisterValues = z.infer<typeof registerSchema>

const SERVER_FIELDS: Record<string, Path<RegisterValues>> = {
  'company.name': 'companyName',
  'company.taxId': 'taxId',
  'company.email': 'companyEmail',
  'company.city': 'city',
  firstName: 'firstName',
  lastName: 'lastName',
  email: 'email',
  password: 'password',
}

interface FieldConfig {
  name: Exclude<Path<RegisterValues>, 'city'>
  label: string
  placeholder?: string
  keyboard?: 'default' | 'email-address' | 'number-pad'
  secure?: boolean
  maxLength?: number
}

const COMPANY_FIELDS: FieldConfig[] = [
  { name: 'companyName', label: 'Razón social', placeholder: 'Constructora Andina S.A.C.' },
  { name: 'taxId', label: 'RUC', placeholder: '20123456789', keyboard: 'number-pad', maxLength: 11 },
  { name: 'companyEmail', label: 'Correo de la empresa', placeholder: 'contacto@empresa.com', keyboard: 'email-address' },
]

const ADMIN_FIELDS: FieldConfig[] = [
  { name: 'firstName', label: 'Nombres' },
  { name: 'lastName', label: 'Apellidos' },
  { name: 'email', label: 'Tu correo (para iniciar sesión)', placeholder: 'tu@empresa.com', keyboard: 'email-address' },
  { name: 'password', label: 'Contraseña', secure: true },
  { name: 'confirmPassword', label: 'Repite la contraseña', secure: true },
]

export default function RegisterScreen() {
  const { register } = useAuth()
  const toast = useToast()
  const [formError, setFormError] = useState<string | null>(null)
  const {
    control,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<RegisterValues>({
    resolver: zodResolver(registerSchema),
    mode: 'onTouched',
    defaultValues: {
      companyName: '',
      taxId: '',
      companyEmail: '',
      city: '',
      firstName: '',
      lastName: '',
      email: '',
      password: '',
      confirmPassword: '',
    },
  })

  async function onSubmit(values: RegisterValues) {
    setFormError(null)
    const city = PERU_CITIES.find((item) => item.name === values.city) ?? PERU_CITIES[0]
    try {
      await register({
        company: {
          name: values.companyName,
          taxId: values.taxId,
          email: values.companyEmail,
          city: city.name,
          latitude: city.latitude,
          longitude: city.longitude,
        },
        firstName: values.firstName,
        lastName: values.lastName,
        email: values.email,
        password: values.password,
      })
      toast.success('¡Cuenta creada!', `Bienvenido a RentEquip, ${values.companyName}.`)
    } catch (error) {
      const apiError = ApiError.from(error)
      apiError.details.forEach((detail) => {
        const field = SERVER_FIELDS[detail.field]
        if (field) setError(field, { message: detail.message })
      })
      setFormError(apiError.details.length > 0 ? 'Revisa los campos marcados.' : apiError.message)
    }
  }

  const renderField = (config: FieldConfig) => (
    <Controller
      key={config.name}
      control={control}
      name={config.name}
      render={({ field }) => (
        <TextField
          label={config.label}
          required
          value={field.value}
          onChangeText={field.onChange}
          onBlur={field.onBlur}
          error={errors[config.name]?.message}
          placeholder={config.placeholder}
          keyboardType={config.keyboard ?? 'default'}
          autoCapitalize={config.keyboard === 'email-address' || config.secure ? 'none' : 'words'}
          secureTextEntry={config.secure}
          maxLength={config.maxLength}
        />
      )}
    />
  )

  return (
    <KeyboardAvoidingView style={styles.flex} behavior={Platform.OS === 'ios' ? 'padding' : undefined}>
      <ScrollView contentContainerStyle={styles.content} keyboardShouldPersistTaps="handled">
        {formError && (
          <Text style={styles.formError} accessibilityRole="alert">
            {formError}
          </Text>
        )}

        <Text style={styles.section}>1. Tu empresa</Text>
        {COMPANY_FIELDS.map(renderField)}
        <Controller
          control={control}
          name="city"
          render={({ field }) => (
            <View style={styles.cityGroup}>
              <Text style={styles.label}>Ciudad *</Text>
              <View style={styles.chips}>
                {PERU_CITIES.map((city) => (
                  <Chip key={city.name} label={city.name} selected={field.value === city.name} onPress={() => field.onChange(city.name)} />
                ))}
              </View>
              {errors.city && <Text style={styles.error}>{errors.city.message}</Text>}
            </View>
          )}
        />

        <Text style={styles.section}>2. Administrador de la cuenta</Text>
        {ADMIN_FIELDS.map(renderField)}
        <Text style={styles.hint}>La contraseña debe tener 8+ caracteres, una mayúscula, una minúscula y un número.</Text>

        <AppButton title="Crear cuenta" icon="checkmark-circle-outline" onPress={handleSubmit(onSubmit)} isLoading={isSubmitting} />
      </ScrollView>
    </KeyboardAvoidingView>
  )
}

const styles = StyleSheet.create({
  flex: { flex: 1, backgroundColor: colors.background },
  content: { padding: spacing.xl, gap: spacing.lg, paddingBottom: 48 },
  section: { ...typography.heading, marginTop: spacing.sm },
  label: { fontSize: 14, fontWeight: '600', color: colors.text },
  cityGroup: { gap: spacing.sm },
  chips: { flexDirection: 'row', flexWrap: 'wrap', gap: spacing.sm },
  error: { fontSize: 12, color: colors.danger },
  hint: typography.caption,
  formError: {
    backgroundColor: colors.dangerSoft,
    color: colors.danger,
    padding: spacing.md,
    borderRadius: radius.md,
    overflow: 'hidden',
  },
})
