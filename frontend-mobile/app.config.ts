import type { ConfigContext, ExpoConfig } from 'expo/config'

/**
 * Extends app.json with values that must not live in the repository. In Expo Go the map works without
 * a key; an Android build (EAS) needs GOOGLE_MAPS_API_KEY for react-native-maps to draw Google Maps.
 */
export default ({ config }: ConfigContext): ExpoConfig => {
  const googleMapsApiKey = process.env.GOOGLE_MAPS_API_KEY
  return {
    ...config,
    name: config.name ?? 'RentEquip',
    slug: config.slug ?? 'rentequip',
    android: {
      ...config.android,
      ...(googleMapsApiKey && { config: { ...config.android?.config, googleMaps: { apiKey: googleMapsApiKey } } }),
    },
  }
}
