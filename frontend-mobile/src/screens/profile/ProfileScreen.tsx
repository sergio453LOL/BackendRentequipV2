import { Ionicons } from '@expo/vector-icons'
import { useNetInfo } from '@react-native-community/netinfo'
import { useFocusEffect } from '@react-navigation/native'
import { useCameraPermissions } from 'expo-camera'
import * as Location from 'expo-location'
import { useCallback, useState } from 'react'
import { Alert, Linking, ScrollView, StyleSheet, Text, View } from 'react-native'
import { AppButton } from '../../components/AppButton'
import { API_BASE_URL } from '../../config/env'
import { useAuth } from '../../hooks/useAuth'
import { colors, radius, spacing, typography } from '../../theme'
import type { UserRole } from '../../types/api'

const ROLE_LABELS: Record<UserRole, string> = { ADMIN: 'Administrador', MANAGER: 'Gerente', OPERATOR: 'Operador' }

type PermissionState = 'granted' | 'denied' | 'undetermined'

function permissionLabel(state: PermissionState): { text: string; color: string } {
  if (state === 'granted') return { text: 'Permitido', color: colors.success }
  if (state === 'denied') return { text: 'Denegado', color: colors.danger }
  return { text: 'Sin decidir', color: colors.muted }
}

export default function ProfileScreen() {
  const { user, logout } = useAuth()
  const { isConnected } = useNetInfo()
  const [cameraPermission] = useCameraPermissions()
  const [locationPermission, setLocationPermission] = useState<PermissionState>('undetermined')

  // Reflect changes the user makes in the system settings when coming back to the app.
  useFocusEffect(
    useCallback(() => {
      void Location.getForegroundPermissionsAsync().then((result) => setLocationPermission(result.status as PermissionState))
    }, []),
  )

  if (!user) return null
  const camera = permissionLabel((cameraPermission?.status as PermissionState | undefined) ?? 'undetermined')
  const location = permissionLabel(locationPermission)

  function confirmLogout() {
    Alert.alert('Cerrar sesión', '¿Quieres salir de RentEquip en este dispositivo?', [
      { text: 'Cancelar', style: 'cancel' },
      { text: 'Cerrar sesión', style: 'destructive', onPress: () => void logout() },
    ])
  }

  return (
    <ScrollView style={styles.container} contentContainerStyle={styles.content}>
      <View style={styles.hero}>
        <View style={styles.avatar}>
          <Text style={styles.avatarText}>{user.companyName.charAt(0).toUpperCase()}</Text>
        </View>
        <Text style={styles.company}>{user.companyName}</Text>
        <Text style={styles.email}>{user.email}</Text>
        <View style={styles.rolePill}>
          <Text style={styles.roleText}>{ROLE_LABELS[user.role]}</Text>
        </View>
      </View>

      <View style={styles.card}>
        <Text style={styles.sectionTitle}>Permisos del dispositivo</Text>
        <Row icon="navigate-outline" label="Ubicación (GPS)" value={location.text} color={location.color} />
        <Row icon="camera-outline" label="Cámara" value={camera.text} color={camera.color} />
        <AppButton title="Gestionar permisos" icon="settings-outline" variant="secondary" onPress={() => void Linking.openSettings()} />
      </View>

      <View style={styles.card}>
        <Text style={styles.sectionTitle}>Conexión</Text>
        <Row
          icon={isConnected === false ? 'cloud-offline-outline' : 'cloud-done-outline'}
          label="Internet"
          value={isConnected === false ? 'Sin conexión' : 'Conectado'}
          color={isConnected === false ? colors.danger : colors.success}
        />
        <Text style={styles.server}>Servidor: {API_BASE_URL}</Text>
      </View>

      <AppButton title="Cerrar sesión" icon="log-out-outline" variant="danger" onPress={confirmLogout} />
    </ScrollView>
  )
}

function Row({ icon, label, value, color }: { icon: keyof typeof Ionicons.glyphMap; label: string; value: string; color: string }) {
  return (
    <View style={styles.row}>
      <Ionicons name={icon} size={20} color={colors.muted} />
      <Text style={styles.rowLabel}>{label}</Text>
      <Text style={[styles.rowValue, { color }]}>{value}</Text>
    </View>
  )
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: colors.background },
  content: { padding: spacing.lg, gap: spacing.lg },
  hero: { alignItems: 'center', gap: spacing.xs, paddingVertical: spacing.lg },
  avatar: { width: 80, height: 80, borderRadius: 40, backgroundColor: colors.brand, alignItems: 'center', justifyContent: 'center', marginBottom: spacing.sm },
  avatarText: { fontSize: 34, fontWeight: '800', color: colors.ink },
  company: typography.title,
  email: typography.caption,
  rolePill: { marginTop: spacing.sm, backgroundColor: colors.brandSoft, borderRadius: radius.pill, paddingHorizontal: spacing.md, paddingVertical: 4 },
  roleText: { fontSize: 13, fontWeight: '700', color: colors.brandDark },
  card: { backgroundColor: colors.surface, borderRadius: radius.lg, borderWidth: 1, borderColor: colors.border, padding: spacing.lg, gap: spacing.md },
  sectionTitle: typography.heading,
  row: { flexDirection: 'row', alignItems: 'center', gap: spacing.md },
  rowLabel: { flex: 1, fontSize: 15, color: colors.text },
  rowValue: { fontSize: 14, fontWeight: '700' },
  server: { ...typography.caption, fontSize: 12 },
})
