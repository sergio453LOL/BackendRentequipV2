export const colors = {
  brand: '#F59E0B',
  brandDark: '#B45309',
  brandSoft: '#FEF3C7',
  ink: '#0F172A',
  text: '#334155',
  muted: '#64748B',
  border: '#E2E8F0',
  background: '#F8FAFC',
  surface: '#FFFFFF',
  success: '#059669',
  successSoft: '#ECFDF5',
  danger: '#DC2626',
  dangerSoft: '#FEF2F2',
  info: '#0284C7',
  infoSoft: '#F0F9FF',
  warningSoft: '#FFFBEB',
} as const

export const spacing = { xs: 4, sm: 8, md: 12, lg: 16, xl: 24, xxl: 32 } as const

export const radius = { sm: 8, md: 12, lg: 16, pill: 999 } as const

export const typography = {
  title: { fontSize: 24, fontWeight: '700', color: colors.ink },
  heading: { fontSize: 18, fontWeight: '700', color: colors.ink },
  body: { fontSize: 15, color: colors.text },
  caption: { fontSize: 13, color: colors.muted },
} as const
