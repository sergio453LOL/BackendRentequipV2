import { Ionicons } from '@expo/vector-icons'
import DateTimePicker, { DateTimePickerAndroid, type DateTimePickerEvent } from '@react-native-community/datetimepicker'
import { Platform, Pressable, StyleSheet, Text, View } from 'react-native'
import { colors, radius, spacing } from '../theme'
import type { IsoDate } from '../types/api'
import { formatDate, toIsoDate } from '../utils/format'

interface DateFieldProps {
  label: string
  value: IsoDate | null
  onChange: (value: IsoDate) => void
  minimumDate?: Date
  error?: string
}

function parse(value: IsoDate): Date {
  const [year, month, day] = value.split('-').map(Number)
  return new Date(year, month - 1, day)
}

/** Native date picker: Android opens the system dialog, iOS shows the compact inline control. */
export function DateField({ label, value, onChange, minimumDate, error }: DateFieldProps) {
  const current = value ? parse(value) : (minimumDate ?? new Date())

  function handleChange(event: DateTimePickerEvent, date?: Date) {
    if (event.type === 'set' && date) onChange(toIsoDate(date))
  }

  return (
    <View style={styles.container}>
      <Text style={styles.label}>{label}</Text>
      {Platform.OS === 'ios' ? (
        <DateTimePicker value={current} mode="date" display="compact" minimumDate={minimumDate} onChange={handleChange} locale="es-PE" />
      ) : (
        <Pressable
          accessibilityRole="button"
          accessibilityLabel={`${label}: ${value ? formatDate(value) : 'sin elegir'}`}
          onPress={() => DateTimePickerAndroid.open({ value: current, mode: 'date', minimumDate, onChange: handleChange })}
          style={[styles.input, error ? styles.inputError : null]}
        >
          <Text style={value ? styles.value : styles.placeholder}>{value ? formatDate(value) : 'Elegir fecha'}</Text>
          <Ionicons name="calendar-outline" size={18} color={colors.muted} />
        </Pressable>
      )}
      {error && <Text style={styles.error}>{error}</Text>}
    </View>
  )
}

const styles = StyleSheet.create({
  container: { flex: 1, gap: spacing.xs },
  label: { fontSize: 14, fontWeight: '600', color: colors.text },
  input: {
    minHeight: 48,
    borderWidth: 1,
    borderColor: colors.border,
    borderRadius: radius.md,
    paddingHorizontal: spacing.md,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    backgroundColor: colors.surface,
  },
  inputError: { borderColor: colors.danger },
  value: { fontSize: 16, color: colors.ink },
  placeholder: { fontSize: 16, color: colors.muted },
  error: { fontSize: 12, color: colors.danger },
})
