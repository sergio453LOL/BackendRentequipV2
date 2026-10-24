import { LogOut, X, type LucideIcon } from 'lucide-react'
import { Link, NavLink, useLocation } from 'react-router-dom'
import { useAuth } from '../../hooks/useAuth'
import { PATHS } from '../../routes/paths'
import type { UserRole } from '../../types'
import { cn } from '../../utils/cn'
import { MODE_LABELS, type WorkspaceMode } from '../../utils/workspaceMode'
import { BrandMark } from './BrandMark'
import { ModeSwitch } from './ModeSwitch'
import { CatalogSearch } from './TopBar'

export interface SidebarItem {
  label: string
  /** Path, optionally with a query string (`/reservations?role=OWNER`). */
  to: string
  icon: LucideIcon
  allowedRoles?: UserRole[]
}

interface SidebarProps {
  items: SidebarItem[]
  mode: WorkspaceMode
  onModeChange: (mode: WorkspaceMode) => void
  isOpen: boolean
  onClose: () => void
}

const ROLE_LABELS: Record<UserRole, string> = {
  ADMIN: 'Administrador',
  MANAGER: 'Gerente',
  OPERATOR: 'Operador',
}

/** An item is active when its path matches and every query parameter it names matches too. */
function isItemActive(item: SidebarItem, pathname: string, search: string): boolean {
  const [path, query] = item.to.split('?')
  if (path !== pathname) return false
  if (!query) return true
  const current = new URLSearchParams(search)
  return [...new URLSearchParams(query)].every(([key, value]) => current.get(key) === value)
}

/** Asphalt sidebar: the product, the company, the mode and its screens, and the account at the foot. */
export function Sidebar({ items, mode, onModeChange, isOpen, onClose }: SidebarProps) {
  const { user, logout } = useAuth()
  const { pathname, search } = useLocation()
  const visibleItems = items.filter(
    (item) => !item.allowedRoles || (user !== null && item.allowedRoles.includes(user.role)),
  )

  return (
    <aside
      className={cn(
        'z-40 flex-col bg-ink text-slate-300',
        'fixed inset-0 lg:sticky lg:top-0 lg:flex lg:h-screen lg:w-[248px] lg:shrink-0',
        isOpen ? 'flex' : 'hidden',
      )}
    >
      <div className="flex h-14 items-center gap-2.5 px-5">
        <Link to={PATHS.home} onClick={onClose} className="flex items-center gap-2.5" aria-label="RentEquip, ir al panel">
          <BrandMark className="size-7 text-brand" />
          <span className="text-base font-bold tracking-tight text-white">RentEquip</span>
        </Link>
        <button
          type="button"
          onClick={onClose}
          aria-label="Cerrar menú"
          className="ml-auto flex size-10 items-center justify-center rounded-md text-slate-300 hover:bg-white/10 lg:hidden"
        >
          <X className="size-5" />
        </button>
      </div>

      {user && (
        <div className="px-5 pt-2 pb-4">
          <p className="truncate text-sm font-semibold text-white" title={user.companyName}>
            {user.companyName}
          </p>
          <p className="text-xs text-slate-400">{ROLE_LABELS[user.role]}</p>
        </div>
      )}

      <ModeSwitch mode={mode} onChange={onModeChange} tone="dark" className="mx-4 mb-2 lg:hidden" />
      <CatalogSearch id="menu-search" className="mx-4 mb-4 sm:hidden" />

      <p className="px-5 pt-3 pb-2 text-xs font-medium text-slate-400">Modo {MODE_LABELS[mode].title.toLowerCase()}</p>
      <nav aria-label="Navegación principal" className="flex flex-col gap-0.5 px-3">
        {visibleItems.map((item) => {
          const isActive = isItemActive(item, pathname, search)
          const Icon = item.icon
          return (
            <NavLink
              key={item.to}
              to={item.to}
              onClick={onClose}
              aria-current={isActive ? 'page' : undefined}
              className={cn(
                'relative flex h-10 items-center gap-3 rounded-md px-3 text-sm font-medium transition-colors duration-200',
                isActive ? 'bg-ink-hover text-white' : 'text-slate-300 hover:bg-white/5 hover:text-white',
              )}
            >
              {isActive && <span className="absolute inset-y-2 left-0 w-0.5 rounded-full bg-brand" aria-hidden="true" />}
              <Icon className={cn('size-[18px] shrink-0', isActive && 'text-brand')} strokeWidth={1.75} aria-hidden="true" />
              {item.label}
            </NavLink>
          )
        })}
      </nav>

      {user && (
        <div className="mt-auto flex items-center gap-2 border-t border-white/10 px-5 py-4">
          <p className="min-w-0 flex-1 truncate text-xs text-slate-400" title={user.email}>
            {user.email}
          </p>
          <button
            type="button"
            onClick={logout}
            className="flex h-8 shrink-0 items-center gap-1.5 rounded-md px-2 text-sm text-slate-300 hover:bg-white/10 hover:text-white"
          >
            <LogOut className="size-4" strokeWidth={1.75} aria-hidden="true" />
            Salir
          </button>
        </div>
      )}
    </aside>
  )
}
