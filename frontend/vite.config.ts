import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  // Root worker URL permits scope '/' in production without widening scope headers.
  worker: { rollupOptions: { output: { entryFileNames: 'firebase-messaging-sw-[hash].js' } } },
  server: {
    host: '127.0.0.1',
    port: 5173,
    strictPort: true,
    headers: { 'Service-Worker-Allowed': '/' },
    proxy: { '/api': 'http://127.0.0.1:8080' },
  },
})
