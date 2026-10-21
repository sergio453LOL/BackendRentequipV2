# RentEquip Mobile

App de React Native + Expo (SDK 58) + TypeScript para buscar maquinaria cerca de la obra, reservarla y documentar
la entrega y devolución con fotos.

## Cómo ejecutarla

1. Levanta el backend (`../`) y asegúrate de que el celular y la PC estén en la **misma red wifi**.
2. Copia `.env.example` a `.env` y pon la IP de tu PC (en Windows: `ipconfig`):
   ```
   EXPO_PUBLIC_API_URL=http://192.168.1.50:8080/api/v1
   ```
   `localhost` no sirve en un celular físico. Si Windows pregunta, permite el puerto 8080 en el firewall.
3. Instala y arranca:
   ```bash
   npm install
   npx expo start
   ```
4. Escanea el QR con **Expo Go** (Android) o con la cámara (iOS).

Para la demo con el backend desplegado, usa su URL pública con HTTPS en `EXPO_PUBLIC_API_URL`.

## Cómo cubre la rúbrica de Mobile

| Criterio | Implementación |
|---|---|
| 2.1 Integración con el backend | `services/api.ts`: axios con `baseURL`, `Authorization: Bearer`, refresh token automático en 401 y logout si falla. `services/authStorage.ts`: JWT en **expo-secure-store** (Keychain/Keystore). Usa GET, POST, PATCH y multipart. |
| 2.2 Arquitectura | `screens/`, `components/`, `services/`, `hooks/`, `contexts/`, `navigation/`, `types/`, `utils/`, `theme/`. Context API (`AuthContext`, `ToastContext`) y custom hooks (`useAsync`, `usePaginatedList`, `useCurrentLocation`, `useAction`, `useDebounce`). |
| 2.3 Sensores y API externa | **GPS** (`expo-location`): busca maquinaria cerca y guarda dónde se tomó cada foto. **Cámara** (`expo-camera`): fotos de entrega/devolución, con linterna y cámara frontal. **Mapa** (`react-native-maps`) con pines y "Buscar en esta zona". **API externa**: clima de la obra con **Open-Meteo**. Fallbacks: sin GPS se usa Lima; sin cámara se elige de la galería (`expo-image-picker`); permisos denegados ofrecen abrir Ajustes. |
| 2.4 Errores y carga | `ActivityIndicator` y pull-to-refresh, scroll infinito, mensajes en español por código HTTP, reintentos automáticos en errores de red/5xx, aviso de sin conexión (`@react-native-community/netinfo`), toasts de éxito/error, botones bloqueados durante las peticiones y validación con React Hook Form + Zod. |

## Navegación

- **AuthStack**: Login, Registro.
- **MainTabs**: Catálogo, Mapa, Mis reservas, Perfil.
- Encima de las pestañas: ficha de maquinaria, detalle de reserva y cámara (modal a pantalla completa).

## Comandos útiles

```bash
npx tsc --noEmit     # tipos
npx eslint .         # lint
npx expo-doctor      # dependencias y configuración
```
