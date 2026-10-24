import { CheckCircle2, Info, X, XCircle, type LucideIcon } from 'lucide-react'
import type { Toast, ToastVariant } from '../../contexts/ToastContextDefinition'
import { cn } from '../../utils/cn'

const VARIANT_STYLES: Record<ToastVariant, { icon: LucideIcon; classes: string }> = {
  success: { icon: CheckCircle2, classes: 'border-emerald-200 [&_svg.toast-icon]:text-emerald-500' },
  error: { icon: XCircle, classes: 'border-red-200 [&_svg.toast-icon]:text-red-500' },
  info: { icon: Info, classes: 'border-slate-200 [&_svg.toast-icon]:text-slate-500' },
}

interface ToastViewportProps {
  toasts: Toast[]
  onDismiss: (id: string) => void
}

export function ToastViewport({ toasts, onDismiss }: ToastViewportProps) {
  return (
    <div
      aria-live="polite"
      className="pointer-events-none fixed inset-x-4 top-4 z-[60] flex flex-col items-center gap-2 sm:inset-x-auto sm:right-6 sm:items-end"
    >
      {toasts.map((toast) => {
        const { icon: Icon, classes } = VARIANT_STYLES[toast.variant]
        return (
          <div
            key={toast.id}
            role={toast.variant === 'error' ? 'alert' : 'status'}
            className={cn(
              'pointer-events-auto flex w-full max-w-sm animate-[slide-in_200ms_ease-out] items-start gap-3 rounded-lg border bg-white p-4 shadow-lg',
              classes,
            )}
          >
            <Icon className="toast-icon mt-0.5 size-5 shrink-0" aria-hidden="true" />
            <div className="min-w-0 flex-1">
              <p className="text-sm font-semibold text-slate-900">{toast.title}</p>
              {toast.description && <p className="mt-0.5 text-sm text-slate-600">{toast.description}</p>}
            </div>
            <button
              type="button"
              onClick={() => onDismiss(toast.id)}
              aria-label="Cerrar notificación"
              className="rounded p-0.5 text-slate-400 hover:text-slate-700"
            >
              <X className="size-4" />
            </button>
          </div>
        )
      })}
    </div>
  )
}
