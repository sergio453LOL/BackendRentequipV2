import { createContext, useCallback, useEffect, useMemo, useState, type ReactNode } from 'react'
import { useToast } from '../hooks/useToast'
import { setSessionExpiredHandler } from '../services/api'
import { authStorage } from '../services/authStorage'
import { authService } from '../services/rentequipApi'
import type { AuthUser, LoginRequest, RegisterRequest } from '../types/api'

export type AuthStatus = 'restoring' | 'signedOut' | 'signedIn'

export interface AuthContextValue {
  status: AuthStatus
  user: AuthUser | null
  login: (credentials: LoginRequest) => Promise<void>
  register: (request: RegisterRequest) => Promise<void>
  logout: () => Promise<void>
}

export const AuthContext = createContext<AuthContextValue | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  const toast = useToast()
  const [user, setUser] = useState<AuthUser | null>(null)
  const [status, setStatus] = useState<AuthStatus>('restoring')
  useEffect(() => {
    let isMounted = true
    authStorage.load().then((session) => {
      if (!isMounted) return
      setUser(session?.user ?? null)
      setStatus(session ? 'signedIn' : 'signedOut')
    })
    return () => {
      isMounted = false
    }
  }, [])

  useEffect(() => {
    setSessionExpiredHandler(() => {
      setUser(null)
      setStatus('signedOut')
      toast.info('Tu sesión expiró', 'Vuelve a iniciar sesión para continuar.')
    })
    return () => setSessionExpiredHandler(null)
  }, [toast])

  const login = useCallback(async (credentials: LoginRequest) => {
    const session = await authStorage.save(await authService.login(credentials))
    setUser(session.user)
    setStatus('signedIn')
  }, [])

  const register = useCallback(async (request: RegisterRequest) => {
    const session = await authStorage.save(await authService.register(request))
    setUser(session.user)
    setStatus('signedIn')
  }, [])

  const logout = useCallback(async () => {
    await authStorage.clear()
    setUser(null)
    setStatus('signedOut')
  }, [])

  const value = useMemo<AuthContextValue>(
    () => ({ status, user, login, register, logout }),
    [status, user, login, register, logout],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}
