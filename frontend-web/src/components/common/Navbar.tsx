import { HardHat, LogOut, Menu } from 'lucide-react'
import { Link } from 'react-router-dom'
import { useAuth } from '../../hooks/useAuth'
import { PATHS } from '../../routes/paths'
import { Button } from './Button'

interface NavbarProps {
  onToggleSidebar: () => void
}

export function Navbar({ onToggleSidebar }: NavbarProps) {
  const { user, logout } = useAuth()

  return (
    <header className="sticky top-0 z-30 flex h-16 items-center gap-3 border-b border-slate-200 bg-white/90 px-4 backdrop-blur sm:px-6">
      <button
        type="button"
        onClick={onToggleSidebar}
        aria-label="Abrir menú de navegación"
        className="rounded-lg p-2 text-slate-600 hover:bg-slate-100 lg:hidden"
      >
        <Menu className="size-5" />
      </button>

      <Link to={PATHS.home} className="flex items-center gap-2 font-bold text-slate-900">
        <span className="flex size-9 items-center justify-center rounded-lg bg-brand-500">
          <HardHat className="size-5" aria-hidden="true" />
        </span>
        <span className="text-lg tracking-tight">RentEquip</span>
      </Link>

      {user && (
        <div className="ml-auto flex items-center gap-3">
          <div className="hidden text-right sm:block">
            <p className="text-sm font-medium text-slate-900">{user.companyName}</p>
            <p className="text-xs text-slate-500">{user.email}</p>
          </div>
          <Button variant="ghost" size="sm" onClick={logout} leftIcon={<LogOut className="size-4" />}>
            <span className="hidden sm:inline">Cerrar sesión</span>
          </Button>
        </div>
      )}
    </header>
  )
}
