import { NavigationContainer } from '@react-navigation/native'
import { StatusBar } from 'expo-status-bar'
import { SafeAreaProvider } from 'react-native-safe-area-context'
import { AuthProvider } from './src/contexts/AuthContext'
import { ToastProvider } from './src/contexts/ToastContext'
import { AppNavigator } from './src/navigation/AppNavigator'

export default function App() {
  return (
    <SafeAreaProvider>
      <ToastProvider>
        <AuthProvider>
          <NavigationContainer>
            <AppNavigator />
          </NavigationContainer>
        </AuthProvider>
      </ToastProvider>
      <StatusBar style="dark" />
    </SafeAreaProvider>
  )
}
