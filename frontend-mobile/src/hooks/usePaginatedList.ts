import { useCallback, useEffect, useRef, useState } from 'react'
import type { PageResponse } from '../types/api'
import { ApiError } from '../utils/apiError'

export type PageFetcher<T> = (page: number, signal: AbortSignal) => Promise<PageResponse<T>>

export interface PaginatedList<T> {
  items: T[]
  totalElements: number
  error: ApiError | null
  /** First page loading with nothing to show yet. */
  isLoading: boolean
  isLoadingMore: boolean
  isRefreshing: boolean
  hasMore: boolean
  loadMore: () => void
  refresh: () => void
  retry: () => void
}

interface ListState<T> {
  /** Which query these items belong to: a different fetcher or generation means a new query. */
  fetchPage: PageFetcher<T> | null
  generation: number
  items: T[]
  page: number
  totalElements: number
  hasMore: boolean
  error: ApiError | null
}

/**
 * Infinite scroll over a paginated endpoint (`content`, `page`, `last`). A new `fetchPage` identity
 * (new filters) restarts from page 0 and aborts whatever was still loading.
 */
export function usePaginatedList<T>(fetchPage: PageFetcher<T>): PaginatedList<T> {
  const [generation, setGeneration] = useState(0)
  const [state, setState] = useState<ListState<T>>({
    fetchPage: null,
    generation: -1,
    items: [],
    page: 0,
    totalElements: 0,
    hasMore: true,
    error: null,
  })
  const [extraLoad, setExtraLoad] = useState<'more' | 'refresh' | null>(null)
  const controllerRef = useRef<AbortController | null>(null)

  const fetchInto = useCallback(
    (pageToLoad: number, onDone?: () => void) => {
      controllerRef.current?.abort()
      const controller = new AbortController()
      controllerRef.current = controller
      fetchPage(pageToLoad, controller.signal).then(
        (result) => {
          if (controller.signal.aborted) return
          setState((previous) => ({
            fetchPage,
            generation,
            items: pageToLoad === 0 ? result.content : [...previous.items, ...result.content],
            page: pageToLoad,
            totalElements: result.totalElements,
            hasMore: !result.last,
            error: null,
          }))
          onDone?.()
        },
        (error: unknown) => {
          if (controller.signal.aborted) return
          setState((previous) => ({
            ...previous,
            fetchPage,
            generation,
            items: previous.fetchPage === fetchPage && previous.generation === generation ? previous.items : [],
            error: ApiError.from(error),
          }))
          onDone?.()
        },
      )
    },
    [fetchPage, generation],
  )

  useEffect(() => {
    fetchInto(0)
    return () => controllerRef.current?.abort()
  }, [fetchInto])

  const isCurrent = state.fetchPage === fetchPage && state.generation === generation
  const isLoading = !isCurrent
  const items = isCurrent ? state.items : []
  const error = isCurrent ? state.error : null

  const loadMore = useCallback(() => {
    if (!isCurrent || !state.hasMore || extraLoad !== null || error) return
    setExtraLoad('more')
    fetchInto(state.page + 1, () => setExtraLoad(null))
  }, [isCurrent, state.hasMore, state.page, extraLoad, error, fetchInto])

  const refresh = useCallback(() => {
    setExtraLoad('refresh')
    fetchInto(0, () => setExtraLoad(null))
  }, [fetchInto])

  const retry = useCallback(() => {
    if (items.length === 0) {
      setGeneration((value) => value + 1)
      return
    }
    setExtraLoad('more')
    fetchInto(state.page + 1, () => setExtraLoad(null))
  }, [items.length, state.page, fetchInto])

  return {
    items,
    totalElements: isCurrent ? state.totalElements : 0,
    error,
    isLoading,
    isLoadingMore: extraLoad === 'more',
    isRefreshing: extraLoad === 'refresh',
    hasMore: isCurrent && state.hasMore,
    loadMore,
    refresh,
    retry,
  }
}
