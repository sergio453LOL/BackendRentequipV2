import { AnimatePresence, motion } from 'framer-motion'
import { CalendarRange, ClipboardList, LayoutDashboard, PlusCircle, Search, Sparkles, Truck } from 'lucide-react'
import { Suspense, useCallback, useEffect, useState } from 'react'
import { Outlet, useLocation, useNavigate } from 'react-router-dom'
import { PATHS } from '../../routes/paths'
import { PUBLISHER_ROLES } from '../../routes/roles'
import { MODE_HOME, modeForLocation, readStoredMode, storeMode, type WorkspaceMode } from '../../utils/workspaceMode'
import { AIChatWidget } from '../common/AIChatWidget'
import { ErrorBoundary } from '../common/ErrorBoundary'
import { OfflineBanner } from '../common/OfflineBanner'
import { Sidebar, type SidebarItem } from '../common/Sidebar'
import { FullPageSpinner } from '../common/Spinner'
import { TopBar } from '../common/TopBar'

const NAVIGATION: Record<WorkspaceMode, SidebarItem[]> = {
  renter: [
    { label: 'Catálogo', to: PATHS.catalog, icon: Search },
    { label: 'Mis reservas', to: `${PATHS.reservations}?role=RENTER`, icon: CalendarRange },
  ],
  owner: [
    { label: 'Panel', to: PATHS.home, icon: LayoutDashboard },
    { label: 'Mi flota', to: PATHS.fleet, icon: Truck },
    { label: 'Reservas de mis equipos', to: `${PATHS.reservations}?role=OWNER`, icon: ClipboardList },
    { label: 'Publicar maquinaria', to: PATHS.publishEquipment, icon: PlusCircle, allowedRoles: PUBLISHER_ROLES },
  ],
}

/**
 * Asphalt sidebar, white header and the screen on a light slate ground. The navigation follows the
 * workspace mode: renting machines in (catalogue, my reservations) or renting the fleet out (panel,
 * fleet, requests, publish).
 */
export function AppLayout() {
  const { pathname, search } = useLocation()
  const navigate = useNavigate()
  const [isMenuOpen, setIsMenuOpen] = useState(false)
  const [isAssistantOpen, setIsAssistantOpen] = useState(false)
  const [storedMode, setStoredMode] = useState<WorkspaceMode>(readStoredMode)

  // The screen decides the mode when it belongs to one side; neutral screens keep the last one.
  const routeMode = modeForLocation(pathname, search)
  const mode = routeMode ?? storedMode
  if (routeMode && routeMode !== storedMode) setStoredMode(routeMode)
  useEffect(() => storeMode(mode), [mode])

  const changeMode = useCallback(
    (next: WorkspaceMode) => {
      setStoredMode(next)
      setIsMenuOpen(false)
      navigate(MODE_HOME[next])
    },
    [navigate],
  )
  const openMenu = useCallback(() => setIsMenuOpen(true), [])
  const closeMenu = useCallback(() => setIsMenuOpen(false), [])
  const toggleAssistant = useCallback(() => setIsAssistantOpen((open) => !open), [])
  const closeAssistant = useCallback(() => setIsAssistantOpen(false), [])

  return (
    <div className="min-h-screen bg-slate-50 lg:flex">
      <Sidebar items={NAVIGATION[mode]} mode={mode} onModeChange={changeMode} isOpen={isMenuOpen} onClose={closeMenu} />
      <div className="flex min-h-screen min-w-0 flex-1 flex-col">
        <OfflineBanner />
        <TopBar
          mode={mode}
          onModeChange={changeMode}
          onOpenMenu={openMenu}
          onToggleAssistant={toggleAssistant}
          isAssistantOpen={isAssistantOpen}
        />
        <main className="flex-1 px-4 py-6 sm:px-6 lg:px-8">
          <ErrorBoundary key={pathname} inline>
            <Suspense fallback={<FullPageSpinner />}>
              <Outlet />
            </Suspense>
          </ErrorBoundary>
        </main>
      </div>
      <AIChatWidget isOpen={isAssistantOpen} onClose={closeAssistant} />

      {/* While exploring, the assistant also floats at hand. */}
      <AnimatePresence>
        {mode === 'renter' && !isAssistantOpen && (
          <motion.button
            type="button"
            onClick={toggleAssistant}
            initial={{ opacity: 0, scale: 0.8, y: 12 }}
            animate={{ opacity: 1, scale: 1, y: 0 }}
            exit={{ opacity: 0, scale: 0.8, y: 12 }}
            whileHover={{ scale: 1.05 }}
            whileTap={{ scale: 0.95 }}
            transition={{ type: 'spring', stiffness: 380, damping: 26 }}
            aria-label="Abrir asistente de IA"
            className="fixed right-5 bottom-5 z-30 flex h-12 items-center gap-2 rounded-full bg-ink pr-5 pl-4 text-sm font-semibold text-white shadow-[0_10px_30px_-6px_rgba(15,23,42,0.55)] ring-2 ring-brand/70"
          >
            <span className="absolute inset-0 -z-10 animate-ping rounded-full bg-brand/25 [animation-duration:2.6s] motion-reduce:hidden" aria-hidden="true" />
            <Sparkles className="size-5 text-brand" aria-hidden="true" />
            Pregúntale a la IA
          </motion.button>
        )}
      </AnimatePresence>
    </div>
  )
}
