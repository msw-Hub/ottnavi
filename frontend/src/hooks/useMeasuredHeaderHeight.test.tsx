import { render } from '@testing-library/react'
import { useRef } from 'react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { MEASURED_HEADER_HEIGHT_VAR, useMeasuredHeaderHeight } from './useMeasuredHeaderHeight'

// 테스트용 머리글. offsetHeight는 jsdom이 항상 0을 주므로 값을 직접 정해 준다
function TestHeader({ height }: { height: number }) {
  const ref = useRef<HTMLElement>(null)
  useMeasuredHeaderHeight(ref)
  return (
    <header
      ref={(node) => {
        ref.current = node
        if (node) Object.defineProperty(node, 'offsetHeight', { value: height, configurable: true })
      }}
    />
  )
}

describe('useMeasuredHeaderHeight', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('머리글 높이를 CSS 변수에 쓰고, 크기가 바뀌면 다시 쓰며, 언마운트하면 지운다', () => {
    // 1. 크기 변화 콜백을 붙잡아 두는 ResizeObserver 대역
    let notify: () => void = () => {}
    vi.stubGlobal(
      'ResizeObserver',
      class {
        constructor(callback: () => void) {
          notify = callback
        }
        observe() {}
        unobserve() {}
        disconnect() {}
      },
    )
    const root = document.documentElement

    // 2. 처음 그릴 때 현재 높이가 변수에 들어간다
    const { container, unmount } = render(<TestHeader height={131} />)
    expect(root.style.getPropertyValue(MEASURED_HEADER_HEIGHT_VAR)).toBe('131px')

    // 3. 줄이 줄어(세 줄 → 두 줄) 높이가 바뀌면 변수도 따라간다
    Object.defineProperty(container.querySelector('header'), 'offsetHeight', { value: 99 })
    notify()
    expect(root.style.getPropertyValue(MEASURED_HEADER_HEIGHT_VAR)).toBe('99px')

    // 4. 언마운트하면 변수를 지워 토스트가 기본값으로 돌아간다
    unmount()
    expect(root.style.getPropertyValue(MEASURED_HEADER_HEIGHT_VAR)).toBe('')
  })
})
