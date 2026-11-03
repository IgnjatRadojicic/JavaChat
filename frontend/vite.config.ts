import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// In development the browser only talks to Vite (port 5173). Vite forwards
// backend paths to Spring Boot (port 8080), so cookies, the session and the
// CSRF token all stay on one origin, exactly like in production.
// The Host header is kept as localhost:5173, so Spring builds Google's
// redirect URI for 5173 and the whole login flow stays on this port.
// changeOrigin and xfwd are written out on purpose: without them Spring saw
// localhost:8080 and sent Google a redirect URI for 8080 instead of 5173.
const backend = {
  target: 'http://localhost:8080',
  changeOrigin: false,
  xfwd: true,
}

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    strictPort: true,
    proxy: {
      '/api': backend,
      '/oauth2': backend,
      '/login': backend,
      '/logout': backend,
    },
  },
})
