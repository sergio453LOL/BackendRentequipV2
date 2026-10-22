import { zodResolver } from '@hookform/resolvers/zod'
import { useForm, type FieldValues, type UseFormProps, type UseFormReturn } from 'react-hook-form'
import type { z } from 'zod'

/**
 * React Hook Form wired to a Zod schema. Fields are validated when they lose focus and then on every
 * keystroke, so the user gets real-time feedback without being scolded before typing anything.
 */
export function useZodForm<TValues extends FieldValues>(
  schema: z.ZodType<TValues, TValues>,
  options?: Omit<UseFormProps<TValues>, 'resolver'>,
): UseFormReturn<TValues> {
  return useForm<TValues>({
    resolver: zodResolver(schema),
    mode: 'onTouched',
    ...options,
  })
}
