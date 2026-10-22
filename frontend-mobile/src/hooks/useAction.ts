import { useCallback, useRef, useState } from 'react'
import { ApiError } from '../utils/apiError'
import { useToast } from './useToast'

interface ActionOptions<T> {
  successMessage?: string
  errorTitle?: string
  onSuccess?: (result: T) => void
}

/**
 * Runs a write request with a loading flag, a success/error toast and a guard against double taps:
 * while one call is running, new calls are ignored.
 */
export function useAction() {
  const toast = useToast()
  const [isRunning, setIsRunning] = useState(false)
  const inFlight = useRef(false)

  const run = useCallback(
    async <T,>(action: () => Promise<T>, options: ActionOptions<T> = {}): Promise<T | undefined> => {
      if (inFlight.current) return undefined
      inFlight.current = true
      setIsRunning(true)
      try {
        const result = await action()
        if (options.successMessage) toast.success(options.successMessage)
        options.onSuccess?.(result)
        return result
      } catch (caught) {
        toast.error(options.errorTitle ?? 'No se pudo completar la acción', ApiError.from(caught).message)
        return undefined
      } finally {
        inFlight.current = false
        setIsRunning(false)
      }
    },
    [toast],
  )

  return { run, isRunning }
}
