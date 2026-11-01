import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'
import pkg from './package.json' with { type: 'json' }

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  // shown on the Settings > About tab; the only value taken from package.json at build time
  define: { __APP_VERSION__: JSON.stringify(pkg.version) },
})
