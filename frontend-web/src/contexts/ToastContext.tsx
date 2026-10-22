import { useCallback, useMemo, useReducer, type ReactNode } from 'react'
import { ToastViewport } from '../components/common/ToastViewport'
import { ToastContext, type Toast, type ToastContextValue } from './ToastContextDefinition'

const MAX_VISIBLE_TOASTS = 4
const TOAST_DURATION_MS = 4_500

type ToastAction = { type: 'add'; toast: Toast } | { type: 'remove'; id: string }

function toastReducer(toasts: Toast[], action: ToastAction): Toast[] {
  switch (action.type) {
    case 'add':
      return [...toasts, action.toast].slice(-MAX_VISIBLE_TOASTS)
    case 'remove':
      return toasts.filter((toast) => toast.id !== action.id)
  }
}

export function ToastProvider({ children }: { children: ReactNode }) {
  const [toasts, dispatch] = useReducer(toastReducer, [])

  const dismiss = useCallback((id: string) => dispatch({ type: 'remove', id }), [])

  const showToast = useCallback(
    (toast: Omit<Toast, 'id'>) => {
      const id = crypto.randomUUID()
      dispatch({ type: 'add', toast: { ...toast, id } })
      setTimeout(() => dismiss(id), TOAST_DURATION_MS)
    },
    [dismiss],
  )

  const value = useMemo<ToastContextValue>(
    () => ({
      showToast,
      success: (title, description) => showToast({ variant: 'success', title, description }),
      error: (title, description) => showToast({ variant: 'error', title, description }),
      info: (title, description) => showToast({ variant: 'info', title, description }),
    }),
    [showToast],
  )

  return (
    <ToastContext.Provider value={value}>
      {children}
      <ToastViewport toasts={toasts} onDismiss={dismiss} />
    </ToastContext.Provider>
  )
}
