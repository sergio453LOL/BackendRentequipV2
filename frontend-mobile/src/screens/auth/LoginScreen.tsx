import { Ionicons } from '@expo/vector-icons'
import { zodResolver } from '@hookform/resolvers/zod'
import { useState } from 'react'
import { Controller, useForm } from 'react-hook-form'
import { KeyboardAvoidingView, Platform, Pressable, ScrollView, StyleSheet, Text, View } from 'react-native'
import { SafeAreaView } from 'react-native-safe-area-context'
import { z } from 'zod'
import { AppButton } from '../../components/AppButton'
import { TextField } from '../../components/TextField'
import { API_BASE_URL } from '../../config/env'
import { useAuth } from '../../hooks/useAuth'
import type { AuthScreenProps } from '../../navigation/types'
import { colors, radius, spacing, typography } from '../../theme'
import { ApiError } from '../../utils/apiError'

const loginSchema = z.object({
  email: z.string().trim().min(1, 'Ingresa tu correo.').pipe(z.email('Ingresa un correo válido.')),
  password: z.string().min(1, 'Ingresa tu contraseña.'),
})

type LoginValues = z.infer<typeof loginSchema>

export default function LoginScreen({ navigation }: AuthScreenProps<'Login'>) {
  const { login } = useAuth()
  const [formError, setFormError] = useState<string | null>(null)
  const [showPassword, setShowPassword] = useState(false)
  const {
    control,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<LoginValues>({ resolver: zodResolver(loginSchema), mode: 'onTouched', defaultValues: { email: '', password: '' } })

  async function onSubmit(values: LoginValues) {
    setFormError(null)
    try {
      await login(values)
    } catch (error) {
      const apiError = ApiError.from(error)
      setFormError(apiError.status === 401 ? 'Correo o contraseña incorrectos.' : apiError.message)
    }
  }

  return (
    <SafeAreaView style={styles.safe}>
      <KeyboardAvoidingView style={styles.flex} behavior={Platform.OS === 'ios' ? 'padding' : undefined}>
        <ScrollView contentContainerStyle={styles.content} keyboardShouldPersistTaps="handled">
          <View style={styles.hero}>
            <View style={styles.logo}>
              <Ionicons name="construct" size={36} color={colors.ink} />
            </View>
            <Text style={styles.title}>RentEquip</Text>
            <Text style={styles.subtitle}>Alquila maquinaria de construcción cerca de tu obra</Text>
          </View>

          {formError && (
            <View style={styles.errorBox} accessibilityRole="alert">
              <Ionicons name="alert-circle" size={18} color={colors.danger} />
              <Text style={styles.errorText}>{formError}</Text>
            </View>
          )}

          <Controller
            control={control}
            name="email"
            render={({ field }) => (
              <TextField
                label="Correo electrónico"
                required
                value={field.value}
                onChangeText={field.onChange}
                onBlur={field.onBlur}
                error={errors.email?.message}
                keyboardType="email-address"
                autoCapitalize="none"
                autoComplete="email"
                textContentType="emailAddress"
                placeholder="tu@empresa.com"
                returnKeyType="next"
              />
            )}
          />
          <Controller
            control={control}
            name="password"
            render={({ field }) => (
              <View>
                <TextField
                  label="Contraseña"
                  required
                  value={field.value}
                  onChangeText={field.onChange}
                  onBlur={field.onBlur}
                  error={errors.password?.message}
                  secureTextEntry={!showPassword}
                  autoComplete="password"
                  textContentType="password"
                  placeholder="••••••••"
                  returnKeyType="go"
                  onSubmitEditing={() => void handleSubmit(onSubmit)()}
                />
                <Pressable
                  onPress={() => setShowPassword((value) => !value)}
                  accessibilityRole="button"
                  accessibilityLabel={showPassword ? 'Ocultar contraseña' : 'Mostrar contraseña'}
                  style={styles.eye}
                  hitSlop={12}
                >
                  <Ionicons name={showPassword ? 'eye-off-outline' : 'eye-outline'} size={20} color={colors.muted} />
                </Pressable>
              </View>
            )}
          />

          <AppButton title="Iniciar sesión" icon="log-in-outline" onPress={handleSubmit(onSubmit)} isLoading={isSubmitting} />
          <AppButton title="Registrar mi empresa" variant="ghost" onPress={() => navigation.navigate('Register')} />
          <Text style={styles.server}>Servidor: {API_BASE_URL}</Text>
        </ScrollView>
      </KeyboardAvoidingView>
    </SafeAreaView>
  )
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: colors.background },
  flex: { flex: 1 },
  content: { flexGrow: 1, justifyContent: 'center', padding: spacing.xl, gap: spacing.lg },
  hero: { alignItems: 'center', gap: spacing.sm, marginBottom: spacing.lg },
  logo: {
    width: 72,
    height: 72,
    borderRadius: radius.lg,
    backgroundColor: colors.brand,
    alignItems: 'center',
    justifyContent: 'center',
  },
  title: typography.title,
  subtitle: { ...typography.body, color: colors.muted, textAlign: 'center' },
  errorBox: {
    flexDirection: 'row',
    gap: spacing.sm,
    backgroundColor: colors.dangerSoft,
    padding: spacing.md,
    borderRadius: radius.md,
    alignItems: 'center',
  },
  errorText: { flex: 1, color: colors.danger, fontSize: 14 },
  eye: { position: 'absolute', right: spacing.md, top: 36 },
  server: { ...typography.caption, textAlign: 'center', fontSize: 11 },
})
