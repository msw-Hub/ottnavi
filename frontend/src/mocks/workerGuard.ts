/*
 * MSW 서비스 워커가 페이지의 요청을 계속 가로채도록 지키는 개발용 도우미다(Task 028 보정).
 *
 * 문제: 목업 모드에서 워커가 요청을 못 가로채면 /api 요청이 Vite 프록시로 새어 나가 실제 백엔드(localhost:8080)로 간다.
 * 백엔드를 켜지 않았으면 터미널에 "[vite] http proxy error: /api/me/... ECONNREFUSED"가 반복되고 화면은 오류가 된다.
 * 새는 경로는 두 가지다.
 * ① 강력 새로고침(Ctrl+Shift+R, Ctrl+F5): 브라우저가 이 새로고침에서는 서비스 워커를 건너뛰어, 페이지가 워커의 통제 밖으로 로드된다
 *    (navigator.serviceWorker.controller가 null). 워커를 시작해도 그 페이지가 닫힐 때까지 요청이 워커를 거치지 않는다.
 * ② 워커가 브라우저에 의해 멈췄다 다시 켜진 경우(탭을 오래 백그라운드에 둔 뒤 등): 워커는 "목업을 쓰는 창" 목록을 메모리에만 들고 있어
 *    다시 켜지면 목록이 비고, 목록이 비어 있으면 모든 요청을 통과시킨다. 탭이 백그라운드면 MSW의 생존 신호(5초마다)도 느려져 이 일이 잘 생긴다.
 */

// 한 번만 다시 불러오기 위한 표시(sessionStorage). 다시 불러와도 통제되지 않는 환경에서 무한 새로고침이 되는 것을 막는다
const RELOAD_FLAG_KEY = 'ottnavi:mock-worker-reloaded'

/**
 * 워커가 이 페이지를 통제하는지 확인하고, 아니면 한 번 다시 불러온다. 다시 불러오기를 시작했으면 true를 돌려준다.
 * (true면 호출한 쪽은 화면을 그리지 않고 끝낸다. 어차피 곧 페이지가 새로 로드된다)
 *
 * 처음 방문이면 워커가 막 등록돼 통제권을 가져오는 중일 수 있어 최대 1초 기다린다(clients.claim 직후 controllerchange 이벤트가 온다).
 */
export async function reloadIfWorkerNotControlling(): Promise<boolean> {
  if (!('serviceWorker' in navigator)) return false
  if (navigator.serviceWorker.controller) {
    clearReloadFlag()
    return false
  }

  await new Promise<void>((resolve) => {
    const timer = window.setTimeout(resolve, 1000)
    navigator.serviceWorker.addEventListener(
      'controllerchange',
      () => {
        window.clearTimeout(timer)
        resolve()
      },
      { once: true },
    )
  })
  if (navigator.serviceWorker.controller) {
    clearReloadFlag()
    return false
  }

  // 아직도 통제권이 없다: 강력 새로고침으로 열린 페이지다. 일반 새로고침으로 한 번 다시 불러오면 워커가 통제한다
  if (readReloadFlag()) return false // 이미 한 번 다시 불러왔다면 더 시도하지 않는다
  writeReloadFlag(true)
  window.location.reload()
  return true
}

/**
 * 화면이 다시 보이거나 창이 포커스를 받을 때마다 워커에 "이 창은 목업을 쓴다"고 다시 알린다(②번 대응).
 * 워커가 이미 알고 있으면 아무 일도 일어나지 않는 동작이라 여러 번 보내도 안전하다.
 */
export function keepWorkerActive(): void {
  if (!('serviceWorker' in navigator)) return

  function notifyWorker() {
    navigator.serviceWorker.controller?.postMessage('MOCK_ACTIVATE')
  }

  document.addEventListener('visibilitychange', () => {
    if (document.visibilityState === 'visible') notifyWorker()
  })
  window.addEventListener('focus', notifyWorker)
  window.addEventListener('pageshow', notifyWorker)
}

function clearReloadFlag() {
  writeReloadFlag(false)
}

function readReloadFlag(): boolean {
  try {
    return window.sessionStorage.getItem(RELOAD_FLAG_KEY) === 'true'
  } catch {
    return true // 저장소를 못 쓰면 표시를 남길 수 없어 무한 새로고침 위험이 있으므로 다시 불러오지 않는다
  }
}

function writeReloadFlag(isSet: boolean) {
  try {
    if (isSet) window.sessionStorage.setItem(RELOAD_FLAG_KEY, 'true')
    else window.sessionStorage.removeItem(RELOAD_FLAG_KEY)
  } catch {
    // 저장소를 쓸 수 없으면 건너뛴다
  }
}
