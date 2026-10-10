import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
// react-router/dom의 RouterProvider는 react-dom의 flushSync를 자동으로 연결해 준다(React Router 문서 RouterProvider 항목)
import { RouterProvider } from 'react-router/dom'
import { Providers } from '@/app/providers'
import { createAppRouter } from '@/app/router'
import './index.css'

// 목업 모드(VITE_USE_MOCK=true)에서만 MSW 워커를 켠다.
// 워커가 준비되기 전에 렌더링하면 첫 API 요청이 목업을 거치지 않고 실제 서버로 나가므로,
// start()를 await한 뒤 렌더링한다. 동적 import라 목업 모드가 아니면 msw 코드가 번들에 들어가지 않는다.
// 반환값: true면 페이지를 다시 불러오는 중이라 화면을 그리지 않는다
async function enableMocking(): Promise<boolean> {
  if (import.meta.env.VITE_USE_MOCK !== 'true') return false
  const { worker } = await import('./mocks/browser')
  // 핸들러가 없는 요청은 그대로 통과시킨다. msw 3부터 옵션 이름이 onUnhandledRequest에서 onUnhandledFrame으로 바뀌었다
  await worker.start({ onUnhandledFrame: 'bypass' })
  // 강력 새로고침 등으로 워커가 이 페이지를 통제하지 못하면 요청이 실제 서버(Vite 프록시)로 샌다. 그러면 한 번 다시 불러온다(mocks/workerGuard.ts)
  const { reloadIfWorkerNotControlling, keepWorkerActive } = await import('./mocks/workerGuard')
  if (await reloadIfWorkerNotControlling()) return true
  keepWorkerActive()
  return false
}

enableMocking().then((isReloading) => {
  if (isReloading) return

  // 라우터는 목업 준비가 끝난 뒤, React 렌더링 바깥에서 한 번만 만든다(렌더링마다 새로 만들면 화면 상태가 초기화된다)
  const router = createAppRouter()

  createRoot(document.getElementById('root')!).render(
    <StrictMode>
      <Providers>
        <RouterProvider router={router} />
      </Providers>
    </StrictMode>,
  )
})
