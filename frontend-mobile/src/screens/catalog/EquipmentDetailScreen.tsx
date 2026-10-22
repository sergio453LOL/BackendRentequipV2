import { Ionicons } from '@expo/vector-icons'
import { useCallback, useState } from 'react'
import { KeyboardAvoidingView, Platform, ScrollView, StyleSheet, Text, View, useWindowDimensions } from 'react-native'
import { AppButton } from '../../components/AppButton'
import { EquipmentStatusBadge } from '../../components/Badge'
import { DateField } from '../../components/DateField'
import { EquipmentImage } from '../../components/EquipmentCard'
import { ErrorView, LoadingView } from '../../components/StateViews'
import { TextField } from '../../components/TextField'
import { useAction } from '../../hooks/useAction'
import { useAsync } from '../../hooks/useAsync'
import { useAuth } from '../../hooks/useAuth'
import { useDebounce } from '../../hooks/useDebounce'
import type { RootScreenProps } from '../../navigation/types'
import { equipmentService, reservationService } from '../../services/rentequipApi'
import { colors, radius, spacing, typography } from '../../theme'
import type { Equipment, IsoDate } from '../../types/api'
import { formatCurrency, rentalDays, todayIso } from '../../utils/format'

const MAX_RENTAL_DAYS = 365
const MAX_NOTES = 500

function parseSpecifications(text: string | null): { label: string; value: string }[] {
  if (!text) return []
  return text
    .split(/\r?\n/)
    .map((line) => line.split(':'))
    .filter((parts) => parts.length >= 2 && parts[0].trim() && parts.slice(1).join(':').trim())
    .map((parts) => ({ label: parts[0].trim(), value: parts.slice(1).join(':').trim() }))
}

function Gallery({ images, width }: { images: string[]; width: number }) {
  const [index, setIndex] = useState(0)
  if (images.length === 0) return <EquipmentImage uri={null} height={240} />
  return (
    <View>
      <ScrollView
        horizontal
        pagingEnabled
        showsHorizontalScrollIndicator={false}
        onMomentumScrollEnd={(event) => setIndex(Math.round(event.nativeEvent.contentOffset.x / width))}
        accessibilityLabel={`Galería de ${images.length} fotos`}
      >
        {images.map((uri) => (
          <View key={uri} style={{ width }}>
            <EquipmentImage uri={uri} height={240} />
          </View>
        ))}
      </ScrollView>
      {images.length > 1 && (
        <View style={styles.dots}>
          {images.map((uri, dotIndex) => (
            <View key={uri} style={[styles.dot, dotIndex === index && styles.dotActive]} />
          ))}
        </View>
      )}
    </View>
  )
}

function ReservationForm({ equipment, onBooked }: { equipment: Equipment; onBooked: () => void }) {
  const [startDate, setStartDate] = useState<IsoDate | null>(null)
  const [endDate, setEndDate] = useState<IsoDate | null>(null)
  const [notes, setNotes] = useState('')
  const [submitted, setSubmitted] = useState(false)
  const { run, isRunning } = useAction()

  const today = todayIso()
  const startError = submitted && !startDate ? 'Elige la fecha de inicio.' : startDate && startDate < today ? 'No puede ser una fecha pasada.' : undefined
  const endError =
    submitted && !endDate
      ? 'Elige la fecha de fin.'
      : startDate && endDate && endDate < startDate
        ? 'Debe ser igual o posterior al inicio.'
        : startDate && endDate && rentalDays(startDate, endDate) > MAX_RENTAL_DAYS
          ? `Máximo ${MAX_RENTAL_DAYS} días.`
          : undefined
  const datesValid = Boolean(startDate && endDate && !startError && !endError)
  const days = datesValid && startDate && endDate ? rentalDays(startDate, endDate) : 0

  const range = useDebounce(datesValid ? `${startDate}|${endDate}` : null, 300)
  const checkAvailability = useCallback(
    (signal: AbortSignal) => {
      const [from, to] = (range ?? '').split('|')
      return reservationService.availability(equipment.id, from, to, signal)
    },
    [equipment.id, range],
  )
  const availability = useAsync(range ? checkAvailability : null)
  const isChecking = datesValid && (availability.isLoading || range !== `${startDate}|${endDate}`)
  const isTaken = datesValid && !isChecking && availability.data?.available === false

  async function submit() {
    setSubmitted(true)
    if (!datesValid || !startDate || !endDate || isTaken) return
    await run(
      () => reservationService.create({ equipmentId: equipment.id, startDate, endDate, notes: notes.trim() || undefined }),
      { successMessage: 'Solicitud enviada. El propietario la revisará pronto.', errorTitle: 'No se pudo reservar', onSuccess: onBooked },
    )
  }

  return (
    <View style={styles.card}>
      <Text style={styles.sectionTitle}>Solicitar reserva</Text>
      <View style={styles.dates}>
        <DateField label="Desde" value={startDate} onChange={setStartDate} minimumDate={new Date()} error={startError} />
        <DateField label="Hasta" value={endDate} onChange={setEndDate} minimumDate={new Date()} error={endError} />
      </View>
      {datesValid && (
        <View style={styles.availability} accessibilityLiveRegion="polite">
          {isChecking ? (
            <Text style={styles.muted}>Verificando disponibilidad…</Text>
          ) : isTaken ? (
            <Text style={styles.taken}>Ya está reservada en esas fechas. Prueba otro rango.</Text>
          ) : availability.data?.available ? (
            <Text style={styles.free}>✓ Disponible en esas fechas</Text>
          ) : null}
        </View>
      )}
      <TextField
        label="Notas para el propietario"
        value={notes}
        onChangeText={setNotes}
        placeholder="Dirección de la obra, horario de entrega…"
        multiline
        maxLength={MAX_NOTES}
        hint={`${notes.length}/${MAX_NOTES}`}
        style={styles.notes}
      />
      <View style={styles.summary}>
        <Row label={`${formatCurrency(equipment.dailyRate, equipment.currency)} × ${days} ${days === 1 ? 'día' : 'días'}`} value={formatCurrency(days * equipment.dailyRate, equipment.currency)} />
        <Row label="Garantía (reembolsable)" value={formatCurrency(equipment.securityDeposit, equipment.currency)} />
        <Row label="Total estimado" value={formatCurrency(days * equipment.dailyRate + equipment.securityDeposit, equipment.currency)} bold />
      </View>
      <AppButton title="Enviar solicitud" icon="send" onPress={submit} isLoading={isRunning} disabled={isChecking || isTaken} />
    </View>
  )
}

function Row({ label, value, bold }: { label: string; value: string; bold?: boolean }) {
  return (
    <View style={styles.row}>
      <Text style={[styles.rowLabel, bold && styles.bold]}>{label}</Text>
      <Text style={[styles.rowValue, bold && styles.bold]}>{value}</Text>
    </View>
  )
}

export default function EquipmentDetailScreen({ route, navigation }: RootScreenProps<'EquipmentDetail'>) {
  const { equipmentId } = route.params
  const { user } = useAuth()
  const { width } = useWindowDimensions()

  const fetchEquipment = useCallback((signal: AbortSignal) => equipmentService.findById(equipmentId, signal), [equipmentId])
  const fetchRating = useCallback((signal: AbortSignal) => equipmentService.rating(equipmentId, signal), [equipmentId])
  const { data: equipment, error, isLoading, reload } = useAsync(fetchEquipment)
  const rating = useAsync(fetchRating)

  if (isLoading) return <LoadingView label="Cargando ficha…" />
  if (error || !equipment) return error ? <ErrorView error={error} onRetry={reload} /> : null

  const specs = parseSpecifications(equipment.specifications)
  const isOwn = equipment.ownerCompanyId === user?.companyId
  const average = rating.data?.averageRating ?? 0

  return (
    <KeyboardAvoidingView style={styles.flex} behavior={Platform.OS === 'ios' ? 'padding' : undefined} keyboardVerticalOffset={90}>
      <ScrollView style={styles.flex} contentContainerStyle={styles.content} keyboardShouldPersistTaps="handled">
        <Gallery images={equipment.imageUrls} width={width} />
        <View style={styles.body}>
          <View style={styles.titleRow}>
            <EquipmentStatusBadge status={equipment.status} />
            {average > 0 && (
              <Text style={styles.rating}>
                <Ionicons name="star" size={14} color={colors.brand} /> {average.toFixed(1)}
              </Text>
            )}
          </View>
          <Text style={styles.title}>{equipment.name}</Text>
          <Text style={styles.muted}>
            {equipment.brand} · {equipment.model}
            {equipment.manufactureYear ? ` · ${equipment.manufactureYear}` : ''}
          </Text>
          <Text style={styles.price}>
            {formatCurrency(equipment.dailyRate, equipment.currency)}
            <Text style={styles.muted}> /día</Text>
          </Text>

          <View style={styles.card}>
            <Info icon="shield-checkmark-outline" text={`Garantía reembolsable de ${formatCurrency(equipment.securityDeposit, equipment.currency)}`} />
            <Info icon="location-outline" text={[equipment.address, equipment.city].filter(Boolean).join(', ')} />
            <Info icon="business-outline" text={equipment.ownerCompanyName} />
            {equipment.categories.length > 0 && <Info icon="pricetags-outline" text={equipment.categories.join(' · ')} />}
          </View>

          {equipment.description && (
            <View style={styles.card}>
              <Text style={styles.sectionTitle}>Descripción</Text>
              <Text style={styles.text}>{equipment.description}</Text>
            </View>
          )}

          {specs.length > 0 && (
            <View style={styles.card}>
              <Text style={styles.sectionTitle}>Especificaciones técnicas</Text>
              {specs.map((spec) => (
                <Row key={spec.label} label={spec.label} value={spec.value} />
              ))}
            </View>
          )}

          {isOwn ? (
            <Text style={styles.notice}>Este equipo es de tu empresa.</Text>
          ) : equipment.status !== 'AVAILABLE' ? (
            <Text style={styles.notice}>Este equipo no está disponible para nuevas reservas.</Text>
          ) : (
            <ReservationForm
              equipment={equipment}
              onBooked={() => navigation.navigate('Main', { screen: 'Reservations', params: { role: 'RENTER' } })}
            />
          )}
        </View>
      </ScrollView>
    </KeyboardAvoidingView>
  )
}

function Info({ icon, text }: { icon: keyof typeof Ionicons.glyphMap; text: string }) {
  return (
    <View style={styles.info}>
      <Ionicons name={icon} size={18} color={colors.muted} />
      <Text style={styles.infoText}>{text}</Text>
    </View>
  )
}

const styles = StyleSheet.create({
  flex: { flex: 1, backgroundColor: colors.background },
  content: { paddingBottom: 48 },
  body: { padding: spacing.lg, gap: spacing.md },
  dots: { flexDirection: 'row', justifyContent: 'center', gap: 6, position: 'absolute', bottom: 10, left: 0, right: 0 },
  dot: { width: 7, height: 7, borderRadius: 4, backgroundColor: 'rgba(255,255,255,0.6)' },
  dotActive: { backgroundColor: colors.brand, width: 18 },
  titleRow: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
  rating: { fontSize: 14, fontWeight: '700', color: colors.ink },
  title: typography.title,
  price: { fontSize: 26, fontWeight: '800', color: colors.ink },
  muted: { fontSize: 14, color: colors.muted },
  text: typography.body,
  card: { backgroundColor: colors.surface, borderRadius: radius.lg, borderWidth: 1, borderColor: colors.border, padding: spacing.lg, gap: spacing.md },
  sectionTitle: typography.heading,
  info: { flexDirection: 'row', gap: spacing.sm, alignItems: 'center' },
  infoText: { flex: 1, fontSize: 14, color: colors.text },
  dates: { flexDirection: 'row', gap: spacing.md },
  availability: { minHeight: 20 },
  free: { color: colors.success, fontWeight: '700' },
  taken: { color: colors.danger, fontWeight: '700' },
  notes: { minHeight: 80, textAlignVertical: 'top', paddingTop: spacing.md },
  summary: { backgroundColor: colors.background, borderRadius: radius.md, padding: spacing.md, gap: spacing.sm },
  row: { flexDirection: 'row', justifyContent: 'space-between', gap: spacing.md },
  rowLabel: { flex: 1, fontSize: 14, color: colors.muted },
  rowValue: { fontSize: 14, color: colors.ink, fontWeight: '600', textAlign: 'right' },
  bold: { fontWeight: '800', color: colors.ink, fontSize: 15 },
  notice: { ...typography.body, textAlign: 'center', color: colors.muted, padding: spacing.lg },
})
