import { ChevronLeft, ChevronRight } from 'lucide-react'
import { useState } from 'react'
import { cn } from '../../utils/cn'
import { EquipmentImage } from './EquipmentImage'

interface ImageGalleryProps {
  images: string[]
  alt: string
}

export function ImageGallery({ images, alt }: ImageGalleryProps) {
  const [selectedIndex, setSelectedIndex] = useState(0)
  const hasMany = images.length > 1
  const current = images[selectedIndex] ?? null

  function showRelative(offset: number) {
    setSelectedIndex((index) => (index + offset + images.length) % images.length)
  }

  return (
    <div className="space-y-3">
      <div className="relative overflow-hidden rounded-lg bg-raised">
        <EquipmentImage src={current} alt={`${alt}, foto ${selectedIndex + 1}`} className="aspect-[4/3] w-full" />
        {hasMany && (
          <>
            <GalleryArrow side="left" onClick={() => showRelative(-1)} />
            <GalleryArrow side="right" onClick={() => showRelative(1)} />
            <span className="absolute right-3 bottom-3 rounded-full bg-slate-900/70 px-2.5 py-1 text-xs text-white">
              {selectedIndex + 1} / {images.length}
            </span>
          </>
        )}
      </div>

      {hasMany && (
        <ul className="grid grid-cols-4 gap-2 sm:grid-cols-5" aria-label="Miniaturas">
          {images.map((image, index) => (
            <li key={image}>
              <button
                type="button"
                onClick={() => setSelectedIndex(index)}
                aria-label={`Ver foto ${index + 1}`}
                aria-pressed={index === selectedIndex}
                className={cn(
                  'w-full overflow-hidden rounded-lg ring-2 transition',
                  index === selectedIndex ? 'ring-slate-300' : 'ring-transparent opacity-70 hover:opacity-100',
                )}
              >
                <EquipmentImage src={image} alt="" className="aspect-square w-full" />
              </button>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}

function GalleryArrow({ side, onClick }: { side: 'left' | 'right'; onClick: () => void }) {
  const Icon = side === 'left' ? ChevronLeft : ChevronRight
  return (
    <button
      type="button"
      onClick={onClick}
      aria-label={side === 'left' ? 'Foto anterior' : 'Foto siguiente'}
      className={cn(
        'absolute top-1/2 flex size-10 -translate-y-1/2 items-center justify-center rounded-full bg-white/90 text-slate-700 shadow transition hover:bg-white',
        side === 'left' ? 'left-3' : 'right-3',
      )}
    >
      <Icon className="size-5" />
    </button>
  )
}
