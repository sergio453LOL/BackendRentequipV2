import { AxiosError } from 'axios'
import type { ErrorResponse, FieldValidationError } from '../types'

const STATUS_MESSAGES: Record<number, string> = {
  400: 'Revisa los datos ingresados e inténtalo nuevamente.',
  401: 'Tu sesión no es válida. Inicia sesión nuevamente.',
  403: 'No tienes permisos para realizar esta acción.',
  404: 'No encontramos el recurso solicitado.',
  409: 'La operación entra en conflicto con el estado actual de los datos.',
  422: 'La operación no cumple las reglas del negocio.',
  429: 'Demasiadas solicitudes. Espera un momento e inténtalo otra vez.',
}

/** Business errors whose code says more than the HTTP status alone. */
const CODE_MESSAGES: Record<string, string> = {
  DUPLICATE_RESOURCE: 'Ya existe un registro con esos datos (RUC o correo).',
  EQUIPMENT_NOT_AVAILABLE: 'La maquinaria ya está reservada en esas fechas. Prueba con otro rango.',
  EQUIPMENT_UNAVAILABLE: 'Esta maquinaria no está disponible para alquiler en este momento.',
  INVALID_RESERVATION_DATES: 'Las fechas de la reserva no son válidas.',
  INVALID_STATUS_TRANSITION: 'La reserva ya cambió de estado. Actualiza la lista e inténtalo otra vez.',
  INVALID_OPERATION: 'No se puede realizar esta operación.',
  FORBIDDEN_OPERATION: 'No tienes permisos para realizar esta acción.',
  RESOURCE_NOT_FOUND: 'No encontramos el recurso solicitado.',
  FILE_TOO_LARGE: 'La imagen supera el máximo de 5 MB.',
}

/** Codes whose backend message is already written for the end user (e.g. the bank's decline reason). */
const USER_FACING_CODES = new Set(['PAYMENT_DECLINED'])

const SERVER_ERROR_MESSAGE = 'Ocurrió un error en el servidor. Inténtalo más tarde.'
const NETWORK_ERROR_MESSAGE = 'No pudimos conectarnos con el servidor. Revisa tu conexión.'
const TIMEOUT_MESSAGE = 'El servidor tardó demasiado en responder. Inténtalo nuevamente.'
const UNKNOWN_ERROR_MESSAGE = 'Ocurrió un error inesperado.'

/** Normalized error thrown by the API layer. `message` is always safe to show to the user. */
export class ApiError extends Error {
  readonly status: number
  readonly code: string | undefined
  readonly serverMessage: string | undefined
  readonly details: FieldValidationError[]

  constructor(
    message: string,
    options: { status: number; code?: string; serverMessage?: string; details?: FieldValidationError[] },
  ) {
    super(message)
    this.name = 'ApiError'
    this.status = options.status
    this.code = options.code
    this.serverMessage = options.serverMessage
    this.details = options.details ?? []
  }

  /** Status 0 means the request never got an HTTP response (offline, DNS, CORS, timeout). */
  get isNetworkError(): boolean {
    return this.status === 0
  }

  /** Network failures and 5xx/429 responses are worth retrying; client errors are not. */
  get isRetryable(): boolean {
    return this.status === 0 || this.status === 429 || this.status >= 500
  }

  /** Validation errors indexed by field name, ready to feed a form. */
  get fieldErrors(): Record<string, string> {
    return Object.fromEntries(this.details.map((detail) => [detail.field, detail.message]))
  }

  static fromAxios(error: AxiosError<ErrorResponse>): ApiError {
    const response = error.response
    if (!response) {
      const isTimeout = error.code === AxiosError.ECONNABORTED || error.code === AxiosError.ETIMEDOUT
      return new ApiError(isTimeout ? TIMEOUT_MESSAGE : NETWORK_ERROR_MESSAGE, { status: 0, code: error.code })
    }

    const { status, data } = response
    const codeMessage =
      data?.code && USER_FACING_CODES.has(data.code) && data.message
        ? data.message
        : data?.code
          ? CODE_MESSAGES[data.code]
          : undefined
    const message =
      status >= 500 ? SERVER_ERROR_MESSAGE : (codeMessage ?? STATUS_MESSAGES[status] ?? UNKNOWN_ERROR_MESSAGE)
    return new ApiError(message, {
      status,
      code: data?.code,
      serverMessage: data?.message,
      details: data?.details,
    })
  }

  static from(error: unknown): ApiError {
    if (error instanceof ApiError) return error
    if (error instanceof AxiosError) return ApiError.fromAxios(error)
    return new ApiError(UNKNOWN_ERROR_MESSAGE, { status: -1 })
  }
}

export function getErrorMessage(error: unknown): string {
  return ApiError.from(error).message
}
