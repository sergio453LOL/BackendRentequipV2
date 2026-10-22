import type { Equipment, EquipmentCreateRequest, UploadedImage, WithLinks } from '../types'
import { api } from './api'
import { ENDPOINTS } from './endpoints'

const UPLOAD_TIMEOUT_MS = 60_000

export const equipmentService = {
  async create(request: EquipmentCreateRequest): Promise<WithLinks<Equipment>> {
    const { data } = await api.post<WithLinks<Equipment>>(ENDPOINTS.equipmentCollection, request)
    return data
  },

  /** Uploads one photo and reports progress from 0 to 100. Resolves with the public URL of the image. */
  async uploadImage(file: File, onProgress?: (percent: number) => void, signal?: AbortSignal): Promise<string> {
    const form = new FormData()
    form.append('file', file)
    const { data } = await api.post<UploadedImage>(ENDPOINTS.imageUploads, form, {
      headers: { 'Content-Type': 'multipart/form-data' },
      timeout: UPLOAD_TIMEOUT_MS,
      signal,
      onUploadProgress: (event) => {
        if (onProgress && event.total) onProgress(Math.round((event.loaded / event.total) * 100))
      },
    })
    return data.url
  },
}
