import { AlertOctagon } from 'lucide-react'
import { Component, type ErrorInfo, type ReactNode } from 'react'
import { Button } from './Button'

interface ErrorBoundaryProps {
  children: ReactNode
  /** Smaller fallback that keeps the surrounding layout, for boundaries inside the app shell. */
  inline?: boolean
}

interface ErrorBoundaryState {
  hasError: boolean
}

/**
 * Catches render errors below it and shows a recovery screen instead of a blank page.
 * Error boundaries still have to be class components: hooks cannot catch render errors.
 */
export class ErrorBoundary extends Component<ErrorBoundaryProps, ErrorBoundaryState> {
  state: ErrorBoundaryState = { hasError: false }

  static getDerivedStateFromError(): ErrorBoundaryState {
    return { hasError: true }
  }

  componentDidCatch(error: Error, info: ErrorInfo): void {
    console.error('Unhandled render error', error, info.componentStack)
  }

  private handleRetry = (): void => {
    this.setState({ hasError: false })
  }

  render(): ReactNode {
    if (!this.state.hasError) return this.props.children

    return (
      <div
        role="alert"
        className={
          this.props.inline
            ? 'flex flex-col items-center gap-4 py-16 text-center'
            : 'flex min-h-screen flex-col items-center justify-center gap-4 p-6 text-center'
        }
      >
        <AlertOctagon className="size-12 text-red-500" aria-hidden="true" />
        <div className="space-y-1">
          <h1 className="text-xl font-bold text-slate-900">Algo salió mal</h1>
          <p className="max-w-md text-sm text-slate-600">
            Ocurrió un error inesperado al mostrar esta pantalla. Puedes reintentar o volver a cargar la página.
          </p>
        </div>
        <div className="flex gap-3">
          <Button variant="secondary" onClick={this.handleRetry}>
            Reintentar
          </Button>
          <Button onClick={() => window.location.reload()}>Recargar página</Button>
        </div>
      </div>
    )
  }
}
