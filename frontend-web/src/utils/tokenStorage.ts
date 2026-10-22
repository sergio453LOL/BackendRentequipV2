import type { AuthResponse, AuthUser, UserRole } from '../types'

const SESSION_KEY = 'rentequip.session'

interface StoredSession {
  accessToken: string
  refreshToken: string
  user: AuthUser
}

const USER_ROLES: readonly UserRole[] = ['ADMIN', 'MANAGER', 'OPERATOR']

function isStoredSession(value: unknown): value is StoredSession {
  if (typeof value !== 'object' || value === null) return false
  const session = value as Partial<StoredSession>
  const user = session.user
  return (
    typeof session.accessToken === 'string' &&
    typeof session.refreshToken === 'string' &&
    typeof user === 'object' &&
    user !== null &&
    typeof user.userId === 'number' &&
    typeof user.email === 'string' &&
    USER_ROLES.includes(user.role) &&
    typeof user.companyId === 'number' &&
    typeof user.companyName === 'string'
  )
}

function readSession(): StoredSession | null {
  try {
    const raw = localStorage.getItem(SESSION_KEY)
    if (!raw) return null
    const parsed: unknown = JSON.parse(raw)
    return isStoredSession(parsed) ? parsed : null
  } catch {
    return null
  }
}

export function toAuthUser(auth: AuthResponse): AuthUser {
  return {
    userId: auth.userId,
    email: auth.email,
    role: auth.role,
    companyId: auth.companyId,
    companyName: auth.companyName,
  }
}

/** Single place that knows where the session lives, so the storage strategy can change without touching callers. */
export const tokenStorage = {
  getAccessToken: (): string | null => readSession()?.accessToken ?? null,
  getRefreshToken: (): string | null => readSession()?.refreshToken ?? null,
  getUser: (): AuthUser | null => readSession()?.user ?? null,

  setSession(auth: AuthResponse): void {
    const session: StoredSession = {
      accessToken: auth.accessToken,
      refreshToken: auth.refreshToken,
      user: toAuthUser(auth),
    }
    localStorage.setItem(SESSION_KEY, JSON.stringify(session))
  },

  clear(): void {
    localStorage.removeItem(SESSION_KEY)
  },
}
