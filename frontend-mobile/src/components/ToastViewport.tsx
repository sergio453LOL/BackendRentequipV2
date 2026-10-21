import { Ionicons } from '@expo/vector-icons'
import { useEffect, useState } from 'react'
import { Animated, Pressable, StyleSheet, Text, View } from 'react-native'
import { useSafeAreaInsets } from 'react-native-safe-area-context'
import type { ToastMessage, ToastVariant } from '../contexts/ToastContext'
import { colors, radius, spacing } from '../theme'

const ICONS: Record<ToastVariant, { name: keyof typeof Ionicons.glyphMap; color: string }> = {
  success: { name: 'checkmark-circle', color: colors.success },
  error: { name: 'close-circle', color: colors.danger },
  info: { name: 'information-circle', color: colors.info },
}

export function ToastViewport({ toast, onDismiss }: { toast: ToastMessage | null; onDismiss: () => void }) {
  const insets = useSafeAreaInsets()
  const [opacity] = useState(() => new Animated.Value(0))

  useEffect(() => {
    if (!toast) return
    opacity.setValue(0)
    Animated.timing(opacity, { toValue: 1, duration: 180, useNativeDriver: true }).start()
  }, [toast, opacity])

  if (!toast) return null
  const icon = ICONS[toast.variant]

  return (
    <View pointerEvents="box-none" style={[styles.wrapper, { top: insets.top + spacing.sm }]}>
      <Animated.View
        style={[
          styles.toast,
          { opacity, transform: [{ translateY: opacity.interpolate({ inputRange: [0, 1], outputRange: [-12, 0] }) }] },
        ]}
      >
        <Pressable
          onPress={onDismiss}
          style={styles.content}
          accessibilityRole="alert"
          accessibilityLabel={`${toast.title}. ${toast.description ?? ''}`}
        >
          <Ionicons name={icon.name} size={22} color={icon.color} />
          <View style={styles.texts}>
            <Text style={styles.title}>{toast.title}</Text>
            {toast.description && <Text style={styles.description}>{toast.description}</Text>}
          </View>
        </Pressable>
      </Animated.View>
    </View>
  )
}

const styles = StyleSheet.create({
  wrapper: { position: 'absolute', left: spacing.lg, right: spacing.lg, zIndex: 100 },
  toast: {
    backgroundColor: colors.surface,
    borderRadius: radius.md,
    borderWidth: 1,
    borderColor: colors.border,
    shadowColor: '#000',
    shadowOpacity: 0.15,
    shadowRadius: 12,
    shadowOffset: { width: 0, height: 4 },
    elevation: 6,
  },
  content: { flexDirection: 'row', gap: spacing.md, padding: spacing.lg, alignItems: 'flex-start' },
  texts: { flex: 1, gap: 2 },
  title: { fontSize: 15, fontWeight: '700', color: colors.ink },
  description: { fontSize: 14, color: colors.text },
})
