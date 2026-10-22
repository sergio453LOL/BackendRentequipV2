import { Ionicons } from '@expo/vector-icons'
import { ActivityIndicator, Pressable, StyleSheet, Text, type StyleProp, type ViewStyle } from 'react-native'
import { colors, radius, spacing } from '../theme'

type Variant = 'primary' | 'secondary' | 'danger' | 'ghost'

interface AppButtonProps {
  title: string
  onPress: () => void
  variant?: Variant
  icon?: keyof typeof Ionicons.glyphMap
  isLoading?: boolean
  disabled?: boolean
  style?: StyleProp<ViewStyle>
  accessibilityHint?: string
}

const VARIANTS: Record<Variant, { background: string; text: string; border: string }> = {
  primary: { background: colors.brand, text: colors.ink, border: colors.brand },
  secondary: { background: colors.surface, text: colors.ink, border: colors.border },
  danger: { background: colors.danger, text: '#FFFFFF', border: colors.danger },
  ghost: { background: 'transparent', text: colors.text, border: 'transparent' },
}

/** Button that blocks repeated taps while `isLoading`, so a slow request cannot be sent twice. */
export function AppButton({
  title,
  onPress,
  variant = 'primary',
  icon,
  isLoading = false,
  disabled = false,
  style,
  accessibilityHint,
}: AppButtonProps) {
  const palette = VARIANTS[variant]
  const isDisabled = disabled || isLoading

  return (
    <Pressable
      onPress={onPress}
      disabled={isDisabled}
      accessibilityRole="button"
      accessibilityLabel={title}
      accessibilityHint={accessibilityHint}
      accessibilityState={{ disabled: isDisabled, busy: isLoading }}
      style={({ pressed }) => [
        styles.base,
        { backgroundColor: palette.background, borderColor: palette.border },
        pressed && !isDisabled && styles.pressed,
        isDisabled && styles.disabled,
        style,
      ]}
    >
      {isLoading ? (
        <ActivityIndicator color={palette.text} />
      ) : (
        icon && <Ionicons name={icon} size={18} color={palette.text} />
      )}
      <Text style={[styles.label, { color: palette.text }]}>{title}</Text>
    </Pressable>
  )
}

const styles = StyleSheet.create({
  base: {
    minHeight: 48,
    paddingHorizontal: spacing.lg,
    borderRadius: radius.md,
    borderWidth: 1,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: spacing.sm,
  },
  label: { fontSize: 16, fontWeight: '700' },
  pressed: { opacity: 0.85, transform: [{ scale: 0.99 }] },
  disabled: { opacity: 0.5 },
})
