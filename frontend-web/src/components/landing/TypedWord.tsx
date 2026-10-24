import { AnimatePresence, motion, useReducedMotion } from 'framer-motion'
import { useEffect, useState } from 'react'

const HOLD_MS = 2600

/** Cycles through words, typing each one in letter by letter. */
export function TypedWord({ words, className }: { words: string[]; className?: string }) {
  const [index, setIndex] = useState(0)
  const reduceMotion = useReducedMotion()

  useEffect(() => {
    if (reduceMotion || words.length < 2) return
    const timer = setInterval(() => setIndex((current) => (current + 1) % words.length), HOLD_MS)
    return () => clearInterval(timer)
  }, [reduceMotion, words.length])

  const word = words[index] ?? ''
  return (
    // The widest word reserves the space, so the line never jumps while letters come and go.
    <span className={`relative inline-grid ${className ?? ''}`}>
      <span className="invisible col-start-1 row-start-1" aria-hidden="true">
        {words.reduce((longest, item) => (item.length > longest.length ? item : longest), '')}
      </span>
      <AnimatePresence mode="wait">
        <motion.span key={word} className="col-start-1 row-start-1" aria-live="polite" exit={{ opacity: 0, y: -8, transition: { duration: 0.2 } }}>
          {word.split('').map((letter, letterIndex) => (
            <motion.span
              key={letterIndex}
              initial={{ opacity: 0, y: 10 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ delay: letterIndex * 0.035, duration: 0.25, ease: 'easeOut' }}
              className="inline-block"
            >
              {letter === ' ' ? ' ' : letter}
            </motion.span>
          ))}
        </motion.span>
      </AnimatePresence>
    </span>
  )
}
