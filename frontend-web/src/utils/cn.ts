import { clsx, type ClassValue } from 'clsx'
import { twMerge } from 'tailwind-merge'

/** Joins class names (skipping falsy values) and lets a later Tailwind class override a conflicting one. */
export function cn(...classes: ClassValue[]): string {
  return twMerge(clsx(classes))
}
