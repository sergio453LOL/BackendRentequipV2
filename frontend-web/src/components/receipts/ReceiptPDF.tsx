import { Document, Page, StyleSheet, Text, View } from '@react-pdf/renderer'
import type { Payment, PaymentStatus, PaymentType, Reservation } from '../../types'
import { formatCurrency, formatDate, reservationCode } from '../../utils/format'

const BRAND = '#f59e0b'
const INK = '#0f172a'
const MUTED = '#64748b'
const LINE = '#e2e8f0'

const styles = StyleSheet.create({
  page: { padding: 40, fontSize: 10, color: INK, fontFamily: 'Helvetica' },
  header: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: 24 },
  brand: { fontSize: 20, fontFamily: 'Helvetica-Bold' },
  brandAccent: { color: BRAND },
  tagline: { color: MUTED, marginTop: 2 },
  docTitle: { fontSize: 12, fontFamily: 'Helvetica-Bold', textAlign: 'right' },
  code: { fontSize: 16, fontFamily: 'Helvetica-Bold', textAlign: 'right', marginTop: 2 },
  issued: { color: MUTED, textAlign: 'right', marginTop: 2 },
  band: { height: 4, backgroundColor: BRAND, marginBottom: 20 },
  section: { marginBottom: 18 },
  sectionTitle: { fontSize: 9, fontFamily: 'Helvetica-Bold', color: MUTED, textTransform: 'uppercase', marginBottom: 6 },
  grid: { flexDirection: 'row', flexWrap: 'wrap' },
  cell: { width: '50%', marginBottom: 8 },
  label: { color: MUTED, fontSize: 8, marginBottom: 2 },
  value: { fontSize: 10 },
  row: { flexDirection: 'row', justifyContent: 'space-between', paddingVertical: 6, borderBottomWidth: 1, borderBottomColor: LINE },
  totalRow: { flexDirection: 'row', justifyContent: 'space-between', paddingVertical: 8, marginTop: 4 },
  totalLabel: { fontSize: 12, fontFamily: 'Helvetica-Bold' },
  totalValue: { fontSize: 12, fontFamily: 'Helvetica-Bold' },
  tableHeader: { flexDirection: 'row', backgroundColor: '#f8fafc', paddingVertical: 5, paddingHorizontal: 4 },
  tableRow: { flexDirection: 'row', paddingVertical: 5, paddingHorizontal: 4, borderBottomWidth: 1, borderBottomColor: LINE },
  colType: { width: '28%' },
  colStatus: { width: '18%' },
  colRef: { width: '34%', fontSize: 8 },
  colAmount: { width: '20%', textAlign: 'right' },
  th: { fontSize: 8, fontFamily: 'Helvetica-Bold', color: MUTED },
  footer: { position: 'absolute', bottom: 32, left: 40, right: 40, color: MUTED, fontSize: 8, textAlign: 'center' },
})

const PAYMENT_TYPE_LABELS: Record<PaymentType, string> = {
  RENTAL: 'Alquiler',
  SECURITY_DEPOSIT: 'Garantía',
  REFUND: 'Reembolso',
}

const PAYMENT_STATUS_LABELS: Record<PaymentStatus, string> = {
  PENDING: 'Pendiente',
  AUTHORIZED: 'Retenida',
  COMPLETED: 'Cobrado',
  FAILED: 'Fallido',
  REFUNDED: 'Devuelto',
}

interface ReceiptDocumentProps {
  reservation: Reservation
  payments: Payment[]
  issuedAt: Date
}

export function ReceiptDocument({ reservation, payments, issuedAt }: ReceiptDocumentProps) {
  const money = (amount: number) => formatCurrency(amount, reservation.currency)
  const depositHold = payments.find((payment) => payment.type === 'SECURITY_DEPOSIT')

  return (
    <Document title={`Comprobante ${reservationCode(reservation.id)}`} author="RentEquip" language="es">
      <Page size="A4" style={styles.page}>
        <View style={styles.header}>
          <View>
            <Text style={styles.brand}>
              Rent<Text style={styles.brandAccent}>Equip</Text>
            </Text>
            <Text style={styles.tagline}>Marketplace B2B de maquinaria de construcción</Text>
          </View>
          <View>
            <Text style={styles.docTitle}>Comprobante de pago</Text>
            <Text style={styles.code}>{reservationCode(reservation.id)}</Text>
            <Text style={styles.issued}>
              Emitido el {issuedAt.toLocaleDateString('es-PE', { day: '2-digit', month: 'long', year: 'numeric' })}
            </Text>
          </View>
        </View>
        <View style={styles.band} />

        <View style={styles.section}>
          <Text style={styles.sectionTitle}>Detalle de la reserva</Text>
          <View style={styles.grid}>
            <Field label="Empresa arrendataria" value={reservation.renterCompanyName} />
            <Field label="Maquinaria" value={reservation.equipmentName} />
            <Field label="Fecha de inicio" value={formatDate(reservation.startDate)} />
            <Field label="Fecha de fin" value={formatDate(reservation.endDate)} />
            <Field label="Duración" value={`${reservation.totalDays} ${reservation.totalDays === 1 ? 'día' : 'días'}`} />
            <Field label="Tarifa diaria" value={money(reservation.dailyRate)} />
          </View>
        </View>

        <View style={styles.section}>
          <Text style={styles.sectionTitle}>Desglose</Text>
          <View style={styles.row}>
            <Text>
              Alquiler ({money(reservation.dailyRate)} × {reservation.totalDays})
            </Text>
            <Text>{money(reservation.rentalAmount)}</Text>
          </View>
          <View style={styles.row}>
            <Text>
              Depósito de garantía {depositHold ? `(${PAYMENT_STATUS_LABELS[depositHold.status].toLowerCase()})` : ''}
            </Text>
            <Text>{money(reservation.securityDeposit)}</Text>
          </View>
          <View style={styles.totalRow}>
            <Text style={styles.totalLabel}>Total</Text>
            <Text style={styles.totalValue}>{money(reservation.totalAmount)}</Text>
          </View>
        </View>

        {payments.length > 0 && (
          <View style={styles.section}>
            <Text style={styles.sectionTitle}>Movimientos</Text>
            <View style={styles.tableHeader}>
              <Text style={[styles.th, styles.colType]}>Concepto</Text>
              <Text style={[styles.th, styles.colStatus]}>Estado</Text>
              <Text style={[styles.th, styles.colRef]}>Referencia</Text>
              <Text style={[styles.th, styles.colAmount]}>Monto</Text>
            </View>
            {payments.map((payment) => (
              <View key={payment.id} style={styles.tableRow}>
                <Text style={styles.colType}>{PAYMENT_TYPE_LABELS[payment.type]}</Text>
                <Text style={styles.colStatus}>{PAYMENT_STATUS_LABELS[payment.status]}</Text>
                <Text style={styles.colRef}>{payment.providerReference ?? '—'}</Text>
                <Text style={styles.colAmount}>{money(payment.amount)}</Text>
              </View>
            ))}
          </View>
        )}

        <Text style={styles.footer} fixed>
          La garantía se retiene en la tarjeta y se libera al finalizar el alquiler sin incidencias. Este documento
          resume la transacción en RentEquip y no reemplaza a un comprobante electrónico de SUNAT.
        </Text>
      </Page>
    </Document>
  )
}

function Field({ label, value }: { label: string; value: string }) {
  return (
    <View style={styles.cell}>
      <Text style={styles.label}>{label}</Text>
      <Text style={styles.value}>{value}</Text>
    </View>
  )
}

