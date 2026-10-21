import type { UserRole } from '../types'

/** Roles the backend lets publish equipment and manage reservation status (`hasAnyRole('ADMIN','MANAGER')`). */
export const PUBLISHER_ROLES: UserRole[] = ['ADMIN', 'MANAGER']

export function canPublish(role: UserRole | undefined): boolean {
  return role !== undefined && PUBLISHER_ROLES.includes(role)
}
