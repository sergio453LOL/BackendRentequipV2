import { useCallback } from 'react'
import { useSearchParams } from 'react-router-dom'

/**
 * A filter stored in the query string. Changing it goes back to the first page, because the old page
 * number rarely exists in the new result set. An empty value removes the parameter.
 */
export function useQueryParam(key: string, defaultValue = ''): [string, (value: string) => void] {
  const [searchParams, setSearchParams] = useSearchParams()
  const value = searchParams.get(key) ?? defaultValue

  const setValue = useCallback(
    (nextValue: string) => {
      setSearchParams(
        (params) => {
          if (nextValue) params.set(key, nextValue)
          else params.delete(key)
          params.delete('page')
          return params
        },
        { replace: true },
      )
    },
    [key, setSearchParams],
  )

  return [value, setValue]
}
