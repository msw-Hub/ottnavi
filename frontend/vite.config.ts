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
    // 기본 5초는 화면 테스트 파일을 병렬로 돌릴 때 부족하다. 관리자 상품 화면처럼 입력 단계가 많은 테스트가 PC 부하에 따라
    // 5초를 넘겨 들쭉날쭉 실패했고(2026-10-27, 변경 없는 코드에서도 재현), 단독 실행에서는 통과했다. 15초로 여유를 둔다.
    testTimeout: 15000,
  },
})
