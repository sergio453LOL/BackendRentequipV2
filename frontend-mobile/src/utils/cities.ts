export interface City {
  name: string
  latitude: number
  longitude: number
}

export const PERU_CITIES: readonly City[] = [
  { name: 'Lima', latitude: -12.04637, longitude: -77.04279 },
  { name: 'Arequipa', latitude: -16.39889, longitude: -71.535 },
  { name: 'Trujillo', latitude: -8.11599, longitude: -79.02998 },
  { name: 'Chiclayo', latitude: -6.77137, longitude: -79.84088 },
  { name: 'Piura', latitude: -5.19449, longitude: -80.63282 },
  { name: 'Cusco', latitude: -13.53195, longitude: -71.96746 },
  { name: 'Huancayo', latitude: -12.06513, longitude: -75.20486 },
  { name: 'Iquitos', latitude: -3.74912, longitude: -73.25383 },
]
