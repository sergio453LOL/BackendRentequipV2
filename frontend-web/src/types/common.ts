/** `LocalDate` in the backend, serialized as `YYYY-MM-DD`. */
export type IsoDate = string

/** `LocalDateTime` in the backend, serialized as `YYYY-MM-DDTHH:mm:ss`. */
export type IsoDateTime = string

export interface PageResponse<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
  last: boolean
}

export interface PageParams {
  page?: number
  size?: number
}

export interface FieldValidationError {
  field: string
  rejectedValue: unknown
  message: string
}

export interface ErrorResponse {
  timestamp: IsoDateTime
  status: number
  error: string
  code?: string
  message?: string
  path?: string
  details?: FieldValidationError[]
}

export interface HalLink {
  href: string
}

/** Endpoints that return `EntityModel<T>` add HATEOAS links next to the DTO fields. */
export type WithLinks<T> = T & {
  _links?: Record<string, HalLink>
}

export interface UploadedImage {
  url: string
}
