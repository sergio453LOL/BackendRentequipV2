import { useCallback, useEffect, useReducer, useState } from 'react'
import { api } from '../services/api'
import { ApiError } from '../utils/apiError'

export type QueryValue = string | number | boolean | null | undefined

export type QueryParams = Record<string, QueryValue>

export interface FetchState<T> {
  data: T | null
  error: ApiError | null
  isLoading: boolean
}

export interface UseFetchResult<T> extends FetchState<T> {
  refetch: () => void
}

type FetchAction<T> =
  | { type: 'start' }
  | { type: 'success'; data: T }
  | { type: 'failure'; error: ApiError }

function fetchReducer<T>(state: FetchState<T>, action: FetchAction<T>): FetchState<T> {
  switch (action.type) {
    case 'start':
      return { ...state, isLoading: true, error: null }
    case 'success':
      return { data: action.data, error: null, isLoading: false }
    case 'failure':
      return { ...state, error: action.error, isLoading: false }
  }
}

const RETRY_DELAYS_MS = [1_000, 2_000]

function wait(ms: number, signal: AbortSignal): Promise<void> {
  return new Promise((resolve) => {
    const timer = setTimeout(resolve, ms)
    signal.addEventListener('abort', () => clearTimeout(timer), { once: true })
  })
}

/**
 * GETs `url` from the API and re-fetches whenever the url or params change.
 * Pass `null` as url to skip the request. In-flight requests are aborted on change and on unmount,
 * and network/5xx failures are retried with backoff before surfacing the error.
 */
export function useFetch<T>(url: string | null, params?: QueryParams): UseFetchResult<T> {
  const [state, dispatch] = useReducer(fetchReducer<T>, { data: null, error: null, isLoading: url !== null })
  const [reloadCount, setReloadCount] = useState(0)
  const paramsKey = JSON.stringify(params ?? {})

  useEffect(() => {
    if (url === null) return

    const controller = new AbortController()
    const requestParams = JSON.parse(paramsKey) as QueryParams

    async function load(requestUrl: string): Promise<void> {
      dispatch({ type: 'start' })
      for (let attempt = 0; ; attempt++) {
        try {
          const { data } = await api.get<T>(requestUrl, { params: requestParams, signal: controller.signal })
          dispatch({ type: 'success', data })
          return
        } catch (error) {
          if (controller.signal.aborted) return
          const apiError = ApiError.from(error)
          const delay = RETRY_DELAYS_MS[attempt]
          if (!apiError.isRetryable || delay === undefined) {
            dispatch({ type: 'failure', error: apiError })
            return
          }
          await wait(delay, controller.signal)
          if (controller.signal.aborted) return
        }
      }
    }

    void load(url)
    return () => controller.abort()
  }, [url, paramsKey, reloadCount])

  const refetch = useCallback(() => setReloadCount((count) => count + 1), [])

  return { ...state, refetch }
}
