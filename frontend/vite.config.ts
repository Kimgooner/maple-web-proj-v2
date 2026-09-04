/// <reference types="vitest/config" />
import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// 개발: /api 를 로컬 Spring(8080) 으로 넘긴다.
// 빌드: Spring 정적 경로 아래 /app 으로 낸다. 산출물은 gitignore 대상.
export default defineConfig({
  plugins: [react()],
  base: '/app/',
  server: {
    proxy: {
      '/api': { target: 'http://localhost:8080', changeOrigin: true },
    },
  },
  build: {
    outDir: '../src/main/resources/static/app',
    emptyOutDir: true,
  },
  test: {
    environment: 'node',
    include: ['src/**/*.test.ts'],
  },
})
