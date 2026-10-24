import { X } from 'lucide-react'
import { useEffect, useId, useRef, type ReactNode } from 'react'
import { createPortal } from 'react-dom'
import { cn } from '../../utils/cn'

interface ModalProps {
  isOpen: boolean
  title: string
  onClose: () => void
  children: ReactNode
  footer?: ReactNode
  size?: 'sm' | 'md' | 'lg'
  /** Blocks closing (Escape, backdrop, X) while a request is running. */
  isDismissable?: boolean
}

const SIZE_CLASSES = { sm: 'max-w-sm', md: 'max-w-lg', lg: 'max-w-2xl' } as const

export function Modal({ isOpen, title, onClose, children, footer, size = 'md', isDismissable = true }: ModalProps) {
  const titleId = useId()
  const dialogRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    if (!isOpen) return
    const previouslyFocused = document.activeElement instanceof HTMLElement ? document.activeElement : null
    const previousOverflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    dialogRef.current?.focus()

    return () => {
      document.body.style.overflow = previousOverflow
      previouslyFocused?.focus()
    }
  }, [isOpen])

  useEffect(() => {
    if (!isOpen || !isDismissable) return
    function handleKeyDown(event: KeyboardEvent) {
      if (event.key === 'Escape') onClose()
    }
    document.addEventListener('keydown', handleKeyDown)
    return () => document.removeEventListener('keydown', handleKeyDown)
  }, [isOpen, isDismissable, onClose])

  if (!isOpen) return null

  return createPortal(
    <div className="fixed inset-0 z-50 flex items-end justify-center p-0 sm:items-center sm:p-4">
      <div
        aria-hidden="true"
        className="absolute inset-0 animate-[fade-in_150ms_ease-out] bg-slate-900/50"
        onClick={isDismissable ? onClose : undefined}
      />
      <div
        ref={dialogRef}
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        tabIndex={-1}
        className={cn(
          'relative flex max-h-[92vh] w-full flex-col rounded-t-lg bg-white shadow-[0_16px_40px_rgba(0,0,0,0.16)] outline-none sm:rounded-lg',
          'animate-[slide-up_200ms_ease-out]',
          SIZE_CLASSES[size],
        )}
      >
        <header className="flex items-center justify-between gap-4 border-b border-slate-200 px-6 py-4">
          <h2 id={titleId} className="text-lg font-semibold text-slate-900">
            {title}
          </h2>
          <button
            type="button"
            onClick={onClose}
            disabled={!isDismissable}
            aria-label="Cerrar"
            className="rounded-lg p-1.5 text-slate-500 hover:bg-slate-100 disabled:opacity-40"
          >
            <X className="size-5" />
          </button>
        </header>
        <div className="overflow-y-auto px-6 py-5">{children}</div>
        {footer && (
          <footer className="flex flex-col-reverse gap-2 border-t border-slate-200 px-6 py-4 sm:flex-row sm:justify-end">
            {footer}
          </footer>
        )}
      </div>
    </div>,
    document.body,
  )
}
