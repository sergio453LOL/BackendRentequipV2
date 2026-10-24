import confetti from 'canvas-confetti'

const BRAND_COLORS = ['#f59e0b', '#d97706', '#ea580c', '#0f172a', '#ffffff']

/** A short burst of confetti in the brand colours. Skipped when the user asks for reduced motion. */
export function celebrate(): void {
  if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) return
  const base = { colors: BRAND_COLORS, zIndex: 1000, disableForReducedMotion: true }
  void confetti({ ...base, particleCount: 90, spread: 75, startVelocity: 42, origin: { y: 0.65 } })
  setTimeout(() => {
    void confetti({ ...base, particleCount: 50, angle: 60, spread: 60, origin: { x: 0, y: 0.7 } })
    void confetti({ ...base, particleCount: 50, angle: 120, spread: 60, origin: { x: 1, y: 0.7 } })
  }, 180)
}
