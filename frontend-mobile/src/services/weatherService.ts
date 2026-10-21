import { create } from 'axios'
import type { Coordinates } from '../utils/format'

/**
 * Site weather from Open-Meteo (free, no API key). Rain and wind decide whether earthworks, concrete
 * pours or crane lifts can go ahead, so the catalogue shows the forecast for where the user is.
 */
const openMeteo = create({ baseURL: 'https://api.open-meteo.com/v1', timeout: 10_000 })

interface OpenMeteoResponse {
  current: { temperature_2m: number; precipitation: number; weather_code: number; wind_speed_10m: number }
  daily: {
    time: string[]
    precipitation_probability_max: number[]
    temperature_2m_max: number[]
    temperature_2m_min: number[]
  }
}

export interface SiteWeather {
  temperature: number
  windSpeedKmh: number
  description: string
  rainProbabilityToday: number
  rainProbabilityTomorrow: number
  advice: string
}

/** WMO weather interpretation codes, grouped. */
function describe(code: number): string {
  if (code === 0) return 'Despejado'
  if (code <= 3) return 'Parcialmente nublado'
  if (code <= 48) return 'Neblina'
  if (code <= 57) return 'Llovizna'
  if (code <= 67) return 'Lluvia'
  if (code <= 77) return 'Nieve'
  if (code <= 82) return 'Chubascos'
  return 'Tormenta'
}

function adviceFor(rainToday: number, rainTomorrow: number, wind: number): string {
  if (wind >= 40) return 'Viento fuerte: evita izajes con grúa y trabajos en altura.'
  if (rainToday >= 60) return 'Alta probabilidad de lluvia hoy: reprograma vaciados de concreto y movimiento de tierras.'
  if (rainTomorrow >= 60) return 'Mañana podría llover: si vas a excavar, mejor hoy.'
  return 'Buen clima para trabajar en obra.'
}

export const weatherService = {
  async forSite({ latitude, longitude }: Coordinates, signal?: AbortSignal): Promise<SiteWeather> {
    const { data } = await openMeteo.get<OpenMeteoResponse>('/forecast', {
      signal,
      params: {
        latitude,
        longitude,
        current: 'temperature_2m,precipitation,weather_code,wind_speed_10m',
        daily: 'precipitation_probability_max,temperature_2m_max,temperature_2m_min',
        timezone: 'auto',
        forecast_days: 2,
      },
    })
    const rainToday = data.daily.precipitation_probability_max[0] ?? 0
    const rainTomorrow = data.daily.precipitation_probability_max[1] ?? 0
    return {
      temperature: Math.round(data.current.temperature_2m),
      windSpeedKmh: Math.round(data.current.wind_speed_10m),
      description: describe(data.current.weather_code),
      rainProbabilityToday: rainToday,
      rainProbabilityTomorrow: rainTomorrow,
      advice: adviceFor(rainToday, rainTomorrow, data.current.wind_speed_10m),
    }
  },
}
