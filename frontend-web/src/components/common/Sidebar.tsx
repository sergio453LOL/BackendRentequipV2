import type { LucideIcon } from 'lucide-react'
import { NavLink } from 'react-router-dom'
import { useAuth } from '../../hooks/useAuth'
import type { UserRole } from '../../types'
import { cn } from '../../utils/cn'

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

export function Sidebar({ items, isOpen, onClose }: SidebarProps) {
  const { user } = useAuth()
  const visibleItems = items.filter(
    (item) => !item.allowedRoles || (user !== null && item.allowedRoles.includes(user.role)),
  )

  return (
    <>
      <div
        aria-hidden="true"
        onClick={onClose}
        className={cn(
          'fixed inset-0 z-30 bg-slate-900/40 transition-opacity lg:hidden',
          isOpen ? 'opacity-100' : 'pointer-events-none opacity-0',
        )}
      />
      <aside
        className={cn(
          'fixed inset-y-0 left-0 z-40 w-64 border-r border-slate-200 bg-white pt-16 transition-transform',
          'lg:sticky lg:top-16 lg:z-0 lg:h-[calc(100vh-4rem)] lg:translate-x-0 lg:pt-0',
          isOpen ? 'translate-x-0' : '-translate-x-full',
        )}
      >
        <nav aria-label="Navegación principal" className="flex flex-col gap-1 p-4">
          {visibleItems.map(({ label, to, icon: Icon }) => (
            <NavLink
              key={to}
              to={to}
              end
              onClick={onClose}
              className={({ isActive }) =>
                cn(
                  'flex items-center gap-3 rounded-lg px-3 py-2 text-sm font-medium transition-colors',
                  isActive
                    ? 'bg-brand-100 text-brand-800'
                    : 'text-slate-600 hover:bg-slate-100 hover:text-slate-900',
                )
              }
            >
              <Icon className="size-5" aria-hidden="true" />
              {label}
            </NavLink>
          ))}
        </nav>
      </aside>
    </>
  )
}
