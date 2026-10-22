import { useCallback, useEffect, useState } from 'react'
import { ApiError } from '../utils/apiError'

const RETRY_DELAYS_MS = [1_000, 2_500]

export type Fetcher<T> = (signal: AbortSignal) => Promise<T>

export interface AsyncState<T> {
  data: T | null
  error: ApiError | null
  isLoading: boolean
  isRefreshing: boolean
  /** Pull-to-refresh: keeps the current data on screen while reloading. */
  refresh: () => void
  reload: () => void
}

interface Settled<T> {
  fetcher: Fetcher<T> | null
  runKey: number
  data: T | null
  error: ApiError | null
}

function wait(ms: number, signal: AbortSignal): Promise<void> {
  return new Promise((resolve) => {
    const timer = setTimeout(resolve, ms)
    signal.addEventListener('abort', () => clearTimeout(timer))
  })
}

async function fetchWithRetries<T>(fetcher: Fetcher<T>, signal: AbortSignal): Promise<T> {
  for (let attempt = 0; ; attempt++) {
    try {
      return await fetcher(signal)
    } catch (caught) {
      const apiError = ApiError.from(caught)
      const delay = RETRY_DELAYS_MS[attempt]
      if (signal.aborted || !apiError.isRetryable || delay === undefined) throw apiError
      await wait(delay, signal)
    }
  }
}

/**
 * Runs `fetcher` and keeps its result. Pass a memoized fetcher (useCallback): a new identity means new
 * inputs, so the request runs again and the previous one is aborted. Network and 5xx errors are retried
 * with backoff before showing the error. Loading flags are derived from which request has settled, so
 * the effect only writes state once the response arrives.
 */
export function useAsync<T>(fetcher: Fetcher<T> | null): AsyncState<T> {
  const [runKey, setRunKey] = useState(0)
  const [refreshKey, setRefreshKey] = useState<number | null>(null)
  const [settled, setSettled] = useState<Settled<T>>({ fetcher: null, runKey: -1, data: null, error: null })

  useEffect(() => {
    if (!fetcher) return
    const controller = new AbortController()
    fetchWithRetries(fetcher, controller.signal).then(
      (data) => {
        if (!controller.signal.aborted) setSettled({ fetcher, runKey, data, error: null })
      },
      (error: unknown) => {
        if (!controller.signal.aborted) {
          setSettled((previous) => ({ fetcher, runKey, data: previous.fetcher === fetcher ? previous.data : null, error: ApiError.from(error) }))
        }
      },
    )
    return () => controller.abort()
  }, [fetcher, runKey])

  const isPending = fetcher !== null && (settled.fetcher !== fetcher || settled.runKey !== runKey)
  const isRefreshing = isPending && refreshKey === runKey && settled.fetcher === fetcher
  const isLoading = isPending && !isRefreshing

  const reload = useCallback(() => setRunKey((key) => key + 1), [])
  const refresh = useCallback(() => {
    setRefreshKey(runKey + 1)
    setRunKey(runKey + 1)
  }, [runKey])

  return {
    data: fetcher && !isLoading ? settled.data : null,
    error: fetcher && !isPending ? settled.error : null,
    isLoading,
    isRefreshing,
    refresh,
    reload,
  }
}
