import js from '@eslint/js'
import globals from 'globals'
import reactHooks from 'eslint-plugin-react-hooks'
import reactRefresh from 'eslint-plugin-react-refresh'
import tseslint from 'typescript-eslint'
import prettier from 'eslint-config-prettier'
import { defineConfig, globalIgnores } from 'eslint/config'

export default defineConfig([
  // dist: 빌드 산출물, mockServiceWorker.js: MSW CLI가 생성·갱신하는 파일이라 직접 린트하지 않는다
  globalIgnores(['dist', 'public/mockServiceWorker.js']),
  {
    files: ['**/*.{ts,tsx}'],
    extends: [
      js.configs.recommended,
      tseslint.configs.recommended,
      reactHooks.configs.flat.recommended,
      reactRefresh.configs.vite,
    ],
    languageOptions: {
      globals: globals.browser,
    },
  },
  {
    // shadcn/ui 원본은 수정하지 않는다(frontend.md). button.tsx처럼 컴포넌트와 buttonVariants를 함께
    // export하는 원본이 fast refresh 규칙에 걸리므로, 이 폴더에서만 규칙을 끈다
    files: ['src/components/ui/**/*.{ts,tsx}'],
    rules: {
      'react-refresh/only-export-components': 'off',
    },
  },
  // 서식 규칙은 Prettier가 맡으므로 충돌하는 ESLint 규칙을 끈다. 다른 설정이 다시 켜지 않도록 맨 마지막에 둔다
  prettier,
])
