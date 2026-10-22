import { WifiOff } from 'lucide-react'
import { useOnlineStatus } from '../../hooks/useOnlineStatus'

export function OfflineBanner() {
  const isOnline = useOnlineStatus()
  if (isOnline) return null

  return (
    <div role="status" className="flex items-center justify-center gap-2 bg-slate-900 px-4 py-2 text-sm text-white">
      <WifiOff className="size-4 text-brand-400" aria-hidden="true" />
      Sin conexión a internet. Los cambios no se guardarán hasta que vuelvas a conectarte.
    </div>
  )
}
