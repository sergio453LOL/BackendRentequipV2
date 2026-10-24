import { animate, useInView, useReducedMotion } from 'framer-motion'
import { useEffect, useRef } from 'react'

interface AnimatedNumberProps {
  value: number
  format: (value: number) => string
  duration?: number
}

/** Counts up from zero to the real value the first time it scrolls into view. */
export function AnimatedNumber({ value, format, duration = 1.4 }: AnimatedNumberProps) {
  const ref = useRef<HTMLSpanElement>(null)
  const isInView = useInView(ref, { once: true, margin: '-40px' })
  const reduceMotion = useReducedMotion()

  useEffect(() => {
    const node = ref.current
    if (!node || !isInView) return
    if (reduceMotion) {
      node.textContent = format(value)
      return
    }
    const controls = animate(0, value, {
      duration,
      ease: [0.16, 1, 0.3, 1],
      onUpdate: (latest) => {
        node.textContent = format(latest)
      },
    })
    return () => controls.stop()
  }, [duration, format, isInView, reduceMotion, value])

  return (
    <span ref={ref} className="num">
      {format(0)}
    </span>
  )
}
