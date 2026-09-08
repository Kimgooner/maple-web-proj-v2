/// <reference types="vitest/config" />
import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// 개발: /api 를 로컬 Spring(8080) 으로 넘긴다.
// 빌드: dist/ 로 낸다. nginx 이미지가 이걸 담아 루트에서 서빙한다.
export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      '/api': { target: 'http://localhost:8080', changeOrigin: true },
    },
  },
  test: {
    environment: 'node',
    include: ['src/**/*.test.ts'],
  },
})
