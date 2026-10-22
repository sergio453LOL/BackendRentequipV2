import { Ionicons } from '@expo/vector-icons'
import { useNetInfo } from '@react-native-community/netinfo'
import { StyleSheet, Text, View } from 'react-native'
import { useSafeAreaInsets } from 'react-native-safe-area-context'
import { colors, spacing } from '../theme'

/** Shown on top of the app while the phone has no internet, before any request fails. */
export function OfflineBanner() {
  const { isConnected, isInternetReachable } = useNetInfo()
  const isOffline = isConnected === false || isInternetReachable === false
  const insets = useSafeAreaInsets()
  if (!isOffline) return null

  return (
    <View style={[styles.banner, { paddingTop: insets.top + spacing.sm }]} accessibilityRole="alert">
      <Ionicons name="cloud-offline-outline" size={16} color={colors.brand} />
      <Text style={styles.text}>Sin conexión. Te mostraremos los datos cuando vuelva internet.</Text>
    </View>
  )
}

const styles = StyleSheet.create({
  banner: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: spacing.sm,
    backgroundColor: colors.ink,
    paddingHorizontal: spacing.lg,
    paddingVertical: spacing.sm,
  },
  text: { color: '#FFFFFF', fontSize: 13, flex: 1 },
})
