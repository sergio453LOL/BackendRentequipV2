import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react'
import { useNavigate } from 'react-router-dom'
import { PATHS } from '../routes/paths'
import { setUnauthorizedHandler } from '../services/api'
import { authService } from '../services/authService'
import type { AuthUser, LoginRequest, RegisterRequest } from '../types'
import { toAuthUser, tokenStorage } from '../utils/tokenStorage'
import { AuthContext, type AuthContextValue, type LoginLocationState } from './AuthContextDefinition'

export function AuthProvider({ children }: { children: ReactNode }) {
  const navigate = useNavigate()
  const [user, setUser] = useState<AuthUser | null>(() => tokenStorage.getUser())

  useEffect(() => {
    setUnauthorizedHandler(() => {
      setUser(null)
      const state: LoginLocationState = { sessionExpired: true }
      navigate(PATHS.login, { replace: true, state })
    })
    return () => setUnauthorizedHandler(null)
  }, [navigate])

  const login = useCallback(async (credentials: LoginRequest) => {
    const auth = await authService.login(credentials)
    tokenStorage.setSession(auth)
    setUser(toAuthUser(auth))
  }, [])

  const register = useCallback(async (request: RegisterRequest) => {
    const auth = await authService.register(request)
    tokenStorage.setSession(auth)
    setUser(toAuthUser(auth))
  }, [])

  const logout = useCallback(() => {
    tokenStorage.clear()
    setUser(null)
    navigate(PATHS.login, { replace: true })
  }, [navigate])

  const value = useMemo<AuthContextValue>(
    () => ({ user, isAuthenticated: user !== null, login, register, logout }),
    [user, login, register, logout],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}
