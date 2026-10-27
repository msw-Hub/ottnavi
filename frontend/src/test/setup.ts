import '@testing-library/jest-dom/vitest' // toBeInTheDocument 같은 DOM 매처를 expect에 등록한다
import { cleanup, configure } from '@testing-library/react'
import { afterEach } from 'vitest'

// findBy·waitFor의 기본 대기는 1초다. 전체 테스트를 병렬로 돌리면 컴퓨터가 바쁜 순간(lazy 화면 불러오기, 긴 입력 시뮬레이션)에
// 화면이 1초 안에 안 뜨는 일이 가끔 생겨 router·ProductsScreen 테스트가 불안정했다(실행 3번 중 1번 재현).
// 로직 문제가 아니라 대기 시간 문제라 5초로 늘린다. 통과하는 테스트의 속도는 달라지지 않는다(나타나면 바로 끝난다).
configure({ asyncUtilTimeout: 5000 })

// RTL은 전역 afterEach가 있을 때만 자동 정리하는데, globals를 켜지 않았으므로 직접 연결한다.
// 정리하지 않으면 앞 테스트에서 렌더한 DOM이 남아 다음 테스트의 조회 결과가 섞인다.
afterEach(() => {
  cleanup()
})

// jsdom은 window.scrollTo가 "구현되지 않음" 오류를 콘솔에 남긴다. 루트 레이아웃의 ScrollRestoration(화면을 옮길 때 맨 위로)이
// 모든 라우터 테스트에서 scrollTo를 부르므로, 아무 일도 하지 않는 대역으로 바꿔 경고가 쌓이지 않게 한다.
window.scrollTo = () => {}

// jsdom에는 ResizeObserver가 없다. Radix 라디오 그룹처럼 크기 감시를 쓰는 부품(수집 범위 선택 등)을 그리는 화면이
// 라우터 테스트 등에서 "ResizeObserver is not defined"로 죽지 않도록, 아무 일도 하지 않는 대역을 전역에 둔다.
if (typeof globalThis.ResizeObserver === 'undefined') {
  globalThis.ResizeObserver = class {
    observe() {}
    unobserve() {}
    disconnect() {}
  }
}
