import { useCallback, useLayoutEffect, useRef, useState } from 'react'
import { ApiError } from '../utils/apiError'
import { useToast } from './useToast'

interface MutationOptions<TResult> {
  /** Toast shown when the request succeeds. Omit it to stay silent. */
  successMessage?: string | ((result: TResult) => string)
  /** Title of the error toast; the description is the friendly message of the HTTP error. */
  errorTitle?: string
  /** Set to false when the caller shows the error itself, e.g. under form fields. */
  showErrorToast?: boolean
  onSuccess?: (result: TResult) => void
  onError?: (error: ApiError) => void
}

export interface MutationResult<TArgs extends unknown[], TResult> {
  /** Resolves with the result, or with `undefined` when the request failed (the error is in `error`). */
  mutate: (...args: TArgs) => Promise<TResult | undefined>
  isLoading: boolean
  error: ApiError | null
}

/**
 * Runs a write request (POST/PUT/PATCH/DELETE) with loading state, success/error toasts and a guard
 * against double submits: while a call is in flight, further calls are ignored.
 */
export function useMutation<TArgs extends unknown[], TResult>(
  mutationFn: (...args: TArgs) => Promise<TResult>,
  options: MutationOptions<TResult> = {},
): MutationResult<TArgs, TResult> {
  const toast = useToast()
  const [isLoading, setIsLoading] = useState(false)
  const [error, setError] = useState<ApiError | null>(null)
  const inFlight = useRef(false)
  const optionsRef = useRef(options)
  useLayoutEffect(() => {
    optionsRef.current = options
  })

  const mutate = useCallback(
    async (...args: TArgs): Promise<TResult | undefined> => {
      if (inFlight.current) return undefined
      inFlight.current = true
      setIsLoading(true)
      setError(null)

      const { successMessage, errorTitle = 'No se pudo completar la acción', showErrorToast = true, onSuccess, onError } =
        optionsRef.current
      try {
        const result = await mutationFn(...args)
        if (successMessage) {
          toast.success(typeof successMessage === 'function' ? successMessage(result) : successMessage)
        }
        onSuccess?.(result)
        return result
      } catch (caught) {
        const apiError = ApiError.from(caught)
        setError(apiError)
        if (showErrorToast) toast.error(errorTitle, apiError.message)
        onError?.(apiError)
        return undefined
      } finally {
        inFlight.current = false
        setIsLoading(false)
      }
    },
    [mutationFn, toast],
  )

  return { mutate, isLoading, error }
}
