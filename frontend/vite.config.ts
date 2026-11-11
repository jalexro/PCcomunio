import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

const backend = process.env.BACKEND_URL ?? 'http://localhost:8080'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    host: true,
    port: 5173,
    // En Docker sobre Windows los cambios de archivos no llegan como eventos: hay que sondear.
    watch: { usePolling: true },
    proxy: {
      '/api': backend,
      '/actuator': backend,
    },
  },
})
