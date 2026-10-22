import type { FieldValues, Path, UseFormSetError } from 'react-hook-form'
import { ApiError } from './apiError'

/**
 * Copies the backend's field validation errors (400 VALIDATION_ERROR) into the form, so each message
 * appears under its input. `fieldMap` translates backend field paths, e.g. `company.taxId` -> `taxId`.
 * Returns true when at least one error was attached to a field.
 */
export function applyServerFieldErrors<T extends FieldValues>(
  error: unknown,
  setError: UseFormSetError<T>,
  fieldMap: Partial<Record<string, Path<T>>> = {},
): boolean {
  const apiError = ApiError.from(error)
  let applied = false
  for (const detail of apiError.details) {
    const field = fieldMap[detail.field] ?? (detail.field as Path<T>)
    setError(field, { type: 'server', message: detail.message })
    applied = true
  }
  return applied
}
