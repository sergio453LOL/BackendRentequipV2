import type { AuthResponse, LoginRequest, RegisterRequest } from '../types'
import { api } from './api'
import { ENDPOINTS } from './endpoints'

export const authService = {
  async login(credentials: LoginRequest): Promise<AuthResponse> {
    const { data } = await api.post<AuthResponse>(ENDPOINTS.auth.login, credentials)
    return data
  },

  async register(request: RegisterRequest): Promise<AuthResponse> {
    const { data } = await api.post<AuthResponse>(ENDPOINTS.auth.register, request)
    return data
  },
}
