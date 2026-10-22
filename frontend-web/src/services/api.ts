import axios, { AxiosError, type InternalAxiosRequestConfig } from 'axios'
import type { AuthResponse, ErrorResponse, RefreshTokenRequest } from '../types'
import { ApiError } from '../utils/apiError'
import { tokenStorage } from '../utils/tokenStorage'

export const API_BASE_URL = import.meta.env.VITE_API_URL ?? 'http://localhost:8080/api/v1'

// A page served over HTTPS (Vercel) cannot call an http:// API: the browser blocks it as mixed content.
if (window.location.protocol === 'https:' && API_BASE_URL.startsWith('http://')) {
  console.error(`VITE_API_URL (${API_BASE_URL}) usa http:// y el navegador bloqueará las peticiones. Usa la URL https:// del backend.`)
}

const REQUEST_TIMEOUT_MS = 15_000
const AUTH_PATH_PREFIX = '/auth/'

export const api = axios.create({
  baseURL: API_BASE_URL,
  timeout: REQUEST_TIMEOUT_MS,
  headers: {
    'Content-Type': 'application/json',
    Accept: 'application/json',
  },
})

type UnauthorizedHandler = () => void

let unauthorizedHandler: UnauthorizedHandler | null = null

/** Lets the AuthProvider decide what happens when the session can no longer be recovered. */
export function setUnauthorizedHandler(handler: UnauthorizedHandler | null): void {
  unauthorizedHandler = handler
}

api.interceptors.request.use((config) => {
  const token = tokenStorage.getAccessToken()
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

let pendingRefresh: Promise<string> | null = null

async function refreshAccessToken(): Promise<string> {
  const refreshToken = tokenStorage.getRefreshToken()
  if (!refreshToken) {
    throw new Error('No refresh token available')
  }
  const body: RefreshTokenRequest = { refreshToken }
  const { data } = await axios.post<AuthResponse>(`${API_BASE_URL}/auth/refresh`, body, {
    timeout: REQUEST_TIMEOUT_MS,
  })
  tokenStorage.setSession(data)
  return data.accessToken
}

/** Concurrent 401s share one refresh call instead of each burning the refresh token. */
function refreshOnce(): Promise<string> {
  pendingRefresh ??= refreshAccessToken().finally(() => {
    pendingRefresh = null
  })
  return pendingRefresh
}

interface RetriableRequestConfig extends InternalAxiosRequestConfig {
  _retried?: boolean
}

api.interceptors.response.use(
  (response) => response,
  async (error: unknown) => {
    if (axios.isCancel(error) || !(error instanceof AxiosError)) {
      return Promise.reject(error)
    }

    const axiosError = error as AxiosError<ErrorResponse>
    const originalRequest = axiosError.config as RetriableRequestConfig | undefined
    const isAuthRequest = originalRequest?.url?.startsWith(AUTH_PATH_PREFIX) ?? false

    if (axiosError.response?.status === 401 && originalRequest && !isAuthRequest) {
      if (!originalRequest._retried) {
        originalRequest._retried = true
        try {
          const newToken = await refreshOnce()
          originalRequest.headers.Authorization = `Bearer ${newToken}`
          return api(originalRequest)
        } catch {
          // The refresh token is expired or revoked too: fall through and end the session.
        }
      }
      tokenStorage.clear()
      unauthorizedHandler?.()
    }

    return Promise.reject(ApiError.fromAxios(axiosError))
  },
)
