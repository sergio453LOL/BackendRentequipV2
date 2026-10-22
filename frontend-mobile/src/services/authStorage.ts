import * as SecureStore from 'expo-secure-store'
import type { AuthResponse, AuthUser, UserRole } from '../types/api'

/**
 * JWT persistence in the device's encrypted storage (iOS Keychain / Android Keystore) through
 * expo-secure-store. Tokens never touch AsyncStorage, which is plain text on disk.
 * Each value goes in its own key because some iOS versions reject values above ~2 KB.
 */
const KEYS = {
  accessToken: 'rentequip.accessToken',
  refreshToken: 'rentequip.refreshToken',
  user: 'rentequip.user',
} as const

const STORE_OPTIONS: SecureStore.SecureStoreOptions = {
  keychainAccessible: SecureStore.AFTER_FIRST_UNLOCK_THIS_DEVICE_ONLY,
}

const USER_ROLES: readonly UserRole[] = ['ADMIN', 'MANAGER', 'OPERATOR']

export interface StoredSession {
  accessToken: string
  refreshToken: string
  user: AuthUser
}

/** In-memory copy so the request interceptor does not hit the keychain on every call. */
let cachedSession: StoredSession | null = null

function isAuthUser(value: unknown): value is AuthUser {
  if (typeof value !== 'object' || value === null) return false
  const user = value as Partial<AuthUser>
  return (
    typeof user.userId === 'number' &&
    typeof user.email === 'string' &&
    typeof user.companyId === 'number' &&
    typeof user.companyName === 'string' &&
    user.role !== undefined &&
    USER_ROLES.includes(user.role)
  )
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

export const authStorage = {
  /** Reads the session saved by a previous launch; returns null if missing or corrupted. */
  async load(): Promise<StoredSession | null> {
    try {
      const [accessToken, refreshToken, rawUser] = await Promise.all([
        SecureStore.getItemAsync(KEYS.accessToken, STORE_OPTIONS),
        SecureStore.getItemAsync(KEYS.refreshToken, STORE_OPTIONS),
        SecureStore.getItemAsync(KEYS.user, STORE_OPTIONS),
      ])
      const user: unknown = rawUser ? JSON.parse(rawUser) : null
      cachedSession = accessToken && refreshToken && isAuthUser(user) ? { accessToken, refreshToken, user } : null
    } catch {
      cachedSession = null
    }
    return cachedSession
  },

  async save(auth: AuthResponse): Promise<StoredSession> {
    const session: StoredSession = {
      accessToken: auth.accessToken,
      refreshToken: auth.refreshToken,
      user: toAuthUser(auth),
    }
    await Promise.all([
      SecureStore.setItemAsync(KEYS.accessToken, session.accessToken, STORE_OPTIONS),
      SecureStore.setItemAsync(KEYS.refreshToken, session.refreshToken, STORE_OPTIONS),
      SecureStore.setItemAsync(KEYS.user, JSON.stringify(session.user), STORE_OPTIONS),
    ])
    cachedSession = session
    return session
  },

  async clear(): Promise<void> {
    cachedSession = null
    await Promise.all(Object.values(KEYS).map((key) => SecureStore.deleteItemAsync(key, STORE_OPTIONS)))
  },

  getAccessToken: (): string | null => cachedSession?.accessToken ?? null,
  getRefreshToken: (): string | null => cachedSession?.refreshToken ?? null,
}
