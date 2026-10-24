import { Menu, Search, Sparkles } from 'lucide-react'
import { useEffect, useRef, useState, type FormEvent } from 'react'
import { Link, useLocation, useNavigate, useSearchParams } from 'react-router-dom'
import { useDebounce } from '../../hooks/useDebounce'
import { PATHS } from '../../routes/paths'
import { cn } from '../../utils/cn'
import { BrandWordmark } from './BrandMark'

const SEARCH_DEBOUNCE_MS = 400

/**
 * Searches the catalogue from any screen. On the catalogue itself it filters as you type; elsewhere
 * Enter opens the catalogue with the query.
 */
function CatalogSearch({ id, className }: { id: string; className?: string }) {
  const navigate = useNavigate()
  const { pathname } = useLocation()
  const [searchParams, setSearchParams] = useSearchParams()
  const onCatalog = pathname === PATHS.catalog
  const urlQuery = onCatalog ? (searchParams.get('q') ?? '') : ''

  // The field follows the URL (back button, "Limpiar filtros") without an effect: derived state.
  const [text, setText] = useState(urlQuery)
  const [syncedQuery, setSyncedQuery] = useState(urlQuery)
  if (urlQuery !== syncedQuery) {
    setSyncedQuery(urlQuery)
    setText(urlQuery)
  }

  const debounced = useDebounce(text.trim(), SEARCH_DEBOUNCE_MS)
  const lastApplied = useRef(debounced)
  useEffect(() => {
    if (!onCatalog || debounced === lastApplied.current) return
    lastApplied.current = debounced
    setSearchParams(
      (params) => {
        if (debounced) params.set('q', debounced)
        else params.delete('q')
        params.delete('page')
        return params
      },
      { replace: true },
    )
  }, [debounced, onCatalog, setSearchParams])

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const query = text.trim()
    if (onCatalog) return
    navigate(query ? `${PATHS.catalog}?q=${encodeURIComponent(query)}` : PATHS.catalog)
  }

  return (
    <form role="search" onSubmit={handleSubmit} className={cn('relative', className)}>
      <label htmlFor={id} className="sr-only">
        Buscar maquinaria en el catálogo
      </label>
      <Search className="pointer-events-none absolute top-1/2 left-3 size-4 -translate-y-1/2 text-slate-400" aria-hidden="true" />
      <input
        id={id}
        type="search"
        value={text}
        onChange={(event) => setText(event.target.value)}
        placeholder="Buscar excavadora, grúa, rodillo…"
        autoComplete="off"
        className="h-8 w-full rounded-md border border-slate-300 bg-white pr-3 pl-9 text-sm text-ink placeholder:text-slate-500 focus:border-ink focus:ring-2 focus:ring-slate-200 focus:outline-none"
      />
    </form>
  )
}

interface TopBarProps {
  onOpenMenu: () => void
  onToggleAssistant: () => void
  isAssistantOpen: boolean
}

/** 48px bar on the frame: the catalogue search in the middle and the assistant on the right. */
export function TopBar({ onOpenMenu, onToggleAssistant, isAssistantOpen }: TopBarProps) {
  return (
    <header className="sticky top-0 z-30 flex h-12 items-center gap-2 bg-frame px-2 lg:static lg:px-0 lg:pr-2">
      <button
        type="button"
        onClick={onOpenMenu}
        aria-label="Abrir menú"
        className="flex size-10 items-center justify-center rounded-md text-slate-600 hover:bg-active lg:hidden"
      >
        <Menu className="size-5" />
      </button>
      <Link to={PATHS.home} className="lg:hidden" aria-label="RentEquip, ir al panel">
        <BrandWordmark />
      </Link>

      <CatalogSearch id="topbar-search" className="mx-auto hidden w-full max-w-[548px] sm:block" />

      <button
        type="button"
        onClick={onToggleAssistant}
        aria-expanded={isAssistantOpen}
        className={cn(
          'ml-auto flex h-8 shrink-0 items-center gap-2 rounded-md px-3 text-sm font-medium text-slate-700 transition-colors hover:bg-active sm:ml-0',
          isAssistantOpen && 'bg-active text-ink',
        )}
      >
        <Sparkles className="size-4 text-accent" aria-hidden="true" />
        <span className="hidden sm:inline">Asistente</span>
        <span className="sr-only sm:hidden">Asistente de IA</span>
      </button>
    </header>
  )
}

export { CatalogSearch }
