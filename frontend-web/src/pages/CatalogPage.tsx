import { FilterX, SearchX } from 'lucide-react'
import { useEffect, useRef, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { Button } from '../components/common/Button'
import { EmptyState } from '../components/common/EmptyState'
import { ErrorState } from '../components/common/ErrorState'
import { PageHeader } from '../components/common/PageHeader'
import { Pagination } from '../components/common/Pagination'
import { SearchBar } from '../components/common/SearchBar'
import { Select } from '../components/common/Select'
import { SkeletonLoader } from '../components/common/SkeletonLoader'
import { EquipmentCard } from '../components/equipment/EquipmentCard'
import { useAuth } from '../hooks/useAuth'
import { useDebounce } from '../hooks/useDebounce'
import { useFetch } from '../hooks/useFetch'
import { usePagination } from '../hooks/usePagination'
import { useQueryParam } from '../hooks/useQueryParam'
import { ENDPOINTS } from '../services/endpoints'
import type { EquipmentCategory, EquipmentSummary, PageResponse } from '../types'
import { PERU_CITIES } from '../utils/cities'
import { cn } from '../utils/cn'

const SEARCH_DEBOUNCE_MS = 400

export default function CatalogPage() {
  const { user } = useAuth()
  const [, setSearchParams] = useSearchParams()
  const [query, setQuery] = useQueryParam('q')
  const [categoryId, setCategoryId] = useQueryParam('categoryId')
  const [city, setCity] = useQueryParam('city')
  const { page, size, setPage, setSize } = usePagination(10)

  const [searchText, setSearchText] = useState(query)
  const debouncedSearch = useDebounce(searchText.trim(), SEARCH_DEBOUNCE_MS)

  const lastDebouncedSearch = useRef(debouncedSearch)
  useEffect(() => {
    if (debouncedSearch === lastDebouncedSearch.current) return
    lastDebouncedSearch.current = debouncedSearch
    setQuery(debouncedSearch)
  }, [debouncedSearch, setQuery])

  const categories = useFetch<EquipmentCategory[]>(ENDPOINTS.categories)
  const results = useFetch<PageResponse<EquipmentSummary>>(ENDPOINTS.equipmentSearch, {
    q: query || undefined,
    categoryId: categoryId || undefined,
    city: city || undefined,
    excludeOwnerId: user?.companyId,
    page,
    size,
  })

  const hasFilters = Boolean(query || categoryId || city)
  const isTyping = searchText.trim() !== query

  function clearFilters() {
    setSearchText('')
    setSearchParams({}, { replace: true })
  }

  return (
    <div className="mx-auto max-w-7xl space-y-6">
      <PageHeader
        title="Catálogo de maquinaria"
        description="Encuentra equipos disponibles de otras empresas y solicita tu reserva."
        breadcrumbs={[{ label: 'Catálogo' }]}
      />

      <section aria-label="Filtros" className="grid gap-4 rounded-2xl border border-slate-200 bg-white p-4 shadow-sm md:grid-cols-[1fr_14rem_12rem_auto] md:items-end">
        <SearchBar
          value={searchText}
          onChange={setSearchText}
          label="Buscar maquinaria"
          placeholder="Busca por nombre, marca o modelo…"
          isSearching={isTyping}
        />
        <Select
          label="Categoría"
          placeholder="Todas las categorías"
          value={categoryId}
          onChange={(event) => setCategoryId(event.target.value)}
          options={(categories.data ?? []).map((category) => ({ value: String(category.id), label: category.name }))}
          hint={categories.error ? 'No pudimos cargar las categorías' : undefined}
        />
        <Select
          label="Ciudad"
          placeholder="Todas las ciudades"
          value={city}
          onChange={(event) => setCity(event.target.value)}
          options={PERU_CITIES.map((item) => ({ value: item.name, label: item.name }))}
        />
        <Button
          variant="ghost"
          onClick={clearFilters}
          disabled={!hasFilters && !searchText}
          leftIcon={<FilterX className="size-4" />}
          className="md:mb-0"
        >
          Limpiar
        </Button>
      </section>

      {results.error && <ErrorState error={results.error} onRetry={results.refetch} />}

      {!results.error && !results.data && <SkeletonLoader variant="card" count={6} />}

      {!results.error && results.data && results.data.totalElements === 0 && (
        <EmptyState
          icon={SearchX}
          title={hasFilters ? 'No encontramos maquinaria con esos filtros' : 'Aún no hay maquinaria publicada'}
          description={
            hasFilters
              ? 'Prueba con otra búsqueda, otra categoría o quita la ciudad para ver más resultados.'
              : 'Cuando otras empresas publiquen sus equipos los verás aquí.'
          }
          action={
            hasFilters && (
              <Button onClick={clearFilters} leftIcon={<FilterX className="size-4" />}>
                Limpiar filtros
              </Button>
            )
          }
        />
      )}

      {!results.error && results.data && results.data.totalElements > 0 && (
        <>
          <ul
            aria-busy={results.isLoading}
            className={cn(
              'grid gap-5 transition-opacity sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4',
              results.isLoading && 'opacity-60',
            )}
          >
            {results.data.content.map((equipment) => (
              <li key={equipment.id}>
                <EquipmentCard equipment={equipment} />
              </li>
            ))}
          </ul>
          <Pagination pageData={results.data} onPageChange={setPage} onSizeChange={setSize} isDisabled={results.isLoading} />
        </>
      )}
    </div>
  )
}
