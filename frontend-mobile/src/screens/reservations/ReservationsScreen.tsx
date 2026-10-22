import { useFocusEffect, useNavigation, useRoute, type RouteProp } from '@react-navigation/native'
import { useCallback, useRef, useState } from 'react'
import { ActivityIndicator, FlatList, Pressable, RefreshControl, ScrollView, StyleSheet, Text, View } from 'react-native'
import { AppButton } from '../../components/AppButton'
import { Chip } from '../../components/Chip'
import { ReservationCard } from '../../components/ReservationCard'
import { EmptyState, ErrorView, LoadingView } from '../../components/StateViews'
import { usePaginatedList } from '../../hooks/usePaginatedList'
import type { MainTabParamList, RootNavigation } from '../../navigation/types'
import { reservationService } from '../../services/rentequipApi'
import { colors, radius, spacing } from '../../theme'
import type { ReservationRole, ReservationStatus } from '../../types/api'
import { RESERVATION_STATUS_LABELS } from '../../utils/labels'

const PAGE_SIZE = 10
const STATUS_FILTERS: (ReservationStatus | null)[] = [null, 'PENDING', 'CONFIRMED', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED']
const ROLE_LABELS: Record<ReservationRole, string> = { RENTER: 'Mis solicitudes', OWNER: 'De mis equipos' }

export default function ReservationsScreen() {
  const navigation = useNavigation<RootNavigation>()
  const route = useRoute<RouteProp<MainTabParamList, 'Reservations'>>()
  // A role passed by another screen (e.g. after booking) wins once; then the user's choice is kept.
  const paramRole = route.params?.role
  const [roleState, setRoleState] = useState<{ role: ReservationRole; fromParam?: ReservationRole }>({
    role: paramRole ?? 'RENTER',
    fromParam: paramRole,
  })
  if (paramRole && paramRole !== roleState.fromParam) setRoleState({ role: paramRole, fromParam: paramRole })
  const role = roleState.role
  const setRole = (next: ReservationRole) => setRoleState((current) => ({ ...current, role: next }))
  const [status, setStatus] = useState<ReservationStatus | null>(null)

  const fetchPage = useCallback(
    (page: number, signal: AbortSignal) =>
      reservationService.list({ role, status: status ?? undefined, page, size: PAGE_SIZE }, signal),
    [role, status],
  )
  const list = usePaginatedList(fetchPage)

  // Coming back from a detail screen (confirmed, cancelled, new photo…) shows the fresh state.
  const isFirstFocus = useRef(true)
  const { refresh } = list
  useFocusEffect(
    useCallback(() => {
      if (isFirstFocus.current) {
        isFirstFocus.current = false
        return
      }
      refresh()
    }, [refresh]),
  )

  return (
    <View style={styles.container}>
      <View style={styles.filters}>
        <View style={styles.segmented} accessibilityRole="tablist">
          {(['RENTER', 'OWNER'] as const).map((option) => (
            <Pressable
              key={option}
              onPress={() => setRole(option)}
              accessibilityRole="tab"
              accessibilityState={{ selected: role === option }}
              style={[styles.segment, role === option && styles.segmentActive]}
            >
              <Text style={[styles.segmentText, role === option && styles.segmentTextActive]}>{ROLE_LABELS[option]}</Text>
            </Pressable>
          ))}
        </View>
        <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={styles.chips}>
          {STATUS_FILTERS.map((option) => (
            <Chip
              key={option ?? 'all'}
              label={option ? RESERVATION_STATUS_LABELS[option] : 'Todas'}
              selected={status === option}
              onPress={() => setStatus(option)}
            />
          ))}
        </ScrollView>
      </View>

      <FlatList
        data={list.items}
        keyExtractor={(item) => String(item.id)}
        renderItem={({ item }) => (
          <ReservationCard reservation={item} role={role} onPress={() => navigation.navigate('ReservationDetail', { reservationId: item.id })} />
        )}
        contentContainerStyle={styles.list}
        ItemSeparatorComponent={() => <View style={{ height: spacing.md }} />}
        onEndReached={list.loadMore}
        onEndReachedThreshold={0.4}
        refreshControl={<RefreshControl refreshing={list.isRefreshing} onRefresh={list.refresh} tintColor={colors.brand} />}
        ListEmptyComponent={
          list.isLoading ? (
            <LoadingView label="Cargando reservas…" />
          ) : list.error ? (
            <ErrorView error={list.error} onRetry={list.retry} />
          ) : (
            <EmptyState
              icon="calendar-outline"
              title={status ? 'Sin reservas con ese estado' : 'Aún no hay reservas'}
              description={
                status
                  ? 'Quita el filtro para ver todo tu historial.'
                  : role === 'RENTER'
                    ? 'Busca maquinaria en el catálogo y solicita tu primera reserva.'
                    : 'Cuando otras empresas reserven tus equipos, aparecerán aquí.'
              }
              action={
                status ? (
                  <AppButton title="Ver todas" variant="secondary" onPress={() => setStatus(null)} />
                ) : role === 'RENTER' ? (
                  <AppButton title="Explorar catálogo" icon="search" onPress={() => navigation.navigate('Main', { screen: 'Catalog' })} />
                ) : undefined
              }
            />
          )
        }
        ListFooterComponent={list.isLoadingMore ? <ActivityIndicator style={{ margin: spacing.lg }} color={colors.brand} /> : undefined}
      />
    </View>
  )
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: colors.background },
  filters: { padding: spacing.lg, paddingBottom: spacing.sm, gap: spacing.md },
  segmented: { flexDirection: 'row', backgroundColor: '#E2E8F0', borderRadius: radius.md, padding: 4 },
  segment: { flex: 1, paddingVertical: spacing.sm, borderRadius: radius.sm, alignItems: 'center' },
  segmentActive: { backgroundColor: colors.surface },
  segmentText: { fontSize: 14, fontWeight: '600', color: colors.muted },
  segmentTextActive: { color: colors.ink },
  chips: { gap: spacing.sm },
  list: { padding: spacing.lg, paddingTop: spacing.sm, flexGrow: 1 },
})
