import { createContext } from 'react'
import type { AuthUser, LoginRequest, RegisterRequest } from '../types'

export interface AuthContextValue {
  user: AuthUser | null
  isAuthenticated: boolean
  login: (credentials: LoginRequest) => Promise<void>
  register: (request: RegisterRequest) => Promise<void>
  logout: () => void
}

/** Router state handed to the login page by redirects. */
export interface LoginLocationState {
  from?: string
  sessionExpired?: boolean
}

export const AuthContext = createContext<AuthContextValue | null>(null)
