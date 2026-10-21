import { AxiosError } from 'axios'
import type { ErrorResponse, FieldValidationError } from '../types/api'

const STATUS_MESSAGES: Record<number, string> = {
  400: 'Revisa los datos ingresados e inténtalo nuevamente.',
  401: 'Tu sesión no es válida. Inicia sesión nuevamente.',
  403: 'No tienes permisos para realizar esta acción.',
  404: 'No encontramos lo que buscabas.',
  409: 'La operación entra en conflicto con el estado actual. Actualiza e inténtalo otra vez.',
  413: 'El archivo es demasiado grande (máximo 5 MB).',
  429: 'Demasiadas solicitudes. Espera un momento.',
}

const CODE_MESSAGES: Record<string, string> = {
  DUPLICATE_RESOURCE: 'Ya existe una empresa o usuario con esos datos (RUC o correo).',
  EQUIPMENT_NOT_AVAILABLE: 'La máquina ya está reservada en esas fechas. Prueba otro rango.',
  EQUIPMENT_UNAVAILABLE: 'Esta máquina no está disponible para alquiler en este momento.',
  INVALID_RESERVATION_DATES: 'Las fechas de la reserva no son válidas.',
  INVALID_STATUS_TRANSITION: 'La reserva ya cambió de estado. Actualiza la lista.',
  FORBIDDEN_OPERATION: 'No tienes permisos para realizar esta acción.',
  FILE_TOO_LARGE: 'La foto supera el máximo de 5 MB.',
}

/** Backend messages that are already written for the end user. */
const USER_FACING_CODES = new Set(['PAYMENT_DECLINED'])

const NETWORK_MESSAGE = 'No pudimos conectarnos con el servidor. Revisa tu conexión a internet.'
const TIMEOUT_MESSAGE = 'El servidor tardó demasiado en responder. Inténtalo nuevamente.'
const SERVER_MESSAGE = 'Tuvimos un problema en el servidor. Inténtalo en unos minutos.'
const UNKNOWN_MESSAGE = 'Ocurrió un error inesperado.'

/** Normalized API error; `message` is always friendly and safe to show to the user. */
export class ApiError extends Error {
  readonly status: number
  readonly code: string | undefined
  readonly details: FieldValidationError[]

  constructor(message: string, status: number, code?: string, details: FieldValidationError[] = []) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.code = code
    this.details = details
  }

  /** No HTTP response at all: offline, wrong server URL, DNS, timeout. */
  get isNetworkError(): boolean {
    return this.status === 0
  }

  get isRetryable(): boolean {
    return this.status === 0 || this.status === 429 || this.status >= 500
  }

  static fromAxios(error: AxiosError<ErrorResponse>): ApiError {
    const response = error.response
    if (!response) {
      const isTimeout = error.code === AxiosError.ECONNABORTED || error.code === AxiosError.ETIMEDOUT
      return new ApiError(isTimeout ? TIMEOUT_MESSAGE : NETWORK_MESSAGE, 0, error.code)
    }
    const { status, data } = response
    const code = data?.code
    let message: string
    if (status >= 500) message = SERVER_MESSAGE
    else if (code && USER_FACING_CODES.has(code) && data?.message) message = data.message
    else message = (code && CODE_MESSAGES[code]) || STATUS_MESSAGES[status] || UNKNOWN_MESSAGE
    return new ApiError(message, status, code, data?.details ?? [])
  }

  static from(error: unknown): ApiError {
    if (error instanceof ApiError) return error
    if (error instanceof AxiosError) return ApiError.fromAxios(error)
    return new ApiError(UNKNOWN_MESSAGE, -1)
  }
}

export function getErrorMessage(error: unknown): string {
  return ApiError.from(error).message
}
