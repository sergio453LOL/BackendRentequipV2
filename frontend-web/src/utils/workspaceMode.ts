import { PATHS } from '../routes/paths'

/** Which side of the marketplace the company is acting on: renting machines in, or renting its fleet out. */
export type WorkspaceMode = 'renter' | 'owner'

const STORAGE_KEY = 'rentequip.mode'

export const MODE_LABELS: Record<WorkspaceMode, { title: string; caption: string }> = {
  renter: { title: 'Arrendatario', caption: 'Comprador' },
  owner: { title: 'Arrendador', caption: 'Vendedor' },
}

/** Where each mode starts. */
export const MODE_HOME: Record<WorkspaceMode, string> = {
  renter: PATHS.catalog,
  owner: PATHS.home,
}

/**
 * The mode a screen belongs to, so the switcher always matches what is on screen. Screens used by both
 * sides (a machine's page) return null and keep the last mode.
 */
export function modeForLocation(pathname: string, search: string): WorkspaceMode | null {
  if (pathname === PATHS.catalog) return 'renter'
  if (pathname === PATHS.home || pathname === PATHS.fleet || pathname === PATHS.publishEquipment) return 'owner'
  if (pathname === PATHS.reservations) return new URLSearchParams(search).get('role') === 'OWNER' ? 'owner' : 'renter'
  return null
}

export function readStoredMode(): WorkspaceMode {
  try {
    return localStorage.getItem(STORAGE_KEY) === 'owner' ? 'owner' : 'renter'
  } catch {
    return 'renter'
  }
}

export function storeMode(mode: WorkspaceMode): void {
  try {
    localStorage.setItem(STORAGE_KEY, mode)
  } catch {
    // Private mode or blocked storage: the mode still follows the screen.
  }
}
