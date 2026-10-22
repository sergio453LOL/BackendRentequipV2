import { Ionicons } from '@expo/vector-icons'
import { useFocusEffect } from '@react-navigation/native'
import { useCallback, useRef, useState } from 'react'
import { Alert, Image, Modal, Pressable, RefreshControl, ScrollView, StyleSheet, Text, View } from 'react-native'
import { AppButton } from '../../components/AppButton'
import { PaymentStatusBadge, ReservationStatusBadge } from '../../components/Badge'
import { ErrorView, LoadingView } from '../../components/StateViews'
import { useAction } from '../../hooks/useAction'
import { useAsync } from '../../hooks/useAsync'
import { useAuth } from '../../hooks/useAuth'
import type { RootScreenProps } from '../../navigation/types'
import { handoverService, reservationService } from '../../services/rentequipApi'
import { colors, radius, spacing, typography } from '../../theme'
import type { HandoverPhoto, Reservation, ReservationStatus } from '../../types/api'
import { formatCurrency, formatDate } from '../../utils/format'
import { HANDOVER_STAGE_LABELS } from '../../utils/labels'

const PHOTO_STATUSES: ReservationStatus[] = ['CONFIRMED', 'IN_PROGRESS', 'COMPLETED']

interface StatusAction {
  label: string
  icon: keyof typeof Ionicons.glyphMap
  confirmTitle: string
  confirmMessage: string
  success: string
  destructive?: boolean
  run: (reservation: Reservation) => Promise<Reservation>
}

const ACTIONS = {
  confirm: {
    label: 'Confirmar',
    icon: 'checkmark-circle-outline',
    confirmTitle: 'Confirmar reserva',
    confirmMessage: 'El equipo quedará reservado para esta empresa en esas fechas.',
    success: 'Reserva confirmada',
    run: (reservation) => reservationService.confirm(reservation.id),
  },
  reject: {
    label: 'Rechazar',
    icon: 'close-circle-outline',
    confirmTitle: 'Rechazar solicitud',
    confirmMessage: 'La otra empresa será notificada y las fechas quedarán libres.',
    success: 'Solicitud rechazada',
    destructive: true,
    run: (reservation) => reservationService.updateStatus(reservation.id, 'REJECTED'),
  },
  start: {
    label: 'Iniciar alquiler',
    icon: 'play-circle-outline',
    confirmTitle: 'Iniciar alquiler',
    confirmMessage: '¿El equipo ya fue entregado? Te recomendamos tomar fotos de la entrega.',
    success: 'Alquiler en curso',
    run: (reservation) => reservationService.updateStatus(reservation.id, 'IN_PROGRESS'),
  },
  complete: {
    label: 'Finalizar alquiler',
    icon: 'flag-outline',
    confirmTitle: 'Finalizar alquiler',
    confirmMessage: '¿El equipo ya fue devuelto? Documenta su estado con fotos de devolución.',
    success: 'Alquiler finalizado',
    run: (reservation) => reservationService.updateStatus(reservation.id, 'COMPLETED'),
  },
  cancel: {
    label: 'Cancelar reserva',
    icon: 'trash-outline',
    confirmTitle: 'Cancelar reserva',
    confirmMessage: 'Esta acción no se puede deshacer. Si ya pagaste, se reembolsará el monto.',
    success: 'Reserva cancelada',
    destructive: true,
    run: (reservation) => reservationService.cancel(reservation.id),
  },
} satisfies Record<string, StatusAction>

/** Mirrors the backend's ReservationStatusPolicy so only possible transitions are offered. */
function availableActions(reservation: Reservation, isOwner: boolean, canManage: boolean): StatusAction[] {
  const actions: StatusAction[] = []
  if (isOwner && canManage) {
    if (reservation.status === 'PENDING') actions.push(ACTIONS.confirm, ACTIONS.reject)
    if (reservation.status === 'CONFIRMED') actions.push(ACTIONS.start)
    if (reservation.status === 'IN_PROGRESS') actions.push(ACTIONS.complete)
  }
  if (reservation.status === 'PENDING' || reservation.status === 'CONFIRMED') actions.push(ACTIONS.cancel)
  return actions
}

export default function ReservationDetailScreen({ route, navigation }: RootScreenProps<'ReservationDetail'>) {
  const { reservationId } = route.params
  const { user } = useAuth()
  const { run, isRunning } = useAction()
  const [viewing, setViewing] = useState<HandoverPhoto | null>(null)

  const fetchReservation = useCallback((signal: AbortSignal) => reservationService.findById(reservationId, signal), [reservationId])
  const fetchPhotos = useCallback((signal: AbortSignal) => handoverService.list(reservationId, signal), [reservationId])
  const reservation = useAsync(fetchReservation)
  const photos = useAsync(fetchPhotos)

  // New photos are taken in the camera screen; reload them when coming back.
  const isFirstFocus = useRef(true)
  const reloadPhotos = photos.reload
  useFocusEffect(
    useCallback(() => {
      if (isFirstFocus.current) {
        isFirstFocus.current = false
        return
      }
      reloadPhotos()
    }, [reloadPhotos]),
  )

  if (reservation.isLoading) return <LoadingView label="Cargando reserva…" />
  if (reservation.error || !reservation.data) {
    return reservation.error ? <ErrorView error={reservation.error} onRetry={reservation.reload} /> : null
  }

  const data = reservation.data
  const isOwner = data.renterCompanyId !== user?.companyId
  const canManage = user?.role !== 'OPERATOR'
  const actions = availableActions(data, isOwner, canManage)
  const canTakePhotos = PHOTO_STATUSES.includes(data.status)

  function askAndRun(action: StatusAction) {
    Alert.alert(action.confirmTitle, action.confirmMessage, [
      { text: 'Volver', style: 'cancel' },
      {
        text: action.label,
        style: action.destructive ? 'destructive' : 'default',
        onPress: () => void run(() => action.run(data), { successMessage: action.success, onSuccess: reservation.reload }),
      },
    ])
  }

  return (
    <ScrollView
      style={styles.container}
      contentContainerStyle={styles.content}
      refreshControl={
        <RefreshControl
          refreshing={reservation.isRefreshing}
          onRefresh={() => {
            reservation.refresh()
            photos.reload()
          }}
        />
      }
    >
      <View style={styles.card}>
        <Text style={styles.code}>Reserva #{data.id}</Text>
        <Pressable onPress={() => navigation.navigate('EquipmentDetail', { equipmentId: data.equipmentId, name: data.equipmentName })}>
          <Text style={styles.title}>{data.equipmentName} ›</Text>
        </Pressable>
        <View style={styles.badges}>
          <ReservationStatusBadge status={data.status} />
          <PaymentStatusBadge status={data.paymentStatus} />
        </View>
        <Line icon="calendar-outline" text={`${formatDate(data.startDate)} – ${formatDate(data.endDate)} (${data.totalDays} ${data.totalDays === 1 ? 'día' : 'días'})`} />
        <Line icon="business-outline" text={`Arrendatario: ${data.renterCompanyName}`} />
        {data.notes && <Line icon="chatbubble-ellipses-outline" text={data.notes} />}
      </View>

      <View style={styles.card}>
        <Amount label={`Alquiler (${formatCurrency(data.dailyRate, data.currency)} × ${data.totalDays})`} value={formatCurrency(data.rentalAmount, data.currency)} />
        <Amount label="Garantía reembolsable" value={formatCurrency(data.securityDeposit, data.currency)} />
        <Amount label="Total" value={formatCurrency(data.totalAmount, data.currency)} bold />
      </View>

      {actions.length > 0 && (
        <View style={styles.actions}>
          {actions.map((action) => (
            <AppButton
              key={action.label}
              title={action.label}
              icon={action.icon}
              variant={action.destructive ? 'secondary' : 'primary'}
              onPress={() => askAndRun(action)}
              isLoading={isRunning}
            />
          ))}
        </View>
      )}

      <View style={styles.card}>
        <Text style={styles.sectionTitle}>Fotos de entrega y devolución</Text>
        <Text style={styles.caption}>
          Documenta el estado del equipo con la cámara. Las fotos guardan fecha y ubicación, y las ven ambas empresas.
        </Text>
        {canTakePhotos ? (
          <View style={styles.photoButtons}>
            <AppButton
              title="Foto de entrega"
              icon="camera-outline"
              variant="secondary"
              style={styles.flex}
              onPress={() => navigation.navigate('HandoverCamera', { reservationId: data.id, stage: 'DELIVERY' })}
            />
            <AppButton
              title="Foto de devolución"
              icon="camera-reverse-outline"
              variant="secondary"
              style={styles.flex}
              onPress={() => navigation.navigate('HandoverCamera', { reservationId: data.id, stage: 'RETURN' })}
            />
          </View>
        ) : (
          <Text style={styles.caption}>Podrás agregar fotos cuando la reserva esté confirmada.</Text>
        )}

        {photos.isLoading ? (
          <LoadingView label="Cargando fotos…" />
        ) : photos.error ? (
          <ErrorView error={photos.error} onRetry={photos.reload} />
        ) : (photos.data ?? []).length === 0 ? (
          <Text style={styles.caption}>Todavía no hay fotos.</Text>
        ) : (
          <View style={styles.grid}>
            {(photos.data ?? []).map((photo) => (
              <Pressable key={photo.id} style={styles.thumb} onPress={() => setViewing(photo)} accessibilityLabel={`Foto de ${HANDOVER_STAGE_LABELS[photo.stage]}`}>
                <Image source={{ uri: photo.imageUrl }} style={styles.thumbImage} />
                <Text style={styles.thumbLabel}>{HANDOVER_STAGE_LABELS[photo.stage]}</Text>
              </Pressable>
            ))}
          </View>
        )}
      </View>

      <Modal visible={viewing !== null} transparent animationType="fade" onRequestClose={() => setViewing(null)}>
        <Pressable style={styles.viewer} onPress={() => setViewing(null)} accessibilityLabel="Cerrar foto">
          {viewing && (
            <>
              <Image source={{ uri: viewing.imageUrl }} style={styles.viewerImage} resizeMode="contain" />
              <View style={styles.viewerInfo}>
                <Text style={styles.viewerTitle}>
                  {HANDOVER_STAGE_LABELS[viewing.stage]} · {viewing.uploadedByCompanyName}
                </Text>
                <Text style={styles.viewerText}>{viewing.createdAt.replace('T', ' ').slice(0, 16)}</Text>
                {viewing.latitude !== null && viewing.longitude !== null && (
                  <Text style={styles.viewerText}>
                    📍 {viewing.latitude.toFixed(5)}, {viewing.longitude.toFixed(5)}
                  </Text>
                )}
                {viewing.note && <Text style={styles.viewerText}>{viewing.note}</Text>}
              </View>
            </>
          )}
        </Pressable>
      </Modal>
    </ScrollView>
  )
}

function Line({ icon, text }: { icon: keyof typeof Ionicons.glyphMap; text: string }) {
  return (
    <View style={styles.line}>
      <Ionicons name={icon} size={16} color={colors.muted} />
      <Text style={styles.lineText}>{text}</Text>
    </View>
  )
}

function Amount({ label, value, bold }: { label: string; value: string; bold?: boolean }) {
  return (
    <View style={styles.amount}>
      <Text style={[styles.amountLabel, bold && styles.bold]}>{label}</Text>
      <Text style={[styles.amountValue, bold && styles.bold]}>{value}</Text>
    </View>
  )
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: colors.background },
  content: { padding: spacing.lg, gap: spacing.lg, paddingBottom: 48 },
  flex: { flex: 1 },
  card: { backgroundColor: colors.surface, borderRadius: radius.lg, borderWidth: 1, borderColor: colors.border, padding: spacing.lg, gap: spacing.md },
  code: typography.caption,
  title: typography.heading,
  badges: { flexDirection: 'row', gap: spacing.sm, flexWrap: 'wrap' },
  line: { flexDirection: 'row', gap: spacing.sm, alignItems: 'flex-start' },
  lineText: { flex: 1, fontSize: 14, color: colors.text },
  amount: { flexDirection: 'row', justifyContent: 'space-between', gap: spacing.md },
  amountLabel: { flex: 1, fontSize: 14, color: colors.muted },
  amountValue: { fontSize: 14, color: colors.ink, fontWeight: '600' },
  bold: { fontWeight: '800', color: colors.ink, fontSize: 16 },
  actions: { gap: spacing.sm },
  sectionTitle: typography.heading,
  caption: typography.caption,
  photoButtons: { flexDirection: 'row', gap: spacing.sm },
  grid: { flexDirection: 'row', flexWrap: 'wrap', gap: spacing.sm },
  thumb: { width: '31%', gap: 4 },
  thumbImage: { width: '100%', aspectRatio: 1, borderRadius: radius.sm, backgroundColor: colors.border },
  thumbLabel: { fontSize: 11, color: colors.muted, textAlign: 'center' },
  viewer: { flex: 1, backgroundColor: 'rgba(15,23,42,0.95)', justifyContent: 'center', padding: spacing.lg, gap: spacing.lg },
  viewerImage: { width: '100%', height: '65%' },
  viewerInfo: { gap: 4 },
  viewerTitle: { color: '#FFFFFF', fontSize: 16, fontWeight: '700' },
  viewerText: { color: '#CBD5E1', fontSize: 14 },
})
