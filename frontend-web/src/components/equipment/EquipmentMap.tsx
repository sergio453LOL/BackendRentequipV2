import 'leaflet/dist/leaflet.css'
import { divIcon, latLngBounds } from 'leaflet'
import { useEffect } from 'react'
import { MapContainer, Marker, Popup, TileLayer, useMap } from 'react-leaflet'
import { Link } from 'react-router-dom'
import { buildPath } from '../../routes/paths'
import type { EquipmentSummary } from '../../types'
import { formatCurrency } from '../../utils/format'

/** Lima's Plaza Mayor: the backend's default search centre, used when there is nothing to show. */
const LIMA: [number, number] = [-12.04637, -77.04279]

const shortPrice = (machine: EquipmentSummary) => formatCurrency(machine.dailyRate, machine.currency).replace(/\.00$/, '')

/** Machines at the same yard (within about a kilometre) share one pin instead of stacking. */
function groupByPlace(machines: EquipmentSummary[]): EquipmentSummary[][] {
  const groups = new Map<string, EquipmentSummary[]>()
  for (const machine of machines) {
    const key = `${machine.latitude.toFixed(2)},${machine.longitude.toFixed(2)}`
    groups.set(key, [...(groups.get(key) ?? []), machine])
  }
  return [...groups.values()]
}

/** Price tag as the pin, so the map reads like a list of offers; a shared yard shows its count. */
function pinFor(group: EquipmentSummary[]) {
  const label = group.length === 1 ? shortPrice(group[0]) : `${group.length} equipos`
  return divIcon({ className: '', iconSize: [0, 0], html: `<span class="re-pin">${label}</span>` })
}

function FitToMachines({ machines }: { machines: EquipmentSummary[] }) {
  const map = useMap()
  useEffect(() => {
    if (machines.length === 0) return
    const bounds = latLngBounds(machines.map((machine) => [machine.latitude, machine.longitude] as [number, number]))
    map.fitBounds(bounds, { padding: [48, 48], maxZoom: 12 })
  }, [machines, map])
  return null
}

/** The catalogue on a map: one pin per yard, a card with each machine's photo and price when a pin opens. */
export default function EquipmentMap({ machines }: { machines: EquipmentSummary[] }) {
  return (
    <div className="relative isolate h-[560px] overflow-hidden rounded-2xl border border-slate-200/80 shadow-sm">
      <MapContainer center={LIMA} zoom={6} scrollWheelZoom className="h-full w-full">
        <TileLayer
          attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>'
          url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
        />
        <FitToMachines machines={machines} />
        {groupByPlace(machines).map((group) => (
          <Marker
            key={group.map((machine) => machine.id).join('-')}
            position={[group[0].latitude, group[0].longitude]}
            icon={pinFor(group)}
            title={group.map((machine) => machine.name).join(', ')}
          >
            <Popup minWidth={230} maxWidth={250}>
              <ul className="max-h-72 space-y-3 overflow-y-auto">
                {group.map((machine) => (
                  <li key={machine.id}>
                    <Link to={buildPath.equipmentDetail(machine.id)} className="block no-underline">
                      {machine.primaryImageUrl && (
                        <img src={machine.primaryImageUrl} alt="" className="mb-2 aspect-[16/10] w-full rounded-md object-cover" />
                      )}
                      <span className="block text-sm font-semibold text-ink">{machine.name}</span>
                      <span className="block text-xs text-slate-500">
                        {machine.city} · {machine.ownerCompanyName}
                      </span>
                      <span className="num mt-1 block text-sm font-bold text-ink">{formatCurrency(machine.dailyRate, machine.currency)} / día</span>
                    </Link>
                  </li>
                ))}
              </ul>
            </Popup>
          </Marker>
        ))}
      </MapContainer>
    </div>
  )
}
