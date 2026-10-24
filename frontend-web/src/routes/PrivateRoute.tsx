import { Navigate, Outlet, useLocation } from 'react-router-dom'
import type { LoginLocationState } from '../contexts/AuthContextDefinition'
import { useAuth } from '../hooks/useAuth'
import type { UserRole } from '../types'
import { MODE_HOME, readStoredMode } from '../utils/workspaceMode'
import { PATHS } from './paths'

interface PrivateRouteProps {
  allowedRoles?: UserRole[]
}

export function PrivateRoute({ allowedRoles }: PrivateRouteProps) {
  const { isAuthenticated, user } = useAuth()
  const location = useLocation()

  if (!isAuthenticated) {
    const state: LoginLocationState = { from: location.pathname + location.search }
    return <Navigate to={PATHS.login} replace state={state} />
  }

  if (allowedRoles && user && !allowedRoles.includes(user.role)) {
    return <Navigate to={PATHS.home} replace />
  }

  return <Outlet />
}

/**
 * Keeps logged-in users away from guest-only pages such as the login form. Right after logging in it
 * also sends the user back to the page that required authentication.
 */
export function GuestRoute() {
  const { isAuthenticated } = useAuth()
  const location = useLocation()
  const from = (location.state as LoginLocationState | null)?.from
  return isAuthenticated ? <Navigate to={from ?? MODE_HOME[readStoredMode()]} replace /> : <Outlet />
}
