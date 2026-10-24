import { FilterX, SearchX } from 'lucide-react'
import { useEffect, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { Button } from '../components/common/Button'
import { EmptyState } from '../components/common/EmptyState'
import { ErrorState } from '../components/common/ErrorState'
import { PageHeader } from '../components/common/PageHeader'
import { Pagination } from '../components/common/Pagination'
import { Select } from '../components/common/Select'
import { SkeletonLoader } from '../components/common/SkeletonLoader'
import { CatalogSearch } from '../components/common/TopBar'
import { EquipmentCard } from '../components/equipment/EquipmentCard'
import { useAuth } from '../hooks/useAuth'
import { useFetch } from '../hooks/useFetch'
import { usePagination } from '../hooks/usePagination'
import { useQueryParam } from '../hooks/useQueryParam'
import { api } from '../services/api'
import { ENDPOINTS } from '../services/endpoints'
import type { EquipmentCategory, EquipmentSummary, PageResponse } from '../types'
import { PERU_CITIES } from '../utils/cities'
import { cn } from '../utils/cn'
import { formatNumber } from '../utils/format'

const ALL = 'all'

interface CountFilters {
  q?: string
  city?: string
  excludeOwnerId?: number
}

/**
 * How many machines each category holds under the current search and city, so the type filter shows
 * what a click will return. One request per category, asking only for the total.
 */
function useCategoryCounts(categories: EquipmentCategory[], filters: CountFilters): Record<string, number> | null {
  const key = JSON.stringify({ ids: categories.map((category) => category.id), ...filters })
  const [result, setResult] = useState<{ key: string; counts: Record<string, number> } | null>(null)

  useEffect(() => {
    if (categories.length === 0) return
    const controller = new AbortController()
    const ids: (number | null)[] = [null, ...categories.map((category) => category.id)]
    Promise.all(
      ids.map(async (categoryId) => {
        const { data } = await api.get<PageResponse<EquipmentSummary>>(ENDPOINTS.equipmentSearch, {
          params: { ...filters, categoryId: categoryId ?? undefined, page: 0, size: 1 },
          signal: controller.signal,
        })
        return [categoryId === null ? ALL : String(categoryId), data.totalElements] as const
      }),
    )
      .then((entries) => setResult({ key, counts: Object.fromEntries(entries) }))
      .catch(() => undefined)
    return () => controller.abort()
    // `key` captures categories and filters.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [key])

  return result?.key === key ? result.counts : null
}

function CategoryCards({
  categories,
  counts,
  selected,
  onSelect,
}: {
  categories: EquipmentCategory[]
  counts: Record<string, number> | null
  selected: string
  onSelect: (value: string) => void
}) {
  const items = [{ value: '', id: ALL, name: 'Todas' }, ...categories.map((category) => ({ value: String(category.id), id: String(category.id), name: category.name }))]
  return (
    <div role="group" aria-label="Tipo de maquinaria" className="-mx-4 flex gap-2 overflow-x-auto px-4 pb-1 sm:mx-0 sm:px-0">
      {items.map((item) => {
        const isSelected = selected === item.value
        const count = counts?.[item.id]
        return (
          <button
            key={item.id}
            type="button"
            aria-pressed={isSelected}
            onClick={() => onSelect(item.value)}
            className={cn(
              'flex h-16 min-w-[132px] shrink-0 flex-col justify-center rounded-xl border px-4 text-left transition-all duration-200 sm:flex-1',
              isSelected ? 'border-brand bg-accent-tint ring-1 ring-brand' : 'border-slate-200 bg-white hover:border-slate-300 hover:shadow-sm',
            )}
          >
            <span className="truncate text-[13px] text-slate-600">{item.name}</span>
            <span className={cn('num text-lg font-bold', count === 0 ? 'text-slate-400' : 'text-ink')}>
              {count === undefined ? '–' : formatNumber(count)}
            </span>
          </button>
        )
      })}
    </div>
  )
}

export default function CatalogPage() {
  const { user } = useAuth()
  const [, setSearchParams] = useSearchParams()
  const [query] = useQueryParam('q')
  const [categoryId, setCategoryId] = useQueryParam('categoryId')
  const [city, setCity] = useQueryParam('city')
  const { page, size, setPage, setSize } = usePagination()

  const categories = useFetch<EquipmentCategory[]>(ENDPOINTS.categories)
  const filters: CountFilters = { q: query || undefined, city: city || undefined, excludeOwnerId: user?.companyId }
  const counts = useCategoryCounts(categories.data ?? [], filters)
  const results = useFetch<PageResponse<EquipmentSummary>>(ENDPOINTS.equipmentSearch, {
    ...filters,
    categoryId: categoryId || undefined,
    page,
    size,
  })

  const hasFilters = Boolean(query || categoryId || city)
  const total = results.data?.totalElements

  function clearFilters() {
    setSearchParams({}, { replace: true })
  }

  return (
    <div className="mx-auto max-w-[1200px] space-y-5">
      <PageHeader
        title="Catálogo de maquinaria"
        description={
          query
            ? `Resultados para “${query}” en equipos de otras empresas.`
            : 'Equipos de otras empresas, listos para reservar por días.'
        }
        breadcrumbs={[{ label: 'Catálogo' }]}
      />

      <CatalogSearch id="catalog-page-search" className="sm:hidden" />

      {categories.error ? (
        <p className="text-sm text-slate-600">No pudimos cargar las categorías; puedes buscar por nombre o ciudad.</p>
      ) : (
        <CategoryCards categories={categories.data ?? []} counts={counts} selected={categoryId} onSelect={setCategoryId} />
      )}

      <div className="flex flex-wrap items-end justify-between gap-3">
        <p className="num text-sm text-slate-600" aria-live="polite">
          {total === undefined ? 'Buscando…' : total === 1 ? '1 máquina' : `${formatNumber(total)} máquinas`}
          {city && ` en ${city}`}
        </p>
        <div className="flex items-end gap-2">
          <Select
            label="Ciudad"
            placeholder="Todas las ciudades"
            value={city}
            onChange={(event) => setCity(event.target.value)}
            options={PERU_CITIES.map((item) => ({ value: item.name, label: item.name }))}
            className="w-48"
          />
          {hasFilters && (
            <Button variant="ghost" onClick={clearFilters} leftIcon={<FilterX className="size-4" />}>
              Limpiar
            </Button>
          )}
        </div>
      </div>

      {results.error && <ErrorState error={results.error} onRetry={results.refetch} />}

      {!results.error && !results.data && <SkeletonLoader variant="card" count={8} />}

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
              'grid gap-4 transition-opacity sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4',
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
