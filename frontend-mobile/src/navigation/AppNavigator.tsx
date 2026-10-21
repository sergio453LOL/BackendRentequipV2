import { Ionicons } from '@expo/vector-icons'
import { createBottomTabNavigator } from '@react-navigation/bottom-tabs'
import { createNativeStackNavigator } from '@react-navigation/native-stack'
import { View } from 'react-native'
import { OfflineBanner } from '../components/OfflineBanner'
import { LoadingView } from '../components/StateViews'
import { useAuth } from '../hooks/useAuth'
import LoginScreen from '../screens/auth/LoginScreen'
import RegisterScreen from '../screens/auth/RegisterScreen'
import HandoverCameraScreen from '../screens/camera/HandoverCameraScreen'
import CatalogScreen from '../screens/catalog/CatalogScreen'
import EquipmentDetailScreen from '../screens/catalog/EquipmentDetailScreen'
import MapScreen from '../screens/map/MapScreen'
import ProfileScreen from '../screens/profile/ProfileScreen'
import ReservationDetailScreen from '../screens/reservations/ReservationDetailScreen'
import ReservationsScreen from '../screens/reservations/ReservationsScreen'
import { colors } from '../theme'
import type { AuthStackParamList, MainTabParamList, RootStackParamList } from './types'

const RootStack = createNativeStackNavigator<RootStackParamList>()
const AuthStack = createNativeStackNavigator<AuthStackParamList>()
const Tabs = createBottomTabNavigator<MainTabParamList>()

const TAB_ICONS: Record<keyof MainTabParamList, keyof typeof Ionicons.glyphMap> = {
  Catalog: 'search',
  Map: 'map',
  Reservations: 'calendar',
  Profile: 'person-circle',
}

function AuthNavigator() {
  return (
    <AuthStack.Navigator screenOptions={{ headerShown: false }}>
      <AuthStack.Screen name="Login" component={LoginScreen} />
      <AuthStack.Screen
        name="Register"
        component={RegisterScreen}
        options={{ headerShown: true, title: 'Registrar empresa', headerBackTitle: 'Volver' }}
      />
    </AuthStack.Navigator>
  )
}

function MainTabs() {
  return (
    <Tabs.Navigator
      screenOptions={({ route }) => ({
        tabBarActiveTintColor: colors.brandDark,
        tabBarInactiveTintColor: colors.muted,
        headerTitleStyle: { fontWeight: '700' },
        tabBarIcon: ({ color, size }) => <Ionicons name={TAB_ICONS[route.name]} size={size} color={color} />,
      })}
    >
      <Tabs.Screen name="Catalog" component={CatalogScreen} options={{ title: 'Catálogo' }} />
      <Tabs.Screen name="Map" component={MapScreen} options={{ title: 'Mapa' }} />
      <Tabs.Screen name="Reservations" component={ReservationsScreen} options={{ title: 'Mis reservas' }} />
      <Tabs.Screen name="Profile" component={ProfileScreen} options={{ title: 'Perfil' }} />
    </Tabs.Navigator>
  )
}

/**
 * Signed out: only the auth stack exists, so a stale deep link can never open a private screen.
 * Signed in: tabs plus the detail and camera screens pushed above them.
 */
export function AppNavigator() {
  const { status } = useAuth()
  if (status === 'restoring') return <LoadingView label="Abriendo RentEquip…" />

  return (
    <View style={{ flex: 1 }}>
      <OfflineBanner />
      <RootStack.Navigator screenOptions={{ headerBackTitle: 'Atrás', headerTintColor: colors.ink }}>
        {status === 'signedOut' ? (
          <RootStack.Screen name="Auth" component={AuthNavigator} options={{ headerShown: false }} />
        ) : (
          <>
            <RootStack.Screen name="Main" component={MainTabs} options={{ headerShown: false }} />
            <RootStack.Screen
              name="EquipmentDetail"
              component={EquipmentDetailScreen}
              options={({ route }) => ({ title: route.params.name ?? 'Maquinaria' })}
            />
            <RootStack.Screen name="ReservationDetail" component={ReservationDetailScreen} options={{ title: 'Reserva' }} />
            <RootStack.Screen
              name="HandoverCamera"
              component={HandoverCameraScreen}
              options={{ headerShown: false, presentation: 'fullScreenModal' }}
            />
          </>
        )}
      </RootStack.Navigator>
    </View>
  )
}
