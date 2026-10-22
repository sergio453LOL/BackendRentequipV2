import { Ionicons } from '@expo/vector-icons'
import type { ReactNode } from 'react'
import { ActivityIndicator, StyleSheet, Text, View } from 'react-native'
import { colors, spacing, typography } from '../theme'
import type { ApiError } from '../utils/apiError'
import { AppButton } from './AppButton'

export function LoadingView({ label = 'Cargando…' }: { label?: string }) {
  return (
    <View style={styles.center} accessibilityRole="progressbar" accessibilityLabel={label}>
      <ActivityIndicator size="large" color={colors.brand} />
      <Text style={styles.caption}>{label}</Text>
    </View>
  )
}

interface ErrorViewProps {
  error: ApiError
  onRetry?: () => void
}

/** Friendly error with a retry button when retrying makes sense (not for 403/404). */
export function ErrorView({ error, onRetry }: ErrorViewProps) {
  const canRetry = onRetry && error.status !== 403 && error.status !== 404
  return (
    <View style={styles.center} accessibilityRole="alert">
      <Ionicons name={error.isNetworkError ? 'cloud-offline-outline' : 'alert-circle-outline'} size={44} color={colors.danger} />
      <Text style={styles.title}>{error.isNetworkError ? 'Sin conexión con el servidor' : 'Algo salió mal'}</Text>
      <Text style={styles.description}>{error.message}</Text>
      {canRetry && <AppButton title="Reintentar" icon="refresh" variant="secondary" onPress={onRetry} />}
    </View>
  )
}

interface EmptyStateProps {
  icon: keyof typeof Ionicons.glyphMap
  title: string
  description: string
  action?: ReactNode
}

export function EmptyState({ icon, title, description, action }: EmptyStateProps) {
  return (
    <View style={styles.center}>
      <View style={styles.iconBubble}>
        <Ionicons name={icon} size={30} color={colors.brandDark} />
      </View>
      <Text style={styles.title}>{title}</Text>
      <Text style={styles.description}>{description}</Text>
      {action}
    </View>
  )
}

const styles = StyleSheet.create({
  center: { flexGrow: 1, alignItems: 'center', justifyContent: 'center', padding: spacing.xl, gap: spacing.md },
  caption: typography.caption,
  title: { ...typography.heading, textAlign: 'center' },
  description: { ...typography.body, textAlign: 'center', color: colors.muted },
  iconBubble: {
    width: 64,
    height: 64,
    borderRadius: 32,
    backgroundColor: colors.brandSoft,
    alignItems: 'center',
    justifyContent: 'center',
  },
})
