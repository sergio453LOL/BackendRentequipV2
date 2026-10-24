import type { ReactNode } from 'react'
import { BrandWordmark } from '../common/BrandMark'

interface AuthLayoutProps {
  title: string
  description: string
  /** Width of the form column: the login is a narrow form, the registration has two columns of fields. */
  width?: 'narrow' | 'wide'
  children: ReactNode
}

/** Login and registration: a machine at work on the left half, the form on white on the right. */
export function AuthLayout({ title, description, width = 'narrow', children }: AuthLayoutProps) {
  return (
    <main className="min-h-screen bg-sheet lg:grid lg:grid-cols-[minmax(0,5fr)_minmax(0,6fr)]">
      <figure className="relative hidden lg:block">
        <img
          src="/images/excavadora-cat-320.jpg"
          alt="Excavadora hidráulica Caterpillar 320 cargando tierra en una obra"
          className="absolute inset-0 size-full object-cover object-[30%_center]"
        />
        <figcaption className="absolute right-0 bottom-0 left-0 bg-black/55 px-6 py-3 text-xs text-white">
          Foto: «CAT 320 excavator», J. Smith · CC BY-SA 3.0 · Wikimedia Commons
        </figcaption>
      </figure>

      <div className="flex min-h-screen flex-col px-4 py-6 sm:px-10 lg:px-16">
        <BrandWordmark />
        <div className={`m-auto w-full py-10 ${width === 'wide' ? 'max-w-[560px]' : 'max-w-[380px]'}`}>
          <h1 className="text-[22px] leading-7 font-semibold text-ink">{title}</h1>
          <p className="mt-1 mb-8 text-sm text-slate-600">{description}</p>
          {children}
        </div>
      </div>
    </main>
  )
}
