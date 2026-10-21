import axios, { AxiosError, create, isCancel, type InternalAxiosRequestConfig } from 'axios'
import { API_BASE_URL } from '../config/env'
import type { AuthResponse, ErrorResponse } from '../types/api'
import { ApiError } from '../utils/apiError'
import { authStorage } from './authStorage'

const REQUEST_TIMEOUT_MS = 15_000
const AUTH_PATH_PREFIX = '/auth/'

/** Single HTTP client for the RentEquip backend: base URL, JSON headers, auth and error handling. */
export const api = create({
  baseURL: API_BASE_URL,
  timeout: REQUEST_TIMEOUT_MS,
  headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
})

type SessionHandler = () => void
let sessionExpiredHandler: SessionHandler | null = null

/** The AuthProvider registers what to do when the session cannot be recovered (back to login). */
export function setSessionExpiredHandler(handler: SessionHandler | null): void {
  sessionExpiredHandler = handler
}

api.interceptors.request.use((config) => {
  const token = authStorage.getAccessToken()
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

let pendingRefresh: Promise<string> | null = null

async function refreshAccessToken(): Promise<string> {
  const refreshToken = authStorage.getRefreshToken()
  if (!refreshToken) throw new Error('No refresh token')
  const { data } = await axios.post<AuthResponse>(
    `${API_BASE_URL}/auth/refresh`,
    { refreshToken },
    { timeout: REQUEST_TIMEOUT_MS },
  )
  await authStorage.save(data)
  return data.accessToken
}

/** Requests that fail with 401 at the same time share one refresh instead of racing for it. */
function refreshOnce(): Promise<string> {
  pendingRefresh ??= refreshAccessToken().finally(() => {
    pendingRefresh = null
  })
  return pendingRefresh
}

interface RetriableConfig extends InternalAxiosRequestConfig {
  _retried?: boolean
}

api.interceptors.response.use(
  (response) => response,
  async (error: unknown) => {
    if (isCancel(error) || !(error instanceof AxiosError)) return Promise.reject(error)

    const axiosError = error as AxiosError<ErrorResponse>
    const original = axiosError.config as RetriableConfig | undefined
    const isAuthCall = original?.url?.startsWith(AUTH_PATH_PREFIX) ?? false

    if (axiosError.response?.status === 401 && original && !isAuthCall) {
      if (!original._retried) {
        original._retried = true
        try {
          const token = await refreshOnce()
          original.headers.Authorization = `Bearer ${token}`
          return api(original)
        } catch {
          // The refresh token is expired too: the session ends below.
        }
      }
      await authStorage.clear()
      sessionExpiredHandler?.()
    }
    return Promise.reject(ApiError.fromAxios(axiosError))
  },
)
