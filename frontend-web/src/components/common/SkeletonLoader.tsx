import { cn } from '../../utils/cn'

type SkeletonVariant = 'text' | 'card' | 'list'

interface SkeletonLoaderProps {
  variant?: SkeletonVariant
  count?: number
  className?: string
}

function SkeletonBlock({ className }: { className?: string }) {
  return <div className={cn('rounded-md bg-raised', className)} />
}

function SkeletonCard() {
  return (
    <div className="overflow-hidden rounded-lg border border-line">
      <SkeletonBlock className="aspect-[4/3] w-full rounded-none" />
      <div className="space-y-2 px-4 pt-3 pb-4">
        <SkeletonBlock className="h-5 w-3/4" />
        <SkeletonBlock className="h-4 w-1/2" />
        <SkeletonBlock className="mt-4 h-6 w-1/3" />
      </div>
    </div>
  )
}

function SkeletonListItem() {
  return (
    <div className="flex items-center gap-4 rounded-lg border border-line p-4">
      <div className="flex-1 space-y-2">
        <SkeletonBlock className="h-4 w-1/2" />
        <SkeletonBlock className="h-3 w-1/3" />
      </div>
      <SkeletonBlock className="h-6 w-20" />
    </div>
  )
}

export function SkeletonLoader({ variant = 'text', count = 3, className }: SkeletonLoaderProps) {
  const items = Array.from({ length: count }, (_, index) => index)

  return (
    <div
      role="status"
      aria-label="Cargando contenido"
      className={cn(
        variant === 'card' ? 'grid gap-4 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4' : 'space-y-3',
        className,
      )}
    >
      {items.map((index) => {
        if (variant === 'card') return <SkeletonCard key={index} />
        if (variant === 'list') return <SkeletonListItem key={index} />
        return <SkeletonBlock key={index} className={cn('h-4', index === count - 1 ? 'w-2/3' : 'w-full')} />
      })}
    </div>
  )
}
