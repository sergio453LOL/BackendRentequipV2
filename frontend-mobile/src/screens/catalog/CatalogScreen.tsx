import { Ionicons } from '@expo/vector-icons'
import { useNavigation } from '@react-navigation/native'
import { useCallback, useState } from 'react'
import { ActivityIndicator, FlatList, Pressable, RefreshControl, ScrollView, StyleSheet, Text, TextInput, View } from 'react-native'
import { AppButton } from '../../components/AppButton'
import { Chip } from '../../components/Chip'
import { EquipmentCard } from '../../components/EquipmentCard'
import { EmptyState, ErrorView, LoadingView } from '../../components/StateViews'
import { WeatherCard } from '../../components/WeatherCard'
import { useAuth } from '../../hooks/useAuth'
import { useCurrentLocation } from '../../hooks/useCurrentLocation'
import { useDebounce } from '../../hooks/useDebounce'
import { usePaginatedList } from '../../hooks/usePaginatedList'
import type { RootNavigation } from '../../navigation/types'
import { equipmentService } from '../../services/rentequipApi'
import { colors, radius, spacing } from '../../theme'
import type { EquipmentSummary } from '../../types/api'
import { distanceKm } from '../../utils/format'

const PAGE_SIZE = 10
/** `null` means "whole country": the backend then lists everything ordered from Lima. */
const RADIUS_OPTIONS: (number | null)[] = [10, 25, 50, 100, null]

export default function CatalogScreen() {
  const navigation = useNavigation<RootNavigation>()
  const { user } = useAuth()
  const location = useCurrentLocation()
  const [query, setQuery] = useState('')
  const [radiusKm, setRadiusKm] = useState<number | null>(25)
  const debouncedQuery = useDebounce(query.trim(), 400)

  // With the real GPS position we search around the phone; without it, the whole catalogue.
  const useGps = !location.isFallback && radiusKm !== null
  const { latitude, longitude } = location.coords
  const companyId = user?.companyId

  const fetchPage = useCallback(
    (page: number, signal: AbortSignal) =>
      equipmentService.search(
        {
          latitude: useGps ? latitude : undefined,
          longitude: useGps ? longitude : undefined,
          radiusKm: useGps ? (radiusKm ?? undefined) : undefined,
          q: debouncedQuery || undefined,
          excludeOwnerId: companyId,
          page,
          size: PAGE_SIZE,
        },
        signal,
      ),
    [useGps, latitude, longitude, radiusKm, debouncedQuery, companyId],
  )
  const list = usePaginatedList(fetchPage)

  if (location.status === 'locating') return <LoadingView label="Buscando tu ubicación…" />

  const header = (
    <View style={styles.header}>
      <WeatherCard coords={location.coords} place={location.isFallback ? 'Lima' : 'Tu ubicación'} />

      {location.message && (
        <View style={styles.locationBanner} accessibilityRole="alert">
          <Ionicons name="navigate-circle-outline" size={20} color={colors.brandDark} />
          <Text style={styles.locationText}>{location.message}</Text>
          <Pressable onPress={location.requestAccess} accessibilityRole="button" hitSlop={8}>
            <Text style={styles.locationAction}>Activar</Text>
          </Pressable>
        </View>
      )}

      <View style={styles.search}>
        <Ionicons name="search" size={18} color={colors.muted} />
        <TextInput
          value={query}
          onChangeText={setQuery}
          placeholder="Excavadora, Caterpillar, rodillo…"
          placeholderTextColor={colors.muted}
          style={styles.searchInput}
          accessibilityLabel="Buscar maquinaria"
          returnKeyType="search"
          autoCorrect={false}
        />
        {query.length > 0 && (
          <Pressable onPress={() => setQuery('')} accessibilityLabel="Limpiar búsqueda" hitSlop={8}>
            <Ionicons name="close-circle" size={18} color={colors.muted} />
          </Pressable>
        )}
      </View>

      {!location.isFallback && (
        <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={styles.chips}>
          {RADIUS_OPTIONS.map((option) => (
            <Chip
              key={option ?? 'all'}
              label={option === null ? 'Todo el Perú' : `${option} km`}
              selected={radiusKm === option}
              onPress={() => setRadiusKm(option)}
            />
          ))}
        </ScrollView>
      )}

      {!list.isLoading && !list.error && list.totalElements > 0 && (
        <Text style={styles.count}>
          {list.totalElements} {list.totalElements === 1 ? 'máquina encontrada' : 'máquinas encontradas'}
        </Text>
      )}
    </View>
  )

  const renderItem = ({ item }: { item: EquipmentSummary }) => (
    <EquipmentCard
      equipment={item}
      distanceKm={location.isFallback ? undefined : distanceKm(location.coords, item)}
      onPress={() => navigation.navigate('EquipmentDetail', { equipmentId: item.id, name: item.name })}
    />
  )

  return (
    <FlatList
      data={list.items}
      keyExtractor={(item) => String(item.id)}
      renderItem={renderItem}
      ListHeaderComponent={header}
      contentContainerStyle={styles.content}
      ItemSeparatorComponent={() => <View style={{ height: spacing.lg }} />}
      onEndReached={list.loadMore}
      onEndReachedThreshold={0.4}
      refreshControl={<RefreshControl refreshing={list.isRefreshing} onRefresh={list.refresh} tintColor={colors.brand} />}
      ListEmptyComponent={
        list.isLoading ? (
          <LoadingView label="Buscando maquinaria…" />
        ) : list.error ? (
          <ErrorView error={list.error} onRetry={list.retry} />
        ) : (
          <EmptyState
            icon="search-outline"
            title={debouncedQuery ? 'Sin resultados' : 'No hay maquinaria cerca'}
            description={
              debouncedQuery
                ? `No encontramos equipos para "${debouncedQuery}". Prueba con otra palabra.`
                : 'Amplía el radio de búsqueda para ver equipos de otras zonas.'
            }
            action={
              debouncedQuery ? (
                <AppButton title="Limpiar búsqueda" variant="secondary" onPress={() => setQuery('')} />
              ) : radiusKm !== null ? (
                <AppButton title="Ver todo el Perú" variant="secondary" onPress={() => setRadiusKm(null)} />
              ) : undefined
            }
          />
        )
      }
      ListFooterComponent={
        list.isLoadingMore ? (
          <ActivityIndicator style={styles.footer} color={colors.brand} />
        ) : list.error && list.items.length > 0 ? (
          <AppButton title="Reintentar" variant="secondary" icon="refresh" onPress={list.retry} style={styles.footer} />
        ) : undefined
      }
    />
  )
}

const styles = StyleSheet.create({
  content: { padding: spacing.lg, paddingBottom: spacing.xxl, flexGrow: 1, backgroundColor: colors.background },
  header: { gap: spacing.md, marginBottom: spacing.lg },
  locationBanner: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: spacing.sm,
    backgroundColor: colors.warningSoft,
    borderRadius: radius.md,
    padding: spacing.md,
  },
  locationText: { flex: 1, fontSize: 13, color: colors.text },
  locationAction: { fontSize: 14, fontWeight: '700', color: colors.brandDark },
  search: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: spacing.sm,
    backgroundColor: colors.surface,
    borderWidth: 1,
    borderColor: colors.border,
    borderRadius: radius.md,
    paddingHorizontal: spacing.md,
    minHeight: 48,
  },
  searchInput: { flex: 1, fontSize: 16, color: colors.ink, paddingVertical: spacing.sm },
  chips: { gap: spacing.sm },
  count: { fontSize: 13, color: colors.muted },
  footer: { marginVertical: spacing.lg },
})
