import { StyleSheet, Text, TextInput, View, type TextInputProps } from 'react-native'
import { colors, radius, spacing } from '../theme'

interface TextFieldProps extends TextInputProps {
  label: string
  error?: string
  hint?: string
  required?: boolean
}

export function TextField({ label, error, hint, required, style, ...inputProps }: TextFieldProps) {
  const message = error ?? hint
  return (
    <View style={styles.container}>
      <Text style={styles.label}>
        {label}
        {required && <Text style={styles.required}> *</Text>}
      </Text>
      <TextInput
        accessibilityLabel={label}
        placeholderTextColor={colors.muted}
        style={[styles.input, error ? styles.inputError : null, style]}
        {...inputProps}
      />
      {message && (
        <Text style={[styles.message, error ? styles.errorText : null]} accessibilityLiveRegion="polite">
          {message}
        </Text>
      )}
    </View>
  )
}

const styles = StyleSheet.create({
  container: { gap: spacing.xs },
  label: { fontSize: 14, fontWeight: '600', color: colors.text },
  required: { color: colors.danger },
  input: {
    minHeight: 48,
    borderWidth: 1,
    borderColor: colors.border,
    borderRadius: radius.md,
    paddingHorizontal: spacing.md,
    fontSize: 16,
    color: colors.ink,
    backgroundColor: colors.surface,
  },
  inputError: { borderColor: colors.danger },
  message: { fontSize: 12, color: colors.muted },
  errorText: { color: colors.danger },
})
