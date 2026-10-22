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

Si el QR no conecta (otra red o firewall), usa `npm run start:tunnel`: Expo crea un túnel público.

## Con el backend desplegado

1. En `.env` pon la URL HTTPS que imprime `deploy/deploy-aws.sh`:
   `EXPO_PUBLIC_API_URL=https://3-237-198-235.sslip.io/api/v1`
2. Reinicia con `npx expo start -c`: las variables `EXPO_PUBLIC_*` se insertan al empaquetar y `-c`
   limpia la caché para que tome la nueva.
3. Escanea el QR con Expo Go. Ya funciona con cualquier red, porque la API es pública.

## Publicar un APK (opcional, EAS)

`eas.json` trae el perfil `preview`, que genera un APK instalable para compartir el enlace:

```bash
npm install -g eas-cli
eas login
eas init                  # vincula el proyecto a tu cuenta de Expo
eas env:create --name EXPO_PUBLIC_API_URL --value https://<ip-con-guiones>.sslip.io/api/v1 --environment preview --visibility plaintext
eas env:create --name GOOGLE_MAPS_API_KEY --value <tu-clave> --environment preview --visibility secret
eas build -p android --profile preview
```

En Expo Go el mapa funciona sin clave. En un APK, `react-native-maps` necesita
`GOOGLE_MAPS_API_KEY` (Maps SDK for Android en Google Cloud); `app.config.ts` la toma del entorno,
así que la clave nunca se guarda en el repositorio.

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
