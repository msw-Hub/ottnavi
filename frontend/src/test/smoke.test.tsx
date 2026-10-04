import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { Button } from '@/components/ui/button'
import { cn } from '@/lib/utils'

// 테스트 환경 점검용 스모크 테스트: jsdom, RTL, jest-dom 매처, user-event, @ alias가 모두 동작하는지 확인한다.
describe('테스트 환경', () => {
  it('Button을 렌더하고 클릭 이벤트를 전달한다', async () => {
    const onClick = vi.fn()
    render(<Button onClick={onClick}>찜하기</Button>)

    await userEvent.click(screen.getByRole('button', { name: '찜하기' }))

    expect(screen.getByRole('button', { name: '찜하기' })).toBeInTheDocument()
    expect(onClick).toHaveBeenCalledTimes(1)
  })

  it('cn이 충돌하는 Tailwind 클래스를 뒤의 값으로 합친다', () => {
    expect(cn('px-2', 'px-4')).toBe('px-4')
  })
})
