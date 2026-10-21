import type { IsoDate, IsoDateTime, PageParams } from './common'

export type EquipmentStatus = 'AVAILABLE' | 'RENTED' | 'MAINTENANCE' | 'INACTIVE'

export interface Equipment {
  id: number
  name: string
  description: string | null
  brand: string
  model: string
  manufactureYear: number | null
  serialNumber: string | null
  specifications: string | null
  dailyRate: number
  weeklyRate: number | null
  securityDeposit: number
  currency: string
  status: EquipmentStatus
  address: string | null
  city: string
  latitude: number
  longitude: number
  imageUrls: string[]
  categories: string[]
  ownerCompanyId: number
  ownerCompanyName: string
  createdAt: IsoDateTime
}

/** Lightweight projection used by search results and map pins. */
export interface EquipmentSummary {
  id: number
  name: string
  brand: string
  model: string
  dailyRate: number
  currency: string
  status: EquipmentStatus
  city: string
  latitude: number
  longitude: number
  primaryImageUrl: string | null
  ownerCompanyId: number
  ownerCompanyName: string
}

export interface EquipmentCreateRequest {
  name: string
  description?: string
  brand: string
  model: string
  manufactureYear?: number
  serialNumber?: string
  specifications?: string
  dailyRate: number
  weeklyRate?: number
  securityDeposit: number
  currency?: string
  address?: string
  city: string
  latitude: number
  longitude: number
  imageUrls?: string[]
  categoryIds: number[]
  ownerCompanyId: number
}

export type EquipmentUpdateRequest = Partial<
  Omit<EquipmentCreateRequest, 'serialNumber' | 'ownerCompanyId'> & { status: EquipmentStatus }
>

export interface EquipmentSearchParams extends PageParams {
  latitude?: number
  longitude?: number
  radiusKm?: number
  categoryId?: number
  city?: string
  maxDailyRate?: number
  startDate?: IsoDate
  endDate?: IsoDate
  excludeOwnerId?: number
}

export interface EquipmentCategory {
  id: number
  name: string
  description: string | null
}

export interface EquipmentCategoryRequest {
  name: string
  description?: string
}

export interface EquipmentRating {
  equipmentId: number
  averageRating: number
}
