import { AlertTriangle, RefreshCw, WifiOff } from 'lucide-react'
import type { ApiError } from '../../utils/apiError'
import { Button } from './Button'

interface ErrorStateProps {
  error: ApiError
  onRetry?: () => void
  title?: string
}

/** Inline error for a section whose data could not be loaded, with a retry when it makes sense. */
export function ErrorState({ error, onRetry, title = 'No pudimos cargar la información' }: ErrorStateProps) {
  const Icon = error.isNetworkError ? WifiOff : AlertTriangle

  return (
    <div role="alert" className="flex flex-col items-center gap-3 rounded-2xl border border-red-200 bg-red-50 px-6 py-10 text-center">
      <Icon className="size-8 text-red-500" aria-hidden="true" />
      <div className="space-y-1">
        <h3 className="font-semibold text-red-800">{title}</h3>
        <p className="text-sm text-red-700">{error.message}</p>
      </div>
      {onRetry && error.status !== 403 && error.status !== 404 && (
        <Button variant="secondary" size="sm" onClick={onRetry} leftIcon={<RefreshCw className="size-4" />}>
          Reintentar
        </Button>
      )}
    </div>
  )
}
