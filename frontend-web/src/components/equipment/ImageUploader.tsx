import { ImagePlus, RotateCcw, Star, Trash2 } from 'lucide-react'
import { useCallback, useEffect, useId, useRef, useState, type DragEvent } from 'react'
import { equipmentService } from '../../services/equipmentService'
import { getErrorMessage } from '../../utils/apiError'
import { cn } from '../../utils/cn'
import { Spinner } from '../common/Spinner'

const MAX_IMAGES = 10
const MAX_FILE_BYTES = 5 * 1024 * 1024
const ACCEPTED_TYPES = ['image/jpeg', 'image/png', 'image/webp']

type UploadState = 'uploading' | 'done' | 'error'

interface UploadItem {
  id: string
  file: File
  previewUrl: string
  state: UploadState
  progress: number
  url?: string
  error?: string
}

interface ImageUploaderProps {
  /** Called with the public URLs of the photos uploaded so far, in display order (the first is the cover). */
  onChange: (urls: string[]) => void
  /** Lets the form block submit while photos are still uploading. */
  onBusyChange?: (isBusy: boolean) => void
  error?: string
}

export function ImageUploader({ onChange, onBusyChange, error }: ImageUploaderProps) {
  const inputId = useId()
  const [items, setItems] = useState<UploadItem[]>([])
  const [isDragging, setIsDragging] = useState(false)
  const [rejection, setRejection] = useState<string | null>(null)
  const itemsRef = useRef(items)

  useEffect(() => {
    itemsRef.current = items
    onChange(items.filter((item) => item.state === 'done' && item.url).map((item) => item.url as string))
    onBusyChange?.(items.some((item) => item.state === 'uploading'))
  }, [items, onChange, onBusyChange])

  useEffect(() => () => itemsRef.current.forEach((item) => URL.revokeObjectURL(item.previewUrl)), [])

  const updateItem = useCallback((id: string, patch: Partial<UploadItem>) => {
    setItems((current) => current.map((item) => (item.id === id ? { ...item, ...patch } : item)))
  }, [])

  const upload = useCallback(
    async (item: UploadItem) => {
      updateItem(item.id, { state: 'uploading', progress: 0, error: undefined })
      try {
        const url = await equipmentService.uploadImage(item.file, (progress) => updateItem(item.id, { progress }))
        updateItem(item.id, { state: 'done', progress: 100, url })
      } catch (uploadError) {
        updateItem(item.id, { state: 'error', error: getErrorMessage(uploadError) })
      }
    },
    [updateItem],
  )

  function addFiles(fileList: FileList | null) {
    if (!fileList) return
    setRejection(null)
    const room = MAX_IMAGES - items.length
    const accepted: UploadItem[] = []
    for (const file of Array.from(fileList)) {
      if (!ACCEPTED_TYPES.includes(file.type)) {
        setRejection(`"${file.name}" no es una imagen JPG, PNG o WEBP.`)
      } else if (file.size > MAX_FILE_BYTES) {
        setRejection(`"${file.name}" pesa más de 5 MB.`)
      } else if (accepted.length >= room) {
        setRejection(`Puedes subir como máximo ${MAX_IMAGES} fotos.`)
        break
      } else {
        accepted.push({
          id: crypto.randomUUID(),
          file,
          previewUrl: URL.createObjectURL(file),
          state: 'uploading',
          progress: 0,
        })
      }
    }
    setItems((current) => [...current, ...accepted])
    accepted.forEach((item) => void upload(item))
  }

  function removeItem(id: string) {
    setItems((current) => {
      const removed = current.find((item) => item.id === id)
      if (removed) URL.revokeObjectURL(removed.previewUrl)
      return current.filter((item) => item.id !== id)
    })
  }

  function makeCover(id: string) {
    setItems((current) => {
      const chosen = current.find((item) => item.id === id)
      return chosen ? [chosen, ...current.filter((item) => item.id !== id)] : current
    })
  }

  function handleDrop(event: DragEvent<HTMLLabelElement>) {
    event.preventDefault()
    setIsDragging(false)
    addFiles(event.dataTransfer.files)
  }

  const message = rejection ?? error

  return (
    <div className="space-y-3">
      <label
        htmlFor={inputId}
        onDragOver={(event) => {
          event.preventDefault()
          setIsDragging(true)
        }}
        onDragLeave={() => setIsDragging(false)}
        onDrop={handleDrop}
        className={cn(
          'flex cursor-pointer flex-col items-center gap-2 rounded-xl border-2 border-dashed px-6 py-10 text-center transition-all duration-200',
          isDragging ? 'scale-[1.01] border-brand bg-accent-tint' : 'border-slate-300 bg-white hover:border-brand hover:bg-amber-50/40',
          items.length >= MAX_IMAGES && 'pointer-events-none opacity-50',
        )}
      >
        <ImagePlus className="size-9 text-brand-strong" aria-hidden="true" />
        <span className="text-sm font-medium text-slate-700">Arrastra tus fotos aquí o haz clic para elegirlas</span>
        <span className="text-xs text-slate-500">JPG, PNG o WEBP · máximo 5 MB cada una · hasta {MAX_IMAGES} fotos</span>
        <input
          id={inputId}
          type="file"
          accept={ACCEPTED_TYPES.join(',')}
          multiple
          className="sr-only"
          disabled={items.length >= MAX_IMAGES}
          onChange={(event) => {
            addFiles(event.target.files)
            event.target.value = ''
          }}
        />
      </label>

      {message && (
        <p role="alert" className="text-xs text-red-600">
          {message}
        </p>
      )}

      {items.length > 0 && (
        <ul className="grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-5" aria-label="Fotos seleccionadas">
          {items.map((item, index) => (
            <li key={item.id} className="group relative overflow-hidden rounded-lg border border-slate-200 bg-slate-100">
              <img src={item.previewUrl} alt={`Foto ${index + 1}`} className="aspect-square w-full object-cover" />

              {index === 0 && item.state === 'done' && (
                <span className="absolute top-2 left-2 rounded-full bg-ink px-2 py-0.5 text-[10px] font-semibold text-white">
                  PORTADA
                </span>
              )}

              {item.state === 'uploading' && (
                <div className="absolute inset-0 flex flex-col items-center justify-center gap-2 bg-slate-900/55 px-3 text-white">
                  <Spinner size="sm" label="Subiendo foto" />
                  <span className="num text-xs font-semibold">{item.progress}%</span>
                  <div
                    role="progressbar"
                    aria-label={`Subiendo foto ${index + 1}`}
                    aria-valuemin={0}
                    aria-valuemax={100}
                    aria-valuenow={item.progress}
                    className="h-1.5 w-full overflow-hidden rounded-full bg-white/25"
                  >
                    <span className="block h-full rounded-full bg-brand transition-[width] duration-200" style={{ width: `${item.progress}%` }} />
                  </div>
                </div>
              )}

              {item.state === 'error' && (
                <div className="absolute inset-0 flex flex-col items-center justify-center gap-2 bg-red-900/70 p-2 text-center text-white">
                  <span className="text-[11px] leading-tight">{item.error}</span>
                  <button
                    type="button"
                    onClick={() => void upload(item)}
                    className="flex items-center gap-1 rounded bg-white/20 px-2 py-1 text-xs hover:bg-white/30"
                  >
                    <RotateCcw className="size-3" aria-hidden="true" /> Reintentar
                  </button>
                </div>
              )}

              <div className="absolute right-2 bottom-2 flex gap-1 opacity-100 transition sm:opacity-0 sm:group-focus-within:opacity-100 sm:group-hover:opacity-100">
                {index > 0 && item.state === 'done' && (
                  <button
                    type="button"
                    onClick={() => makeCover(item.id)}
                    aria-label={`Usar la foto ${index + 1} como portada`}
                    title="Usar como portada"
                    className="rounded-full bg-white/90 p-1.5 text-slate-700 shadow hover:bg-white"
                  >
                    <Star className="size-3.5" />
                  </button>
                )}
                <button
                  type="button"
                  onClick={() => removeItem(item.id)}
                  aria-label={`Quitar la foto ${index + 1}`}
                  title="Quitar"
                  className="rounded-full bg-white/90 p-1.5 text-red-600 shadow hover:bg-white"
                >
                  <Trash2 className="size-3.5" />
                </button>
              </div>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}
