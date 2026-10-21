import { Ionicons } from '@expo/vector-icons'
import { useState } from 'react'
import { Image, Pressable, StyleSheet, Text, View } from 'react-native'
import { colors, radius, spacing } from '../theme'
import type { EquipmentSummary } from '../types/api'
import { formatCurrency, formatDistance } from '../utils/format'
import { EquipmentStatusBadge } from './Badge'

interface EquipmentCardProps {
  equipment: EquipmentSummary
  distanceKm?: number
  onPress: () => void
}

export function EquipmentImage({ uri, height }: { uri: string | null | undefined; height: number }) {
  const [failed, setFailed] = useState(false)
  if (!uri || failed) {
    return (
      <View style={[styles.placeholder, { height }]} accessibilityLabel="Sin foto">
        <Ionicons name="construct-outline" size={height / 3} color={colors.muted} />
      </View>
    )
  }
  return <Image source={{ uri }} style={{ height, width: '100%' }} resizeMode="cover" onError={() => setFailed(true)} />
}

export function EquipmentCard({ equipment, distanceKm, onPress }: EquipmentCardProps) {
  return (
    <Pressable
      onPress={onPress}
      accessibilityRole="button"
      accessibilityLabel={`${equipment.name}, ${formatCurrency(equipment.dailyRate, equipment.currency)} por día`}
      style={({ pressed }) => [styles.card, pressed && styles.pressed]}
    >
      <EquipmentImage uri={equipment.primaryImageUrl} height={150} />
      <View style={styles.body}>
        <View style={styles.row}>
          <Text style={styles.name} numberOfLines={1}>
            {equipment.name}
          </Text>
          <EquipmentStatusBadge status={equipment.status} />
        </View>
        <Text style={styles.subtitle}>
          {equipment.brand} · {equipment.model}
        </Text>
        <View style={styles.row}>
          <View style={styles.meta}>
            <Ionicons name="location-outline" size={14} color={colors.muted} />
            <Text style={styles.metaText}>
              {equipment.city}
              {distanceKm !== undefined ? ` · a ${formatDistance(distanceKm)}` : ''}
            </Text>
          </View>
          <Text style={styles.price}>
            {formatCurrency(equipment.dailyRate, equipment.currency)}
            <Text style={styles.perDay}> /día</Text>
          </Text>
        </View>
        <Text style={styles.owner} numberOfLines={1}>
          {equipment.ownerCompanyName}
        </Text>
      </View>
    </Pressable>
  )
}

const styles = StyleSheet.create({
  card: {
    backgroundColor: colors.surface,
    borderRadius: radius.lg,
    overflow: 'hidden',
    borderWidth: 1,
    borderColor: colors.border,
  },
  pressed: { opacity: 0.9 },
  placeholder: { width: '100%', backgroundColor: '#E2E8F0', alignItems: 'center', justifyContent: 'center' },
  body: { padding: spacing.lg, gap: spacing.xs },
  row: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', gap: spacing.sm },
  name: { flex: 1, fontSize: 17, fontWeight: '700', color: colors.ink },
  subtitle: { fontSize: 14, color: colors.muted },
  meta: { flexDirection: 'row', alignItems: 'center', gap: 4, flexShrink: 1 },
  metaText: { fontSize: 13, color: colors.muted },
  price: { fontSize: 17, fontWeight: '800', color: colors.ink },
  perDay: { fontSize: 13, fontWeight: '400', color: colors.muted },
  owner: { fontSize: 12, color: colors.muted },
})
