import '@testing-library/jest-dom/vitest' // toBeInTheDocument 같은 DOM 매처를 expect에 등록한다
import { cleanup } from '@testing-library/react'
import { afterEach } from 'vitest'

// RTL은 전역 afterEach가 있을 때만 자동 정리하는데, globals를 켜지 않았으므로 직접 연결한다.
// 정리하지 않으면 앞 테스트에서 렌더한 DOM이 남아 다음 테스트의 조회 결과가 섞인다.
afterEach(() => {
  cleanup()
})
