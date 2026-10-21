import { CalendarRange, LayoutDashboard, PlusCircle, Search } from 'lucide-react'
import { Suspense, useCallback, useState } from 'react'
import { Outlet, useLocation } from 'react-router-dom'
import { PATHS } from '../../routes/paths'
import { PUBLISHER_ROLES } from '../../routes/roles'
import { AIChatWidget } from '../common/AIChatWidget'
import { ErrorBoundary } from '../common/ErrorBoundary'
import { Navbar } from '../common/Navbar'
import { OfflineBanner } from '../common/OfflineBanner'
import { Sidebar, type SidebarItem } from '../common/Sidebar'
import { FullPageSpinner } from '../common/Spinner'

const NAVIGATION: SidebarItem[] = [
  { label: 'Dashboard', to: PATHS.home, icon: LayoutDashboard },
  { label: 'Catálogo', to: PATHS.catalog, icon: Search },
  { label: 'Mis reservas', to: PATHS.reservations, icon: CalendarRange },
  { label: 'Publicar maquinaria', to: PATHS.publishEquipment, icon: PlusCircle, allowedRoles: PUBLISHER_ROLES },
]

export function AppLayout() {
  const { pathname } = useLocation()
  const [isSidebarOpen, setIsSidebarOpen] = useState(false)
  const toggleSidebar = useCallback(() => setIsSidebarOpen((open) => !open), [])
  const closeSidebar = useCallback(() => setIsSidebarOpen(false), [])

  return (
    <div className="min-h-screen">
      <OfflineBanner />
      <Navbar onToggleSidebar={toggleSidebar} />
      <div className="flex">
        <Sidebar items={NAVIGATION} isOpen={isSidebarOpen} onClose={closeSidebar} />
        <main className="min-w-0 flex-1 p-4 pb-24 sm:p-6 sm:pb-24 lg:p-8 lg:pb-24">
          <ErrorBoundary key={pathname} inline>
            <Suspense fallback={<FullPageSpinner />}>
              <Outlet />
            </Suspense>
          </ErrorBoundary>
        </main>
      </div>
      <AIChatWidget />
    </div>
  )
}
