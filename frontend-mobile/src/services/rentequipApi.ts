import type {
  AuthResponse,
  Availability,
  Equipment,
  EquipmentRating,
  EquipmentSummary,
  HandoverPhoto,
  HandoverStage,
  LoginRequest,
  PageResponse,
  RegisterRequest,
  Reservation,
  ReservationCreateRequest,
  ReservationRole,
  ReservationStatus,
} from '../types/api'
import { api } from './api'

export const authService = {
  async login(request: LoginRequest): Promise<AuthResponse> {
    return (await api.post<AuthResponse>('/auth/login', request)).data
  },
  async register(request: RegisterRequest): Promise<AuthResponse> {
    return (await api.post<AuthResponse>('/auth/register', request)).data
  },
}

export interface EquipmentSearchParams {
  latitude?: number
  longitude?: number
  radiusKm?: number
  q?: string
  excludeOwnerId?: number
  page: number
  size: number
}

export const equipmentService = {
  async search(params: EquipmentSearchParams, signal?: AbortSignal): Promise<PageResponse<EquipmentSummary>> {
    return (await api.get<PageResponse<EquipmentSummary>>('/equipment/search', { params, signal })).data
  },
  async findById(id: number, signal?: AbortSignal): Promise<Equipment> {
    return (await api.get<Equipment>(`/equipment/${id}`, { signal })).data
  },
  async rating(equipmentId: number, signal?: AbortSignal): Promise<EquipmentRating> {
    return (await api.get<EquipmentRating>('/reviews/ratings', { params: { equipmentId }, signal })).data
  },
}

export const reservationService = {
  async list(
    params: { role: ReservationRole; status?: ReservationStatus; page: number; size: number },
    signal?: AbortSignal,
  ): Promise<PageResponse<Reservation>> {
    return (await api.get<PageResponse<Reservation>>('/reservations', { params, signal })).data
  },
  async findById(id: number, signal?: AbortSignal): Promise<Reservation> {
    return (await api.get<Reservation>(`/reservations/${id}`, { signal })).data
  },
  async availability(equipmentId: number, startDate: string, endDate: string, signal?: AbortSignal): Promise<Availability> {
    return (await api.get<Availability>('/reservations/availability', { params: { equipmentId, startDate, endDate }, signal }))
      .data
  },
  async create(request: ReservationCreateRequest): Promise<Reservation> {
    return (await api.post<Reservation>('/reservations', request)).data
  },
  async confirm(id: number): Promise<Reservation> {
    return (await api.post<Reservation>(`/reservations/${id}/confirmation`)).data
  },
  async cancel(id: number, reason?: string): Promise<Reservation> {
    return (await api.post<Reservation>(`/reservations/${id}/cancellation`, null, { params: { reason } })).data
  },
  async updateStatus(id: number, status: ReservationStatus, reason?: string): Promise<Reservation> {
    return (await api.patch<Reservation>(`/reservations/${id}/status`, { status, reason })).data
  },
}

export interface PhotoUpload {
  uri: string
  mimeType?: string | null
  fileName?: string | null
}

const PHOTO_UPLOAD_TIMEOUT_MS = 60_000

export const handoverService = {
  async list(reservationId: number, signal?: AbortSignal): Promise<HandoverPhoto[]> {
    return (await api.get<HandoverPhoto[]>(`/reservations/${reservationId}/handover-photos`, { signal })).data
  },

  async upload(
    reservationId: number,
    photo: PhotoUpload,
    details: { stage: HandoverStage; latitude?: number; longitude?: number; note?: string },
    onProgress?: (percent: number) => void,
  ): Promise<HandoverPhoto> {
    const form = new FormData()
    // React Native's FormData takes a { uri, name, type } descriptor and streams the file from disk.
    const file = {
      uri: photo.uri,
      name: photo.fileName ?? `handover-${Date.now()}.jpg`,
      type: photo.mimeType ?? 'image/jpeg',
    }
    form.append('file', file as unknown as Blob)
    form.append('stage', details.stage)
    if (details.latitude !== undefined && details.longitude !== undefined) {
      form.append('latitude', details.latitude.toFixed(7))
      form.append('longitude', details.longitude.toFixed(7))
    }
    if (details.note) form.append('note', details.note)

    const { data } = await api.post<HandoverPhoto>(`/reservations/${reservationId}/handover-photos`, form, {
      headers: { 'Content-Type': 'multipart/form-data' },
      timeout: PHOTO_UPLOAD_TIMEOUT_MS,
      onUploadProgress: (event) => {
        if (onProgress && event.total) onProgress(Math.round((event.loaded / event.total) * 100))
      },
    })
    return data
  },
}
