import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router'
import { describe, expect, it, vi } from 'vitest'
import { WithdrawScreen } from '@/features/auth/components/WithdrawScreen'

/*
 * 회원 탈퇴 화면(SCR-13) 테스트(Task 081). 서버·이동은 이 화면의 일이 아니라 onWithdraw 콜백으로 넘기므로 콜백이 불리는지만 본다.
 * 확인하는 것: 동의 체크 전에는 [탈퇴하기]가 잠겨 있고, 체크 → 확인 대화상자 → [탈퇴]를 눌러야 콜백이 불리는지,
 * 요청 중(isPending)에는 버튼이 잠기고 대화상자를 닫을 수 없는지, 실패 안내(errorMessage)가 대화상자 안에 보이는지.
 * 막으려는 사고: 체크나 확인 없이 탈퇴 요청이 나가는 것(되돌릴 수 없는 삭제), 요청 도중 대화상자가 닫혀 결과를 못 보는 것.
 */

function renderScreen(props: Partial<React.ComponentProps<typeof WithdrawScreen>> = {}) {
  const onWithdraw = props.onWithdraw ?? vi.fn()
  render(
    <MemoryRouter>
      <WithdrawScreen onWithdraw={onWithdraw} {...props} />
    </MemoryRouter>,
  )
  return { onWithdraw }
}

// 동의에 체크하고 [탈퇴하기]를 눌러 확인 대화상자를 연다
async function openConfirmDialog(user: ReturnType<typeof userEvent.setup>) {
  await user.click(screen.getByRole('checkbox', { name: /탈퇴에 동의해요/ }))
  await user.click(screen.getByRole('button', { name: '탈퇴하기' }))
  return screen.findByRole('alertdialog')
}

describe('회원 탈퇴 화면', () => {
  it('삭제되는 데이터 목록을 보여 주고, 동의 체크 전에는 [탈퇴하기]가 잠겨 있다', () => {
    renderScreen()

    const list = screen.getByRole('list', { name: '삭제되는 데이터' })
    expect(within(list).getAllByRole('listitem')).toHaveLength(4)
    expect(screen.getByRole('button', { name: '탈퇴하기' })).toBeDisabled()
  })

  it('체크하면 [탈퇴하기]가 열리고, 대화상자에서 [탈퇴]를 눌러야 콜백이 불린다', async () => {
    const user = userEvent.setup()
    const { onWithdraw } = renderScreen()

    const dialog = await openConfirmDialog(user)
    // 대화상자만 열린 단계에서는 아직 탈퇴 요청이 나가지 않는다
    expect(onWithdraw).not.toHaveBeenCalled()

    await user.click(within(dialog).getByRole('button', { name: '탈퇴' }))
    expect(onWithdraw).toHaveBeenCalledTimes(1)
  })

  it('대화상자의 [취소]를 누르면 콜백 없이 닫힌다', async () => {
    const user = userEvent.setup()
    const { onWithdraw } = renderScreen()

    const dialog = await openConfirmDialog(user)
    await user.click(within(dialog).getByRole('button', { name: '취소' }))

    expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument()
    expect(onWithdraw).not.toHaveBeenCalled()
  })

  it('요청 중(isPending)이면 버튼이 잠기고 Esc로도 대화상자가 닫히지 않는다', async () => {
    const user = userEvent.setup()
    // 처음에는 요청 전 상태로 대화상자를 연 뒤, 같은 화면을 요청 중 상태로 다시 그린다
    const onWithdraw = vi.fn()
    const view = render(
      <MemoryRouter>
        <WithdrawScreen onWithdraw={onWithdraw} />
      </MemoryRouter>,
    )
    const dialog = await openConfirmDialog(user)

    view.rerender(
      <MemoryRouter>
        <WithdrawScreen onWithdraw={onWithdraw} isPending />
      </MemoryRouter>,
    )

    const confirmButton = within(dialog).getByRole('button', { name: '탈퇴하는 중…' })
    expect(confirmButton).toBeDisabled()
    expect(within(dialog).getByRole('button', { name: '취소' })).toBeDisabled()
    await user.keyboard('{Escape}')
    expect(screen.getByRole('alertdialog')).toBeInTheDocument()
  })

  it('실패 안내(errorMessage)가 대화상자 안에 보인다', async () => {
    const user = userEvent.setup()
    const view = render(
      <MemoryRouter>
        <WithdrawScreen onWithdraw={vi.fn()} />
      </MemoryRouter>,
    )
    const dialog = await openConfirmDialog(user)
    expect(within(dialog).queryByRole('alert')).not.toBeInTheDocument()

    view.rerender(
      <MemoryRouter>
        <WithdrawScreen onWithdraw={vi.fn()} errorMessage="일시적인 오류가 발생했습니다." />
      </MemoryRouter>,
    )

    expect(within(dialog).getByRole('alert')).toHaveTextContent('일시적인 오류가 발생했습니다.')
  })
})
