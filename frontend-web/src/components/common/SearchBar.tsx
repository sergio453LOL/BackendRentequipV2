import { Search, X } from 'lucide-react'
import { useId } from 'react'

interface SearchBarProps {
  value: string
  onChange: (value: string) => void
  placeholder?: string
  label?: string
  isSearching?: boolean
}

export function SearchBar({ value, onChange, placeholder = 'Buscar…', label = 'Buscar', isSearching = false }: SearchBarProps) {
  const inputId = useId()

  return (
    <div className="relative">
      <label htmlFor={inputId} className="sr-only">
        {label}
      </label>
      <Search className="pointer-events-none absolute top-1/2 left-3.5 size-5 -translate-y-1/2 text-slate-400" aria-hidden="true" />
      <input
        id={inputId}
        type="search"
        value={value}
        onChange={(event) => onChange(event.target.value)}
        placeholder={placeholder}
        autoComplete="off"
        aria-busy={isSearching}
        className="h-12 w-full rounded-xl border border-slate-300 bg-white pr-11 pl-11 text-sm shadow-sm placeholder:text-slate-400 focus:border-brand-500 focus:ring-2 focus:ring-brand-200 focus:outline-none [&::-webkit-search-cancel-button]:hidden"
      />
      {value && (
        <button
          type="button"
          onClick={() => onChange('')}
          aria-label="Limpiar búsqueda"
          className="absolute top-1/2 right-3 -translate-y-1/2 rounded-full p-1 text-slate-400 hover:bg-slate-100 hover:text-slate-700"
        >
          <X className="size-4" />
        </button>
      )}
    </div>
  )
}
