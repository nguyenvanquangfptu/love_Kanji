import path from 'node:path'
import tailwindcss from '@tailwindcss/vite'
import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react(), tailwindcss()],
  resolve: {
    alias: {
      '@': path.resolve(import.meta.dirname, './src'),
    },
  },
  server: {
    proxy: {
      '/api': {
        // Ghi rõ IPv4: backend trong Docker chỉ mở cổng trên 127.0.0.1, còn "localhost" có thể phân giải ra ::1.
        target: 'http://127.0.0.1:8080',
        changeOrigin: true,
      },
    },
  },
})
