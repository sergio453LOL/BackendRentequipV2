import type { NavigatorScreenParams } from '@react-navigation/native'
import type { NativeStackNavigationProp, NativeStackScreenProps } from '@react-navigation/native-stack'
import type { HandoverStage, ReservationRole } from '../types/api'

export type AuthStackParamList = {
  Login: undefined
  Register: undefined
}

export type MainTabParamList = {
  Catalog: undefined
  Map: undefined
  Reservations: { role?: ReservationRole } | undefined
  Profile: undefined
}

export type RootStackParamList = {
  Auth: NavigatorScreenParams<AuthStackParamList>
  Main: NavigatorScreenParams<MainTabParamList>
  EquipmentDetail: { equipmentId: number; name?: string }
  ReservationDetail: { reservationId: number }
  HandoverCamera: { reservationId: number; stage: HandoverStage }
}

export type RootScreenProps<Screen extends keyof RootStackParamList> = NativeStackScreenProps<RootStackParamList, Screen>
export type AuthScreenProps<Screen extends keyof AuthStackParamList> = NativeStackScreenProps<AuthStackParamList, Screen>

/** Tabs push detail screens that live in the root stack, so they navigate with the root navigator type. */
export type RootNavigation = NativeStackNavigationProp<RootStackParamList>
