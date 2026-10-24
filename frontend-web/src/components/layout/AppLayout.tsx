import { CalendarRange, LayoutDashboard, PlusCircle, Search } from 'lucide-react'
import { Suspense, useCallback, useState } from 'react'
import { Outlet, useLocation } from 'react-router-dom'
import { PATHS } from '../../routes/paths'
import { PUBLISHER_ROLES } from '../../routes/roles'
import { AIChatWidget } from '../common/AIChatWidget'
import { ErrorBoundary } from '../common/ErrorBoundary'
import { OfflineBanner } from '../common/OfflineBanner'
import { Sidebar, type SidebarItem } from '../common/Sidebar'
import { FullPageSpinner } from '../common/Spinner'
import { TopBar } from '../common/TopBar'

const NAVIGATION: SidebarItem[] = [
  { label: 'Panel', to: PATHS.home, icon: LayoutDashboard },
  { label: 'Catálogo', to: PATHS.catalog, icon: Search },
  { label: 'Reservas', to: PATHS.reservations, icon: CalendarRange },
  { label: 'Publicar maquinaria', to: PATHS.publishEquipment, icon: PlusCircle, allowedRoles: PUBLISHER_ROLES },
]

/**
 * Framed sheet: the sidebar and the 48px top bar share one tinted frame with no rule between them, and
 * the screen sits on a white sheet inset 8px from the window's right and bottom edges (16px corners,
 * 1px edge, no shadow). On a phone the frame folds away and the sheet is the screen.
 */
export function AppLayout() {
  const { pathname } = useLocation()
  const [isMenuOpen, setIsMenuOpen] = useState(false)
  const [isAssistantOpen, setIsAssistantOpen] = useState(false)
  const openMenu = useCallback(() => setIsMenuOpen(true), [])
  const closeMenu = useCallback(() => setIsMenuOpen(false), [])
  const toggleAssistant = useCallback(() => setIsAssistantOpen((open) => !open), [])
  const closeAssistant = useCallback(() => setIsAssistantOpen(false), [])

  return (
    <div className="min-h-screen bg-frame lg:flex">
      <Sidebar items={NAVIGATION} isOpen={isMenuOpen} onClose={closeMenu} />
      <div className="flex min-h-screen min-w-0 flex-1 flex-col">
        <OfflineBanner />
        <TopBar onOpenMenu={openMenu} onToggleAssistant={toggleAssistant} isAssistantOpen={isAssistantOpen} />
        <main className="flex-1 bg-sheet lg:mr-2 lg:mb-2 lg:rounded-[16px] lg:border lg:border-line">
          <div className="px-4 py-5 sm:px-6 lg:p-6">
            <ErrorBoundary key={pathname} inline>
              <Suspense fallback={<FullPageSpinner />}>
                <Outlet />
              </Suspense>
            </ErrorBoundary>
          </div>
        </main>
      </div>
      <AIChatWidget isOpen={isAssistantOpen} onClose={closeAssistant} />
    </div>
  )
}
