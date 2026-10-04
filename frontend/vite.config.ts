import path from 'node:path'
import tailwindcss from '@tailwindcss/vite'
import react from '@vitejs/plugin-react'
import { defineConfig } from 'vitest/config'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react(), tailwindcss()],
  resolve: {
    alias: {
      // "@/..." 경로를 src 폴더로 연결한다 (tsconfig의 paths와 같은 값을 유지해야 한다)
      '@': path.resolve(import.meta.dirname, './src'),
    },
  },
  server: {
    proxy: {
      // 로컬 개발 시 /api 요청을 로컬 백엔드로 넘긴다(배포 환경은 Vercel rewrite가 같은 역할을 한다)
      '/api': 'http://localhost:8080',
    },
  },
  test: {
    environment: 'jsdom', // 컴포넌트 테스트에 브라우저 DOM이 필요하다
    setupFiles: ['./src/test/setup.ts'],
    // 테스트는 소스 옆(*.test.ts(x))이나 src/test에 둔다. 생성물·산출물은 대상에서 뺀다
    include: ['src/**/*.test.{ts,tsx}'],
  },
})
