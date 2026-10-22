import { createContext, useCallback, useMemo, useRef, useState, type ReactNode } from 'react'
import { ToastViewport } from '../components/ToastViewport'

export type ToastVariant = 'success' | 'error' | 'info'

export interface ToastMessage {
  id: number
  variant: ToastVariant
  title: string
  description?: string
}

export interface ToastContextValue {
  success: (title: string, description?: string) => void
  error: (title: string, description?: string) => void
  info: (title: string, description?: string) => void
}

export const ToastContext = createContext<ToastContextValue | null>(null)

const TOAST_DURATION_MS = 3_500

/** Non-blocking notifications for results of actions (saved, failed, offline…), shown on top of any screen. */
export function ToastProvider({ children }: { children: ReactNode }) {
  const [toast, setToast] = useState<ToastMessage | null>(null)
  const timer = useRef<ReturnType<typeof setTimeout> | null>(null)
  const nextId = useRef(0)

  const show = useCallback((variant: ToastVariant, title: string, description?: string) => {
    if (timer.current) clearTimeout(timer.current)
    nextId.current += 1
    setToast({ id: nextId.current, variant, title, description })
    timer.current = setTimeout(() => setToast(null), TOAST_DURATION_MS)
  }, [])

  const value = useMemo<ToastContextValue>(
    () => ({
      success: (title, description) => show('success', title, description),
      error: (title, description) => show('error', title, description),
      info: (title, description) => show('info', title, description),
    }),
    [show],
  )

  return (
    <ToastContext.Provider value={value}>
      {children}
      <ToastViewport toast={toast} onDismiss={() => setToast(null)} />
    </ToastContext.Provider>
  )
}
