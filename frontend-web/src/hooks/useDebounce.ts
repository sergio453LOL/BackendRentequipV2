import { useEffect, useState } from 'react'

/** Returns `value` only after it stopped changing for `delayMs`, so typing does not fire a request per key. */
export function useDebounce<T>(value: T, delayMs = 400): T {
  const [debouncedValue, setDebouncedValue] = useState(value)

  useEffect(() => {
    const timer = setTimeout(() => setDebouncedValue(value), delayMs)
    return () => clearTimeout(timer)
  }, [value, delayMs])

  return debouncedValue
}
