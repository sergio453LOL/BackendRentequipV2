import { LogOut, X, type LucideIcon } from 'lucide-react'
import { Link, NavLink } from 'react-router-dom'
import { useAuth } from '../../hooks/useAuth'
import { PATHS } from '../../routes/paths'
import type { UserRole } from '../../types'
import { cn } from '../../utils/cn'
import { BrandWordmark } from './BrandMark'
import { CatalogSearch } from './TopBar'

export interface SidebarItem {
  label: string
  to: string
  icon: LucideIcon
  allowedRoles?: UserRole[]
}

interface SidebarProps {
  items: SidebarItem[]
  isOpen: boolean
  onClose: () => void
}

const ROLE_LABELS: Record<UserRole, string> = {
  ADMIN: 'Administrador',
  MANAGER: 'Gerente',
  OPERATOR: 'Operador',
}

/**
 * The left arm of the frame: the product, the company the user acts for, the screens and the account.
 * On a phone it opens as a full-screen sheet from the top bar's menu button.
 */
export function Sidebar({ items, isOpen, onClose }: SidebarProps) {
  const { user, logout } = useAuth()
  const visibleItems = items.filter(
    (item) => !item.allowedRoles || (user !== null && item.allowedRoles.includes(user.role)),
  )

  return (
    <aside
      className={cn(
        'z-40 flex-col bg-frame',
        'fixed inset-0 lg:sticky lg:top-0 lg:z-0 lg:flex lg:h-screen lg:w-[228px]',
        isOpen ? 'flex' : 'hidden',
      )}
    >
      <div className="flex h-12 items-center gap-2 px-4">
        <Link to={PATHS.home} onClick={onClose} aria-label="RentEquip, ir al panel">
          <BrandWordmark />
        </Link>
        <button
          type="button"
          onClick={onClose}
          aria-label="Cerrar menú"
          className="ml-auto flex size-10 items-center justify-center rounded-md text-slate-600 hover:bg-active lg:hidden"
        >
          <X className="size-5" />
        </button>
      </div>

      {user && (
        <div className="mx-2 mt-1 mb-4 rounded-md px-2 py-1.5">
          <p className="truncate text-sm font-medium text-ink" title={user.companyName}>
            {user.companyName}
          </p>
          <p className="text-xs text-slate-500">{ROLE_LABELS[user.role]}</p>
        </div>
      )}

      <CatalogSearch id="menu-search" className="mx-4 mb-4 sm:hidden" />

      <nav aria-label="Navegación principal" className="flex flex-col gap-0.5 px-2">
        {visibleItems.map(({ label, to, icon: Icon }) => (
          <NavLink
            key={to}
            to={to}
            end
            onClick={onClose}
            className={({ isActive }) =>
              cn(
                'flex h-10 items-center gap-2.5 rounded-md px-2.5 text-sm transition-colors lg:h-8',
                isActive ? 'bg-active text-ink' : 'text-slate-600 hover:bg-raised hover:text-ink',
              )
            }
          >
            <Icon className="size-4 shrink-0" strokeWidth={1.75} aria-hidden="true" />
            {label}
          </NavLink>
        ))}
      </nav>

      {user && (
        <div className="mt-auto flex items-center gap-2 px-4 py-4">
          <p className="min-w-0 flex-1 truncate text-xs text-slate-500" title={user.email}>
            {user.email}
          </p>
          <button
            type="button"
            onClick={logout}
            className="flex h-8 shrink-0 items-center gap-1.5 rounded-md px-2 text-sm text-slate-600 hover:bg-active hover:text-ink"
          >
            <LogOut className="size-4" strokeWidth={1.75} aria-hidden="true" />
            Salir
          </button>
        </div>
      )}
    </aside>
  )
}
