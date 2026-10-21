import type { IsoDateTime } from './common'

export interface Company {
  id: number
  name: string
  taxId: string
  email: string
  phone: string | null
  address: string | null
  city: string
  latitude: number
  longitude: number
  active: boolean
  createdAt: IsoDateTime
}

export interface CompanyCreateRequest {
  name: string
  taxId: string
  email: string
  phone?: string
  address?: string
  city: string
  latitude: number
  longitude: number
}

export type CompanyUpdateRequest = Partial<Omit<CompanyCreateRequest, 'taxId'>>

export interface CompanyDashboard {
  companyId: number
  publishedEquipment: number
  activeReservationsAsOwner: number
  pendingRequestsAsOwner: number
  activeReservationsAsRenter: number
  estimatedRevenue: number
  currency: string
}
