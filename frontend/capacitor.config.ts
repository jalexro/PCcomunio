import type { CapacitorConfig } from '@capacitor/cli'

const config: CapacitorConfig = {
  appId: 'com.ligacomunio.app',
  appName: 'LigaComunio',
  webDir: 'dist',
  server: {
    // En desarrollo la API va por http, así que la app se sirve también por http (origen http://localhost,
    // ya permitido en el CORS del backend). Al publicar con HTTPS hay que volver al valor por defecto (https).
    androidScheme: 'http',
  },
}

export default config
