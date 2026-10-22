import type { IsoDateTime } from './common'

export interface Review {
  id: number
  reservationId: number
  equipmentId: number
  reviewerCompanyId: number
  rating: number
  conditionRating: number | null
  comment: string | null
  createdAt: IsoDateTime
}

export interface ReviewCreateRequest {
  reservationId: number
  rating: number
  conditionRating?: number
  comment?: string
}
