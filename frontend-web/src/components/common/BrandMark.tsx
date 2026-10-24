import { cn } from '../../utils/cn'

/** RentEquip's mark: an excavator in profile (track, cab, boom and bucket) in the machinery amber. */
export function BrandMark({ className }: { className?: string }) {
  return (
    <svg viewBox="0 0 24 24" className={cn('size-6 shrink-0 text-accent', className)} aria-hidden="true">
      <rect x="1.5" y="17" width="13" height="4.5" rx="2.25" fill="currentColor" />
      <rect x="3" y="10.5" width="7.5" height="5.5" rx="1" fill="currentColor" />
      <path d="M9.5 12.5 15.5 5.5l5 4.5" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinejoin="round" strokeLinecap="round" />
      <path d="M18.8 10.2h4l-1.3 5.3-3.6-1.6z" fill="currentColor" />
    </svg>
  )
}

export function BrandWordmark({ className }: { className?: string }) {
  return (
    <span className={cn('flex items-center gap-2 text-[15px] font-semibold text-ink', className)}>
      <BrandMark />
      RentEquip
    </span>
  )
}
