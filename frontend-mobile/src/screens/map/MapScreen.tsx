import { Ionicons } from '@expo/vector-icons'
import { useNavigation } from '@react-navigation/native'
import { useCallback, useMemo, useRef, useState } from 'react'
import { ActivityIndicator, Pressable, StyleSheet, Text, View } from 'react-native'
import MapView, { Callout, Marker, type Region } from 'react-native-maps'
import { AppButton } from '../../components/AppButton'
import { LoadingView } from '../../components/StateViews'
import { useAsync } from '../../hooks/useAsync'
import { useAuth } from '../../hooks/useAuth'
import { useCurrentLocation } from '../../hooks/useCurrentLocation'
import type { RootNavigation } from '../../navigation/types'
import { equipmentService } from '../../services/rentequipApi'
import { colors, radius, spacing } from '../../theme'
import type { EquipmentStatus } from '../../types/api'
import { formatCurrency } from '../../utils/format'

const KM_PER_DEGREE = 111
const MAX_RESULTS = 100
const DEFAULT_RADIUS_KM = 50
const MAX_RADIUS_KM = 1000

const PIN_COLORS: Record<EquipmentStatus, string> = {
  AVAILABLE: '#059669',
  RENTED: '#0284C7',
  MAINTENANCE: '#F59E0B',
  INACTIVE: '#64748B',
}

interface SearchArea {
  latitude: number
  longitude: number
  radiusKm: number
}

function regionFor(area: SearchArea): Region {
  const delta = (area.radiusKm * 2) / KM_PER_DEGREE
  return { latitude: area.latitude, longitude: area.longitude, latitudeDelta: delta, longitudeDelta: delta }
}

/** Radius that covers what the user sees on screen, so "search this area" matches the visible map. */
function radiusFor(region: Region): number {
  const visibleKm = (Math.max(region.latitudeDelta, region.longitudeDelta) * KM_PER_DEGREE) / 2
  return Math.min(MAX_RADIUS_KM, Math.max(1, Math.round(visibleKm)))
}

export default function MapScreen() {
  const navigation = useNavigation<RootNavigation>()
  const { user } = useAuth()
  const location = useCurrentLocation()
  const mapRef = useRef<MapView>(null)
  const [chosenArea, setArea] = useState<SearchArea | null>(null)
  const [visibleRegion, setVisibleRegion] = useState<Region | null>(null)

  // Until the user searches another zone, the map is centred on the phone (or Lima) once the GPS answers.
  const { latitude, longitude } = location.coords
  const isLocating = location.status === 'locating'
  const initialArea = useMemo<SearchArea | null>(
    () => (isLocating ? null : { latitude, longitude, radiusKm: location.isFallback ? 30 : DEFAULT_RADIUS_KM }),
    [isLocating, latitude, longitude, location.isFallback],
  )
  const area = chosenArea ?? initialArea

  const companyId = user?.companyId
  const fetchArea = useCallback(
    (signal: AbortSignal) =>
      equipmentService.search(
        { latitude: area?.latitude, longitude: area?.longitude, radiusKm: area?.radiusKm, excludeOwnerId: companyId, page: 0, size: MAX_RESULTS },
        signal,
      ),
    [area, companyId],
  )
  const { data, error, isLoading, reload } = useAsync(area ? fetchArea : null)

  if (!area) return <LoadingView label="Ubicándote en el mapa…" />

  const equipment = data?.content ?? []
  const movedAway =
    visibleRegion !== null &&
    (Math.abs(visibleRegion.latitude - area.latitude) > visibleRegion.latitudeDelta / 4 ||
      Math.abs(visibleRegion.longitude - area.longitude) > visibleRegion.longitudeDelta / 4 ||
      Math.abs(radiusFor(visibleRegion) - area.radiusKm) > area.radiusKm / 2)

  function searchVisibleArea() {
    if (!visibleRegion) return
    setArea({ latitude: visibleRegion.latitude, longitude: visibleRegion.longitude, radiusKm: radiusFor(visibleRegion) })
  }

  function centerOnMe() {
    if (location.isFallback) {
      location.requestAccess()
      return
    }
    mapRef.current?.animateToRegion(regionFor({ ...location.coords, radiusKm: 10 }), 500)
  }

  return (
    <View style={styles.container}>
      <MapView
        ref={mapRef}
        style={StyleSheet.absoluteFill}
        initialRegion={regionFor(area)}
        showsUserLocation={!location.isFallback}
        showsMyLocationButton={false}
        onRegionChangeComplete={setVisibleRegion}
        accessibilityLabel="Mapa de maquinaria disponible"
      >
        {equipment.map((item) => (
          <Marker
            key={item.id}
            coordinate={{ latitude: item.latitude, longitude: item.longitude }}
            pinColor={PIN_COLORS[item.status]}
            title={item.name}
          >
            <Callout onPress={() => navigation.navigate('EquipmentDetail', { equipmentId: item.id, name: item.name })}>
              <View style={styles.callout}>
                <Text style={styles.calloutTitle} numberOfLines={1}>
                  {item.name}
                </Text>
                <Text style={styles.calloutText}>
                  {formatCurrency(item.dailyRate, item.currency)}/día · {item.ownerCompanyName}
                </Text>
                <Text style={styles.calloutLink}>Ver ficha ›</Text>
              </View>
            </Callout>
          </Marker>
        ))}
      </MapView>

      <View style={styles.topBar} pointerEvents="box-none">
        <View style={styles.pill}>
          {isLoading ? (
            <ActivityIndicator size="small" color={colors.brand} />
          ) : (
            <Ionicons name="construct" size={16} color={colors.brandDark} />
          )}
          <Text style={styles.pillText}>
            {isLoading
              ? 'Buscando…'
              : `${data?.totalElements ?? 0} ${data?.totalElements === 1 ? 'máquina' : 'máquinas'} en ${area.radiusKm} km`}
          </Text>
        </View>
        {movedAway && !isLoading && (
          <AppButton title="Buscar en esta zona" icon="search" onPress={searchVisibleArea} style={styles.searchHere} />
        )}
        {location.message && <Text style={styles.warning}>{location.message}</Text>}
        {error && (
          <View style={styles.errorBox}>
            <Text style={styles.errorText}>{error.message}</Text>
            <Pressable onPress={reload} accessibilityRole="button">
              <Text style={styles.retry}>Reintentar</Text>
            </Pressable>
          </View>
        )}
      </View>

      <Pressable
        onPress={centerOnMe}
        style={styles.fab}
        accessibilityRole="button"
        accessibilityLabel={location.isFallback ? 'Activar ubicación' : 'Centrar en mi ubicación'}
      >
        <Ionicons name={location.isFallback ? 'navigate-outline' : 'locate'} size={24} color={colors.ink} />
      </Pressable>
    </View>
  )
}

const styles = StyleSheet.create({
  container: { flex: 1 },
  topBar: { position: 'absolute', top: spacing.md, left: spacing.md, right: spacing.md, alignItems: 'center', gap: spacing.sm },
  pill: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: spacing.sm,
    backgroundColor: colors.surface,
    borderRadius: radius.pill,
    paddingHorizontal: spacing.lg,
    paddingVertical: spacing.sm,
    elevation: 4,
    shadowColor: '#000',
    shadowOpacity: 0.12,
    shadowRadius: 6,
    shadowOffset: { width: 0, height: 2 },
  },
  pillText: { fontSize: 14, fontWeight: '600', color: colors.ink },
  searchHere: { minHeight: 40, elevation: 4 },
  warning: {
    backgroundColor: colors.warningSoft,
    color: colors.text,
    fontSize: 12,
    padding: spacing.sm,
    borderRadius: radius.sm,
    overflow: 'hidden',
  },
  errorBox: { flexDirection: 'row', gap: spacing.md, backgroundColor: colors.dangerSoft, padding: spacing.sm, borderRadius: radius.sm, alignItems: 'center' },
  errorText: { flex: 1, fontSize: 12, color: colors.danger },
  retry: { fontWeight: '700', color: colors.danger },
  fab: {
    position: 'absolute',
    right: spacing.lg,
    bottom: spacing.xl,
    width: 52,
    height: 52,
    borderRadius: 26,
    backgroundColor: colors.surface,
    alignItems: 'center',
    justifyContent: 'center',
    elevation: 5,
    shadowColor: '#000',
    shadowOpacity: 0.2,
    shadowRadius: 6,
    shadowOffset: { width: 0, height: 2 },
  },
  callout: { width: 220, gap: 2, padding: 4 },
  calloutTitle: { fontSize: 14, fontWeight: '700', color: colors.ink },
  calloutText: { fontSize: 12, color: colors.muted },
  calloutLink: { fontSize: 12, fontWeight: '700', color: colors.brandDark, marginTop: 2 },
})
