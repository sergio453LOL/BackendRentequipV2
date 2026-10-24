import { useCallback } from 'react'
import { useSearchParams } from 'react-router-dom'

export const PAGE_SIZE_OPTIONS = [12, 24, 48, 96] as const

const PAGE_PARAM = 'page'
const SIZE_PARAM = 'size'

export interface PaginationState {
  page: number
  size: number
  setPage: (page: number) => void
  setSize: (size: number) => void
}

function parseNonNegativeInt(value: string | null): number | null {
  if (value === null) return null
  const parsed = Number(value)
  return Number.isInteger(parsed) && parsed >= 0 ? parsed : null
}

/**
 * Page and page size live in the query string (`?page=0&size=10`), so a paginated view survives a
 * reload, works with the browser's back button and can be shared as a link.
 */
export function usePagination(defaultSize: number = PAGE_SIZE_OPTIONS[0]): PaginationState {
  const [searchParams, setSearchParams] = useSearchParams()

  const page = parseNonNegativeInt(searchParams.get(PAGE_PARAM)) ?? 0
  const requestedSize = parseNonNegativeInt(searchParams.get(SIZE_PARAM))
  const size =
    requestedSize !== null && (PAGE_SIZE_OPTIONS as readonly number[]).includes(requestedSize)
      ? requestedSize
      : defaultSize

  const setPage = useCallback(
    (nextPage: number) => {
      setSearchParams((params) => {
        params.set(PAGE_PARAM, String(Math.max(0, nextPage)))
        return params
      })
    },
    [setSearchParams],
  )

  const setSize = useCallback(
    (nextSize: number) => {
      setSearchParams((params) => {
        params.set(SIZE_PARAM, String(nextSize))
        params.set(PAGE_PARAM, '0')
        return params
      })
    },
    [setSearchParams],
  )

  return { page, size, setPage, setSize }
}
