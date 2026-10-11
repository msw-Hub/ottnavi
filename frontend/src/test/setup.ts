import '@testing-library/jest-dom/vitest' // toBeInTheDocument 같은 DOM 매처를 expect에 등록한다
import { cleanup } from '@testing-library/react'
import { afterEach } from 'vitest'

// RTL은 전역 afterEach가 있을 때만 자동 정리하는데, globals를 켜지 않았으므로 직접 연결한다.
// 정리하지 않으면 앞 테스트에서 렌더한 DOM이 남아 다음 테스트의 조회 결과가 섞인다.
afterEach(() => {
  cleanup()
})

// jsdom에는 ResizeObserver가 없다. Radix 라디오 그룹처럼 크기 감시를 쓰는 부품(수집 범위 선택 등)을 그리는 화면이
// 라우터 테스트 등에서 "ResizeObserver is not defined"로 죽지 않도록, 아무 일도 하지 않는 대역을 전역에 둔다.
if (typeof globalThis.ResizeObserver === 'undefined') {
  globalThis.ResizeObserver = class {
    observe() {}
    unobserve() {}
    disconnect() {}
  }
}
