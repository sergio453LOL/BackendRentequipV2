/** Contracts of the RentEquip REST API, mirroring the backend DTOs (`dtos/request`, `dtos/response`). */

/** `LocalDate`, serialized as `YYYY-MM-DD`. */
export type IsoDate = string
/** `LocalDateTime`, serialized as `YYYY-MM-DDTHH:mm:ss`. */
export type IsoDateTime = string

export interface PageResponse<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
  last: boolean
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

export type UserRole = 'ADMIN' | 'MANAGER' | 'OPERATOR'

export interface LoginRequest {
  email: string
  password: string
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

export interface RegisterRequest {
  company: CompanyCreateRequest
  firstName: string
  lastName: string
  email: string
  password: string
  phone?: string
}

export interface AuthResponse {
  accessToken: string
  refreshToken: string
  tokenType: string
  expiresInSeconds: number
  userId: number
  email: string
  role: UserRole
  companyId: number
  companyName: string
}

export type AuthUser = Pick<AuthResponse, 'userId' | 'email' | 'role' | 'companyId' | 'companyName'>

export type EquipmentStatus = 'AVAILABLE' | 'RENTED' | 'MAINTENANCE' | 'INACTIVE'

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

export interface Equipment extends Omit<EquipmentSummary, 'primaryImageUrl'> {
  description: string | null
  manufactureYear: number | null
  serialNumber: string | null
  specifications: string | null
  weeklyRate: number | null
  securityDeposit: number
  address: string | null
  imageUrls: string[]
  categories: string[]
  createdAt: IsoDateTime
}

export interface EquipmentRating {
  equipmentId: number
  averageRating: number
}

export type ReservationStatus = 'PENDING' | 'CONFIRMED' | 'IN_PROGRESS' | 'COMPLETED' | 'CANCELLED' | 'REJECTED'
export type ReservationRole = 'RENTER' | 'OWNER'
export type ReservationPaymentStatus = 'UNPAID' | 'PAID' | 'REFUNDED'

export interface Reservation {
  id: number
  equipmentId: number
  equipmentName: string
  renterCompanyId: number
  renterCompanyName: string
  startDate: IsoDate
  endDate: IsoDate
  totalDays: number
  dailyRate: number
  rentalAmount: number
  securityDeposit: number
  totalAmount: number
  currency: string
  status: ReservationStatus
  notes: string | null
  paymentStatus: ReservationPaymentStatus
  createdAt: IsoDateTime
}

export interface ReservationCreateRequest {
  equipmentId: number
  startDate: IsoDate
  endDate: IsoDate
  notes?: string
}

export interface Availability {
  equipmentId: number
  startDate: IsoDate
  endDate: IsoDate
  available: boolean
}

export type HandoverStage = 'DELIVERY' | 'RETURN'

export interface HandoverPhoto {
  id: number
  reservationId: number
  stage: HandoverStage
  imageUrl: string
  latitude: number | null
  longitude: number | null
  note: string | null
  uploadedByCompanyId: number
  uploadedByCompanyName: string
  createdAt: IsoDateTime
}
