import { Pressable, StyleSheet, Text } from 'react-native'
import { colors, radius, spacing } from '../theme'

interface ChipProps {
  label: string
  selected: boolean
  onPress: () => void
}

export function Chip({ label, selected, onPress }: ChipProps) {
  return (
    <Pressable
      onPress={onPress}
      accessibilityRole="button"
      accessibilityState={{ selected }}
      style={[styles.chip, selected && styles.selected]}
    >
      <Text style={[styles.text, selected && styles.selectedText]}>{label}</Text>
    </Pressable>
  )
}

const styles = StyleSheet.create({
  chip: {
    paddingHorizontal: spacing.md,
    paddingVertical: spacing.sm,
    borderRadius: radius.pill,
    borderWidth: 1,
    borderColor: colors.border,
    backgroundColor: colors.surface,
  },
  selected: { backgroundColor: colors.brandSoft, borderColor: colors.brand },
  text: { fontSize: 14, fontWeight: '600', color: colors.text },
  selectedText: { color: colors.brandDark },
})
