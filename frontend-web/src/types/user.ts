import type { IsoDateTime } from './common'

export type UserRole = 'ADMIN' | 'MANAGER' | 'OPERATOR'

export interface User {
  id: number
  firstName: string
  lastName: string
  email: string
  phone: string | null
  role: UserRole
  enabled: boolean
  companyId: number
  companyName: string
  createdAt: IsoDateTime
}

export interface UserCreateRequest {
  firstName: string
  lastName: string
  email: string
  password: string
  phone?: string
  role: UserRole
  companyId: number
}

export type UserUpdateRequest = Partial<Pick<User, 'firstName' | 'lastName' | 'phone' | 'role'>>
