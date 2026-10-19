import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { useState } from 'react'
import { describe, expect, it } from 'vitest'
import type { WishlistPriority } from '@/components/common/displayTypes'
import { WishlistButton } from '@/components/common/WishlistButton'

// 찜 해제 요청이 "요청 중(버튼 disabled) → 끝나면 찜하기 상태"로 가는 흐름을 흉내 내는 테스트용 틀
function Harness() {
  const [priority, setPriority] = useState<WishlistPriority | null>('WANT')
  const [isPending, setIsPending] = useState(false)
  return (
    <WishlistButton
      targetName="시즌 3"
      priority={priority}
      isPending={isPending}
      onAdd={() => {}}
      onChangePriority={() => {}}
      onRemove={() => {
        setIsPending(true)
        setTimeout(() => {
          setPriority(null)
          setIsPending(false)
        }, 20)
      }}
    />
  )
}

describe('WishlistButton 키보드 포커스', () => {
  it('설정 상자에서 찜 해제를 누르면 요청이 끝난 뒤 포커스가 찜하기 버튼으로 돌아온다', async () => {
    const user = userEvent.setup()
    render(<Harness />)

    // 1. 상자를 열고 찜 해제를 누른다(누른 버튼은 상자와 함께 사라진다)
    await user.click(screen.getByRole('button', { name: '시즌 3 찜함 · 보고 싶음' }))
    await user.click(screen.getByRole('button', { name: /찜 해제/ }))

    // 2. 요청이 끝나 "찜하기" 버튼이 되면 포커스가 거기에 있다(문서 맨 앞으로 날아가지 않는다)
    await waitFor(() => expect(screen.getByRole('button', { name: '시즌 3 찜하기' })).toHaveFocus())
  })
})
