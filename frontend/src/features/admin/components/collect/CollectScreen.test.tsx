import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { setupServer } from 'msw/node'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { afterAll, afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest'
import { Providers } from '@/app/providers'
import { CollectScreen } from '@/features/admin/components/collect/CollectScreen'
import { addDays, getTodayDateString } from '@/lib/dateUtils'
import { resetMockAdminState } from '@/mocks/adminHandlers'
import { handlers } from '@/mocks/handlers'

/*
 * 수집 실행·이력 화면 테스트(Task 029, SCR-16): 이력 표시, 수동 실행 202와 409 안내, 복구 확인 대화상자, 다시 실행,
 * 변경된 작품 펼침(한 번에 하나), 실패 사유 펼침, 비활성 범위, 오류·빈 상태를 본다. API는 앱의 목업 핸들러(MSW)다.
 * 기본 이력(오늘 기준): 5번 실행 중(오늘) / 4번 성공(어제) / 3번 중단됨(2일 전) / 2번 실패(3일 전) / 1번 성공(4일 전).
 * 표(md 이상)와 카드(좁은 화면)가 CSS로만 갈리므로 테스트(CSS 없음)에서는 둘 다 보인다. 그래서 표 안으로 범위를 좁혀 찾는다.
 */

const server = setupServer(...handlers)

beforeAll(() => {
  server.listen({ onUnhandledFrame: 'error' })
  // Radix 라디오 그룹(수집 범위)이 크기 감시(ResizeObserver)를 쓰는데 jsdom에는 없어 아무 일도 하지 않는 대역을 둔다
  vi.stubGlobal(
    'ResizeObserver',
    class {
      observe() {}
      unobserve() {}
      disconnect() {}
    },
  )
})
beforeEach(() => vi.stubEnv('VITE_USE_MOCK', 'true'))
afterEach(() => {
  server.resetHandlers()
  vi.unstubAllEnvs()
  resetMockAdminState()
  window.history.replaceState({}, '', '/')
})
afterAll(() => {
  server.close()
  vi.unstubAllGlobals()
})

const TODAY = getTodayDateString()

function renderCollect() {
  const router = createMemoryRouter([{ path: '/admin/collect', element: <CollectScreen /> }], {
    initialEntries: ['/admin/collect'],
  })
  render(
    <Providers>
      <RouterProvider router={router} />
    </Providers>,
  )
}

async function findTable() {
  return screen.findByRole('table', { name: '수집 실행 이력' })
}

// 표의 본문 행 중 실행 ID 칸이 일치하는 행(오류·펼침 줄 제외)
function runRow(table: HTMLElement, executionId: number) {
  const row = within(table)
    .getAllByRole('row')
    .find((r) => r.getAttribute('data-run-row') === String(executionId))
  if (!row) throw new Error(`실행 ${executionId} 행이 없습니다`)
  return row
}

// 대상 날짜를 바꿔 [수집 실행]을 누른다
async function submitRun(user: ReturnType<typeof userEvent.setup>, date: string) {
  // type="date"는 jsdom에서 user.type으로 값이 채워지지 않아 fireEvent.change로 값을 넣는다
  fireEvent.change(screen.getByLabelText('대상 날짜'), { target: { value: date } })
  await user.click(screen.getByRole('button', { name: '수집 실행' }))
}

describe('이력', () => {
  it('실행 5건과 상태 배지 4종, 마지막 성공 수집 한 줄이 보인다', async () => {
    renderCollect()
    const table = await findTable()

    const rows = within(table)
      .getAllByRole('row')
      .filter((r) => r.hasAttribute('data-run-row'))
    expect(rows.map((r) => r.getAttribute('data-run-row'))).toEqual(['5', '4', '3', '2', '1']) // 최신 순
    expect(within(runRow(table, 5)).getByText('실행 중')).toBeInTheDocument()
    expect(within(runRow(table, 4)).getByText('성공')).toBeInTheDocument()
    expect(within(runRow(table, 3)).getByText('중단됨')).toBeInTheDocument()
    expect(within(runRow(table, 2)).getByText('실패')).toBeInTheDocument()
    expect(screen.getByText(/^마지막 성공 수집:/)).toBeInTheDocument()
  })

  it('범위 DAILY·WEEKLY는 보이되 고를 수 없고 "2단계부터" 태그가 붙는다', async () => {
    renderCollect()
    await findTable()
    expect(screen.getByRole('radio', { name: '전체(ALL)' })).toBeChecked()
    expect(screen.getByRole('radio', { name: '매일 갱신(DAILY)' })).toBeDisabled()
    expect(screen.getByRole('radio', { name: '주간 갱신(WEEKLY)' })).toBeDisabled()
    expect(screen.getAllByText('2단계부터')).toHaveLength(2)
  })
})

describe('수동 실행', () => {
  it('202로 접수되면 안내 띠가 나오고 이력 맨 위에 "실행 중" 새 행이 생긴다', async () => {
    const user = userEvent.setup()
    renderCollect()
    const table = await findTable()

    await submitRun(user, addDays(TODAY, -5))

    expect(await screen.findByText(/수집을 접수했습니다\. 실행 ID 6/)).toBeInTheDocument()
    await waitFor(() => expect(within(runRow(table, 6)).getByText('실행 중')).toBeInTheDocument())
    // 새 실행 ID는 5 다음인 6이다. 대상 날짜가 가장 오래돼서(5일 전) 이력 맨 아래에 놓인다
    const ids = within(table)
      .getAllByRole('row')
      .filter((r) => r.hasAttribute('data-run-row'))
      .map((r) => r.getAttribute('data-run-row'))
    expect(ids).toEqual(['5', '4', '3', '2', '1', '6'])
  })

  it('접수한 실행은 이력을 한 번 더 불러오면([새로 고침]) 성공으로 바뀐다', async () => {
    const user = userEvent.setup()
    renderCollect()
    const table = await findTable()
    await submitRun(user, addDays(TODAY, -5))
    await waitFor(() => expect(within(runRow(table, 6)).getByText('실행 중')).toBeInTheDocument())

    await user.click(screen.getByRole('button', { name: '새로 고침' }))

    await waitFor(() => expect(within(runRow(table, 6)).getByText('성공')).toBeInTheDocument())
  })

  it('같은 날짜·범위가 실행 중이면 오류가 아니라 안내 띠(409)가 나온다', async () => {
    const user = userEvent.setup()
    renderCollect()
    await findTable()

    await submitRun(user, TODAY) // 5번이 오늘 ALL로 실행 중

    expect(
      await screen.findByText(/이미 실행 중입니다\. 끝난 뒤 이력에서 결과를 확인하세요/),
    ).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '실행 중인 이력 보기' })).toBeInTheDocument()
  })

  it('이미 성공한 날짜면 "다시 수집하지 않습니다" 안내가 나온다', async () => {
    const user = userEvent.setup()
    renderCollect()
    await findTable()

    await submitRun(user, addDays(TODAY, -1)) // 4번이 어제 ALL로 성공

    expect(
      await screen.findByText(/수집은 이미 성공했습니다\. 같은 날짜는 다시 수집하지 않습니다/),
    ).toBeInTheDocument()
  })

  it('중단된 실행이 있는 날짜는 먼저 복구하라는 안내가 나온다', async () => {
    const user = userEvent.setup()
    renderCollect()
    await findTable()

    await submitRun(user, addDays(TODAY, -2)) // 3번이 중단됨

    expect(await screen.findByText(/\[복구\]를 먼저 실행한 뒤 다시 실행하세요/)).toBeInTheDocument()
  })

  it('대상 날짜가 비어 있으면 요청하지 않고 오류 문구가 보인다', async () => {
    const user = userEvent.setup()
    renderCollect()
    await findTable()

    await submitRun(user, '')

    expect(screen.getByText('대상 날짜를 입력해 주세요.')).toBeInTheDocument()
    expect(screen.getByLabelText('대상 날짜')).toHaveAttribute('aria-invalid', 'true')
  })

  it('?mockScenario=run-error면 실행만 실패하고 오류 문장이 보인다', async () => {
    window.history.replaceState({}, '', '/?mockScenario=run-error')
    const user = userEvent.setup()
    renderCollect()
    const table = await findTable()
    expect(runRow(table, 4)).toBeInTheDocument() // 이력은 정상

    await submitRun(user, addDays(TODAY, -5))

    expect(await screen.findByRole('alert')).toHaveTextContent('일시적인 오류가 발생했습니다')
  })
})

describe('복구와 다시 실행', () => {
  it('[복구]는 확인 대화상자를 거치고 처음 포커스는 [취소]이며, Esc로 닫으면 [복구] 버튼으로 돌아온다', async () => {
    const user = userEvent.setup()
    renderCollect()
    const table = await findTable()

    const recoverButton = within(runRow(table, 3)).getByRole('button', { name: /^복구/ })
    await user.click(recoverButton)
    const dialog = await screen.findByRole('alertdialog', { name: '중단된 실행을 복구할까요?' })
    expect(within(dialog).getByRole('button', { name: '취소' })).toHaveFocus()
    expect(dialog).toHaveTextContent('아직 실제로 돌고 있는 실행이라면 복구하지 마세요')

    await user.keyboard('{Escape}')
    await waitFor(() => expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument())
    await waitFor(() => expect(recoverButton).toHaveFocus())
    expect(within(runRow(table, 3)).getByText('중단됨')).toBeInTheDocument() // 아무것도 바뀌지 않았다
  })

  it('복구하면 "실패"가 되고 [다시 실행]이 나타나며, 다시 실행하면 새 "실행 중" 행이 생긴다', async () => {
    const user = userEvent.setup()
    renderCollect()
    const table = await findTable()

    await user.click(within(runRow(table, 3)).getByRole('button', { name: /^복구/ }))
    const dialog = await screen.findByRole('alertdialog')
    await user.click(within(dialog).getByRole('button', { name: '복구' }))

    await waitFor(() => expect(within(runRow(table, 3)).getByText('실패')).toBeInTheDocument())
    // 복구한 실행은 종료 시각을 기록하지 않아 종료(소요) 칸이 "—"다(실행 중의 "진행 중"과 글자가 다르다)
    expect(within(runRow(table, 3)).getAllByRole('cell')[4]).toHaveTextContent('—')
    expect(
      within(runRow(table, 3)).queryByRole('button', { name: /^복구/ }),
    ).not.toBeInTheDocument()
    const restartButton = within(runRow(table, 3)).getByRole('button', { name: /^다시 실행/ })

    await user.click(restartButton)

    expect(await screen.findByText(/수집을 접수했습니다\. 실행 ID 6/)).toBeInTheDocument()
    await waitFor(() => expect(within(runRow(table, 6)).getByText('실행 중')).toBeInTheDocument())
    // 다시 실행은 원래 실행(3번) 바로 위에 붙는다: 대상 날짜 내림차순 → 같은 날짜는 실행 ID 내림차순
    const ids = within(table)
      .getAllByRole('row')
      .filter((r) => r.hasAttribute('data-run-row'))
      .map((r) => r.getAttribute('data-run-row'))
    expect(ids).toEqual(['5', '4', '6', '3', '2', '1'])
  })

  it('실패한 실행에는 [다시 실행]이 있고 실패 사유를 전체 보기로 펼칠 수 있다', async () => {
    const user = userEvent.setup()
    renderCollect()
    const table = await findTable()

    expect(within(runRow(table, 2)).getByRole('button', { name: /^다시 실행/ })).toBeInTheDocument()
    expect(
      within(runRow(table, 4)).queryByRole('button', { name: /^다시 실행/ }),
    ).not.toBeInTheDocument()

    const reasonToggle = within(table).getByRole('button', { name: /^전체 보기/ })
    expect(reasonToggle).toHaveAttribute('aria-expanded', 'false')
    expect(within(table).getByText(/TMDB 응답 지연/)).toHaveClass('line-clamp-2')
    await user.click(reasonToggle)
    expect(reasonToggle).toHaveAttribute('aria-expanded', 'true')
    expect(within(table).getByText(/TMDB 응답 지연/)).not.toHaveClass('line-clamp-2')
  })
})

describe('변경된 작품 펼침', () => {
  it('성공한 행을 펼치면 신규 글자와 이전 → 이후 상태, 2단계부터 제공 자리가 보이고 한 번에 하나만 열린다', async () => {
    const user = userEvent.setup()
    renderCollect()
    const table = await findTable()

    const toggle4 = within(runRow(table, 4)).getByRole('button', { name: /^변경 6건/ })
    await user.click(toggle4)
    expect(toggle4).toHaveAttribute('aria-expanded', 'true')
    expect(await within(table).findByText('기생충')).toBeInTheDocument()
    expect(within(table).getAllByText('신규').length).toBeGreaterThan(0) // 이전 상태가 없는 작품
    expect(within(table).getAllByText('2단계부터 제공')).toHaveLength(2)

    const toggle1 = within(runRow(table, 1)).getByRole('button', { name: /^변경 3건/ })
    await user.click(toggle1)
    expect(toggle1).toHaveAttribute('aria-expanded', 'true')
    expect(toggle4).toHaveAttribute('aria-expanded', 'false') // 앞에 열린 것은 닫힌다
    expect(within(table).queryByText('기생충 ')).not.toBeInTheDocument()
    expect(within(table).getAllByText('2단계부터 제공')).toHaveLength(2)
  })

  it('실행 중인 행을 펼치면 끝나면 보인다는 안내만 나온다', async () => {
    const user = userEvent.setup()
    renderCollect()
    const table = await findTable()

    await user.click(within(runRow(table, 5)).getByRole('button', { name: /^변경 12건/ }))

    expect(within(table).getByText('실행이 끝나면 변경된 작품이 보입니다.')).toBeInTheDocument()
  })
})

describe('빈 상태·오류', () => {
  it('?mockScenario=empty면 빈 상태 안내와 "아직 성공한 수집이 없습니다"가 보인다', async () => {
    window.history.replaceState({}, '', '/?mockScenario=empty')
    renderCollect()
    expect(await screen.findByText('아직 수집 실행 이력이 없습니다')).toBeInTheDocument()
    expect(screen.getByText('아직 성공한 수집이 없습니다.')).toBeInTheDocument()
  })

  it('?mockScenario=error면 오류 안내와 다시 시도 버튼이 보인다', async () => {
    window.history.replaceState({}, '', '/?mockScenario=error')
    renderCollect()
    expect(
      await screen.findByText('불러오지 못했습니다', {}, { timeout: 8000 }),
    ).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '다시 시도' })).toBeInTheDocument()
  }, 15000)
})
