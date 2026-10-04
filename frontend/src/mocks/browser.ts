import { setupWorker } from 'msw/browser'
import { handlers } from './handlers'

// 브라우저용 MSW 워커. public/mockServiceWorker.js(npx msw init으로 생성)를 서비스 워커로 등록한다.
export const worker = setupWorker(...handlers)
