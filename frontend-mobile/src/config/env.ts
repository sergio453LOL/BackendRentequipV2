import { Platform } from 'react-native'

/**
 * Backend URL as seen from the phone. On a physical device "localhost" is the phone itself, so set
 * EXPO_PUBLIC_API_URL to the PC's LAN IP or the deployed URL. Without it, emulators reach the PC through
 * their loopback aliases.
 */
const EMULATOR_DEFAULT =
  Platform.OS === 'android' ? 'http://10.0.2.2:8080/api/v1' : 'http://localhost:8080/api/v1'

export const API_BASE_URL = process.env.EXPO_PUBLIC_API_URL ?? EMULATOR_DEFAULT

/** Lima's Plaza Mayor: the backend uses the same point when the client sends no coordinates. */
export const DEFAULT_LOCATION = { latitude: -12.04637, longitude: -77.04279 } as const
