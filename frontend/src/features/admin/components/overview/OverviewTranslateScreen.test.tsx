import { act, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { setupServer } from 'msw/node'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { afterAll, afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest'
import { Providers } from '@/app/providers'
import { OverviewTranslateScreen } from '@/features/admin/components/overview/OverviewTranslateScreen'
import { handlers } from '@/mocks/handlers'
import { resetMockLlmState } from '@/mocks/llmHandlers'
import { resetMockSession, setMockRole } from '@/mocks/mockSession'

/*
 * 관리자 "LLM 줄거리 번역" 화면 테스트. API는 앱의 목업 핸들러(MSW, mocks/llmHandlers)다.
 * 확인하는 것: 현황·후보·탭 개수, 기능 꺼짐/일괄 실행 중에 버튼이 막히는지, 일괄 완료 전환, 중단 사유 안내,
 *   반영 확인 대화상자(표본 미확인 경고·요청 본문·연타), 부분 성공 결과, 되돌리기, 반영 충돌 안내, 데모 계정 자물쇠.
 * 막으려는 사고: 사람이 읽지 않은 번역이 경고 없이 이용자 화면에 나가는 것, 같은 반영이 두 번 나가는 것,
 *   번역 기능이 꺼졌는데 버튼이 눌리는 것, 데모 계정이 공용 데이터를 바꾸는 것.
 * 시나리오는 주소의 ?mockScenario= 값으로 고른다(목업 모드에서 요청 헤더로 실린다).
 */

const server = setupServer(...handlers)

beforeAll(() => {
  server.listen({ onUnhandledFrame: 'error' })
  // Radix 부품이 크기 감시(ResizeObserver)를 쓰는데 jsdom에는 없어 아무 일도 하지 않는 대역을 둔다
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
  server.events.removeAllListeners()
  vi.unstubAllEnvs()
  vi.useRealTimers()
  resetMockLlmState()
  resetMockSession()
  window.history.replaceState({}, '', '/')
})
afterAll(() => {
  server.close()
  vi.unstubAllGlobals()
})

function renderScreen(scenario?: string) {
  if (scenario) window.history.replaceState({}, '', `/?mockScenario=${scenario}`)
  const router = createMemoryRouter(
    [{ path: '/admin/llm', element: <OverviewTranslateScreen /> }],
    { initialEntries: ['/admin/llm'] },
  )
  render(
    <Providers>
      <RouterProvider router={router} />
    </Providers>,
  )
}

// 섹션(region)을 이름으로 찾는다
function region(name: string) {
  return screen.findByRole('region', { name })
}

// 쓰기 요청(POST·DELETE)의 경로와 본문을 기록한다
function recordWrites() {
  const writes: { method: string; path: string; body: string }[] = []
  server.events.on('request:start', ({ request }) => {
    if (request.method === 'GET') return
    const path = new URL(request.url).pathname
    void request
      .clone()
      .text()
      .then((body) => writes.push({ method: request.method, path, body }))
  })
  return writes
}

// 성공 초안 전부 반영 큰 버튼
function applyAllButton() {
  return screen.getByRole('button', { name: /성공 초안 전부 반영/ })
}

describe('정상 화면', () => {
  it('번역 현황, 번역 후보, 탭의 초안 개수가 보인다', async () => {
    renderScreen()
    const summary = await region('번역 현황')
    expect(await within(summary).findByText('2,512편')).toBeInTheDocument()
    expect(within(summary).getByText('37 / 400회')).toBeInTheDocument()
    expect(within(summary).getByText('12회')).toBeInTheDocument()
    expect(within(summary).getByText('gemini-2.5-flash')).toBeInTheDocument()
    expect(within(summary).getByText('Gemini(LLM)')).toBeInTheDocument()

    expect(await screen.findByRole('tab', { name: '성공 (30)' })).toBeInTheDocument()
    expect(screen.getByRole('tab', { name: '승인 대기 · 주의 (6)' })).toBeInTheDocument()
    expect(screen.getByRole('tab', { name: '실패 (5)' })).toBeInTheDocument()

    const candidates = await region('번역 후보')
    expect(within(candidates).getByText(/전체 2,512편/)).toBeInTheDocument()
    expect(within(candidates).getAllByText('기생충').length).toBeGreaterThan(0)
  })

  it('실패 탭에는 선택 체크박스가 없고, 실패 이유와 반영 불가 이유가 보인다', async () => {
    const user = userEvent.setup()
    renderScreen()
    await user.click(await screen.findByRole('tab', { name: '실패 (5)' }))

    const panel = await screen.findByRole('tabpanel', { name: '실패 (5)' })
    expect(
      await within(panel).findByText(/검사에 걸린 초안은 그대로 반영할 수 없습니다/),
    ).toBeInTheDocument()
    expect(within(panel).queryAllByRole('checkbox')).toHaveLength(0)
    expect(within(panel).getAllByText('실패').length).toBeGreaterThan(0)
    // 실패 초안의 [반영]은 막히고 이유가 글자로 붙는다(색·비활성 모양만으로 알리지 않는다)
    for (const button of within(panel).getAllByRole('button', { name: '반영' })) {
      expect(button).toBeDisabled()
    }
    expect(
      within(panel).getAllByText(/검사 실패가 남아 있어 반영할 수 없습니다/).length,
    ).toBeGreaterThan(0)
    expect(within(panel).getAllByRole('list', { name: '검사 실패 이유' })).toHaveLength(5)
  })

  it('조회가 실패하면 현황과 후보 상자에 오류와 다시 시도가 보인다', async () => {
    renderScreen('error')
    const summary = await region('번역 현황')
    expect(await within(summary).findByRole('button', { name: '다시 시도' })).toBeInTheDocument()
  })
})

describe('번역 기능이 꺼진 경우', () => {
  it('경고가 보이고 단건 생성·일괄 시작이 이유와 함께 비활성이다', async () => {
    renderScreen('llm-disabled')
    expect(await screen.findByText('번역 기능이 꺼져 있습니다')).toBeInTheDocument()

    const bulkStart = await screen.findByRole('button', { name: '일괄 번역 시작' })
    expect(bulkStart).toBeDisabled()
    expect(bulkStart).toHaveAccessibleDescription('번역 기능이 꺼져 있어 초안을 만들 수 없습니다.')

    const candidates = await region('번역 후보')
    const rowButtons = within(candidates).getAllByRole('button', { name: /만들기/ })
    expect(rowButtons.length).toBeGreaterThan(0)
    for (const button of rowButtons) expect(button).toBeDisabled()
    // 조회·반영·삭제는 막지 않는다: 성공 초안 전부 반영은 켜져 있다
    await waitFor(() => expect(applyAllButton()).toBeEnabled())
  })
})

describe('일괄 번역', () => {
  it('실행 중이면 진행 막대가 보이고 시작·전부 반영·단건 생성이 막힌다', async () => {
    renderScreen('llm-bulk-running')
    const progress = await screen.findByRole('progressbar', { name: '일괄 번역 진행' })
    expect(progress).toHaveAttribute('aria-valuemax', '20')
    expect(screen.getByText('실행 중')).toBeInTheDocument()

    expect(screen.getByRole('button', { name: '일괄 번역 시작' })).toBeDisabled()
    expect(applyAllButton()).toBeDisabled()
    const candidates = await region('번역 후보')
    for (const button of within(candidates).getAllByRole('button', { name: /만들기/ })) {
      expect(button).toBeDisabled()
    }
    expect(
      screen.getAllByText('일괄 번역이 실행 중이라 끝난 뒤에 할 수 있습니다.').length,
    ).toBeGreaterThan(0)
  })

  it('완료로 바뀌면 완료 문장이 한 번 알려지고 5초 확인이 멈춘다', async () => {
    // 5초마다 확인하는 동작을 보려고 타이머를 가짜로 바꾸되, 실제 시간도 흐르게 둔다
    vi.useFakeTimers({ shouldAdvanceTime: true })
    let countReads = 0
    server.events.on('request:start', ({ request }) => {
      const url = new URL(request.url)
      if (url.pathname === '/api/admin/llm/drafts' && url.searchParams.get('status') === 'PASSED') {
        countReads += 1
      }
    })
    renderScreen('llm-bulk-running')
    await screen.findByText('실행 중')

    // 목업은 개수 조회를 읽을 때마다 5편씩 나아가 3번 더 읽으면 완료된다(20편)
    for (let i = 0; i < 4; i++) await act(() => vi.advanceTimersByTimeAsync(5000))
    expect(
      await screen.findByText(
        '일괄 번역을 마쳤습니다. 주의 2편은 [승인 대기] 탭에서 승인해 주세요.',
      ),
    ).toBeInTheDocument()
    await waitFor(() => expect(screen.queryByText('실행 중')).not.toBeInTheDocument())
    expect(screen.getByText('완료')).toBeInTheDocument()

    const readsAtCompletion = countReads
    await act(() => vi.advanceTimersByTimeAsync(20_000))
    expect(countReads).toBe(readsAtCompletion)
  })

  it('중단됐으면 사유 안내가 보인다(하루 상한)', async () => {
    renderScreen('llm-bulk-stopped-daily-limit')
    expect(await screen.findByText('일괄 번역이 멈췄습니다')).toBeInTheDocument()
    expect(screen.getByText(/오늘 호출 상한\(400회\)을 모두 써서 멈췄습니다/)).toBeInTheDocument()
    expect(screen.getByText('중단됨')).toBeInTheDocument()
  })

  it('중단됐으면 사유 안내가 보인다(서버 재시작)', async () => {
    renderScreen('llm-bulk-stopped-server-restarted')
    expect(await screen.findByText(/실행 중에 서버가 다시 시작되어 멈췄습니다/)).toBeInTheDocument()
  })

  it('시작하면 접수 요청이 나가고, 범위를 벗어난 숫자는 요청 없이 오류를 보인다', async () => {
    const user = userEvent.setup()
    const writes = recordWrites()
    renderScreen()
    const input = await screen.findByLabelText('찜 많은 순 상위 몇 편을 번역할까요')

    await user.clear(input)
    await user.type(input, '0')
    await user.click(screen.getByRole('button', { name: '일괄 번역 시작' }))
    expect(screen.getByText('1부터 100 사이의 숫자를 입력해 주세요.')).toBeInTheDocument()
    expect(writes).toHaveLength(0)

    await user.clear(input)
    await user.type(input, '5')
    await user.click(screen.getByRole('button', { name: '일괄 번역 시작' }))
    await waitFor(() => expect(writes).toHaveLength(1))
    expect(writes[0].path).toBe('/api/admin/llm/bulk-drafts')
    expect(JSON.parse(writes[0].body)).toEqual({ count: 5 })
    expect(await screen.findByText('실행 중')).toBeInTheDocument()
  })
})

describe('초안 반영', () => {
  it('표본을 확인하지 않았으면 경고와 체크가 나오고, 체크해야 반영할 수 있다. 요청은 allPassed다', async () => {
    const user = userEvent.setup()
    const writes = recordWrites()
    renderScreen()
    await waitFor(() => expect(applyAllButton()).toBeEnabled())
    await user.click(applyAllButton())

    const dialog = await screen.findByRole('alertdialog')
    expect(within(dialog).getByText('성공 초안 30편을 모두 반영할까요?')).toBeInTheDocument()
    expect(within(dialog).getByText('표본을 아직 확인하지 않았습니다')).toBeInTheDocument()
    const confirm = within(dialog).getByRole('button', { name: '30편 모두 반영' })
    expect(confirm).toBeDisabled()

    await user.click(within(dialog).getByRole('checkbox', { name: '표본 확인 없이 반영합니다' }))
    expect(confirm).toBeEnabled()
    await user.click(confirm)

    await waitFor(() => expect(writes).toHaveLength(1))
    expect(writes[0].path).toBe('/api/admin/llm/drafts/apply')
    expect(JSON.parse(writes[0].body)).toEqual({ allPassed: true })
    const result = await region('반영 결과')
    expect(within(result).getByText('반영 30편 · 건너뜀 0편')).toBeInTheDocument()
  })

  it('표본을 읽었다고 표시하면 확인한 편수가 보이고 체크 없이 반영할 수 있다', async () => {
    const user = userEvent.setup()
    renderScreen()
    await user.click(await screen.findByRole('button', { name: /무작위 표본 10편 읽어 보기/ }))
    await user.click(await screen.findByRole('checkbox', { name: '표본 10편을 읽어 봤습니다' }))
    await waitFor(() => expect(applyAllButton()).toBeEnabled())
    expect(screen.getByText(/표본 확인 완료/)).toBeInTheDocument()
    await user.click(applyAllButton())

    const dialog = await screen.findByRole('alertdialog')
    expect(within(dialog).getByText('✓ 이번에 표본 10편을 확인했습니다.')).toBeInTheDocument()
    expect(within(dialog).queryByRole('checkbox')).not.toBeInTheDocument()
    expect(within(dialog).getByRole('button', { name: '30편 모두 반영' })).toBeEnabled()
  })

  it('확정 버튼을 연달아 눌러도 반영 요청은 한 번만 나간다', async () => {
    const user = userEvent.setup()
    const writes = recordWrites()
    renderScreen()
    await waitFor(() => expect(applyAllButton()).toBeEnabled())
    await user.click(applyAllButton())
    const dialog = await screen.findByRole('alertdialog')
    await user.click(within(dialog).getByRole('checkbox', { name: '표본 확인 없이 반영합니다' }))
    await user.dblClick(within(dialog).getByRole('button', { name: '30편 모두 반영' }))

    await region('반영 결과')
    expect(writes.filter((w) => w.path === '/api/admin/llm/drafts/apply')).toHaveLength(1)
  })

  it('부분 성공이면 요약과 건너뜀 이유가 보인다', async () => {
    const user = userEvent.setup()
    renderScreen('llm-partial-apply')
    await waitFor(() => expect(applyAllButton()).toBeEnabled())
    await user.click(applyAllButton())
    const dialog = await screen.findByRole('alertdialog')
    await user.click(within(dialog).getByRole('checkbox', { name: '표본 확인 없이 반영합니다' }))
    await user.click(within(dialog).getByRole('button', { name: '30편 모두 반영' }))

    const result = await region('반영 결과')
    expect(within(result).getByText('반영 27편 · 건너뜀 3편')).toBeInTheDocument()
    expect(
      within(result).getByText('초안이 없음(보관 기간이 지났거나 이미 처리됨)'),
    ).toBeInTheDocument()
    expect(within(result).getByText('검사 실패 초안')).toBeInTheDocument()
    expect(
      within(result).getByText('번역한 뒤 원문이 바뀌었거나 이미 한국어임'),
    ).toBeInTheDocument()
    expect(within(result).getByRole('button', { name: '반영한 27편 보기' })).toBeInTheDocument()
  })

  it('수집 등과 겹치면(409) 오류 대신 안내 띠와 수집 화면 링크가 보인다', async () => {
    const user = userEvent.setup()
    renderScreen('llm-apply-conflict')
    await waitFor(() => expect(applyAllButton()).toBeEnabled())
    await user.click(applyAllButton())
    const dialog = await screen.findByRole('alertdialog')
    await user.click(within(dialog).getByRole('checkbox', { name: '표본 확인 없이 반영합니다' }))
    await user.click(within(dialog).getByRole('button', { name: '30편 모두 반영' }))

    expect(
      await screen.findByText(/수집 화면에서 실행 상태를 확인할 수 있습니다/),
    ).toBeInTheDocument()
    expect(screen.getByRole('link', { name: '수집 화면으로 가기' })).toHaveAttribute(
      'href',
      '/admin/collect',
    )
    await waitFor(() => expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument())
  })

  it('반영한 작품을 영어로 되돌리면 경고 대화상자 확인 뒤 되돌리기 요청이 나간다', async () => {
    const user = userEvent.setup()
    const writes = recordWrites()
    renderScreen()
    await waitFor(() => expect(applyAllButton()).toBeEnabled())
    await user.click(applyAllButton())
    const applyDialog = await screen.findByRole('alertdialog')
    await user.click(
      within(applyDialog).getByRole('checkbox', { name: '표본 확인 없이 반영합니다' }),
    )
    await user.click(within(applyDialog).getByRole('button', { name: '30편 모두 반영' }))

    const result = await region('반영 결과')
    await user.click(within(result).getByRole('button', { name: '반영한 30편 보기' }))
    const revertButtons = within(result).getAllByRole('button', { name: '영어로 되돌리기' })
    await user.click(revertButtons[0])

    const dialog = await screen.findByRole('alertdialog')
    expect(within(dialog).getByText(/최대 일주일 동안/)).toBeInTheDocument()
    await user.click(within(dialog).getByRole('button', { name: '영어로 되돌리기' }))

    await waitFor(() =>
      expect(
        writes.some((w) => /\/api\/admin\/llm\/titles\/\d+\/overview-revert$/.test(w.path)),
      ).toBe(true),
    )
    await waitFor(() => expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument())
  })
})

// 탭 이름으로 탭을 열고 그 탭의 내용(tabpanel)을 돌려준다
async function openTab(user: ReturnType<typeof userEvent.setup>, name: string) {
  await user.click(await screen.findByRole('tab', { name }))
  return screen.findByRole('tabpanel', { name })
}

describe('등급별 탭과 항목', () => {
  it('일괄 진행 줄에 성공·주의·실패 수가 나뉘어 보인다', async () => {
    renderScreen()
    const bulk = await region('일괄 번역')
    expect(await within(bulk).findByText(/성공 13 · 주의 2 · 실패 3/)).toBeInTheDocument()
  })

  it('승인 대기 탭의 [승인하고 반영]은 확인창 없이 그 한 편만 반영하고 탭 건수가 줄어든다', async () => {
    const user = userEvent.setup()
    const writes = recordWrites()
    renderScreen()
    const panel = await openTab(user, '승인 대기 · 주의 (6)')

    // 주의 탭에는 체크가 있고, 주의 이유가 글자로 보인다
    const approveButtons = await within(panel).findAllByRole('button', { name: '승인하고 반영' })
    expect(approveButtons).toHaveLength(6)
    expect(within(panel).getByText('사람이 읽고 승인해야 합니다')).toBeInTheDocument()
    expect(within(panel).getAllByRole('list', { name: '주의 이유' }).length).toBeGreaterThan(0)

    await user.click(approveButtons[0])
    await waitFor(() => expect(writes).toHaveLength(1))
    expect(writes[0].path).toBe('/api/admin/llm/drafts/apply')
    expect(JSON.parse(writes[0].body)).toEqual({ draftIds: [expect.any(String)] })
    expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument()

    const result = await region('반영 결과')
    expect(within(result).getByText('반영 1편 · 건너뜀 0편')).toBeInTheDocument()
    expect(await screen.findByRole('tab', { name: '승인 대기 · 주의 (5)' })).toBeInTheDocument()
  })

  it('주의 초안을 골라 반영하면 주의가 섞였다는 안내가 대화상자에 보인다', async () => {
    const user = userEvent.setup()
    const writes = recordWrites()
    renderScreen()
    const panel = await openTab(user, '승인 대기 · 주의 (6)')
    await screen.findAllByRole('button', { name: '승인하고 반영' })

    const checkboxes = within(panel).getAllByRole('checkbox') // 첫 번째는 "전체 선택"
    await user.click(checkboxes[1])
    await user.click(checkboxes[2])
    await user.click(within(panel).getByRole('button', { name: '선택한 2편 반영' }))

    const dialog = await screen.findByRole('alertdialog')
    expect(within(dialog).getByText('선택한 2편을 반영할까요?')).toBeInTheDocument()
    expect(within(dialog).getByText(/고른 초안 중 주의 2편이 있습니다/)).toBeInTheDocument()
    await user.click(within(dialog).getByRole('button', { name: '2편 반영' }))
    await waitFor(() => expect(writes).toHaveLength(1))
    expect(JSON.parse(writes[0].body).draftIds).toHaveLength(2)
  })

  it('전부 반영 대화상자는 성공만 반영하고 주의는 들어가지 않는다고 알린다', async () => {
    const user = userEvent.setup()
    renderScreen()
    await waitFor(() => expect(applyAllButton()).toBeEnabled())
    expect(
      screen.getByText(/성공 초안만 반영합니다\. 주의 6편과 실패 5편은 들어가지 않습니다\./),
    ).toBeInTheDocument()
    await user.click(applyAllButton())
    const dialog = await screen.findByRole('alertdialog')
    expect(within(dialog).getByText(/주의 6편은 이번 반영에 들어가지 않습니다/)).toBeInTheDocument()
  })

  it('성공 탭의 표본 상자는 펼칠 때 표본을 불러온다', async () => {
    const user = userEvent.setup()
    renderScreen()
    await screen.findByRole('tab', { name: '성공 (30)' })
    expect(screen.queryByRole('checkbox', { name: '표본 10편을 읽어 봤습니다' })).toBeNull()
    await user.click(await screen.findByRole('button', { name: /무작위 표본 10편 읽어 보기/ }))
    expect(
      await screen.findByRole('checkbox', { name: '표본 10편을 읽어 봤습니다' }),
    ).toBeInTheDocument()
  })

  it('수정해 저장하면 새 등급이 보이고, 닫으면 탭 건수가 맞춰지며, 고친 초안은 다시 번역 전에 확인받는다', async () => {
    const user = userEvent.setup()
    const writes = recordWrites()
    renderScreen()
    const panel = await openTab(user, '성공 (30)')

    // 1. 첫 항목의 수정 칸을 열고 주소가 든 글로 바꿔 저장한다(목업은 주소가 있으면 실패로 다시 검사한다)
    await user.click((await within(panel).findAllByRole('button', { name: '수정' }))[0])
    const textarea = await within(panel).findByRole('textbox', { name: /한국어 번역문/ })
    await user.clear(textarea)
    await user.type(textarea, '주소 https://example.com 이 들어 있는 충분히 긴 번역문이다.')
    await user.click(within(panel).getByRole('button', { name: '저장하고 다시 검사' }))

    // 2. 저장 응답의 새 등급이 결과 띠와 배지에 보이고, 요청 본문에 고친 글이 담긴다
    expect(await within(panel).findByText('저장했습니다 · 검사 결과: 실패')).toBeInTheDocument()
    const patch = writes.find((w) => w.method === 'PATCH')
    expect(patch?.path).toMatch(/\/api\/admin\/llm\/drafts\/.+/)
    expect(JSON.parse(patch?.body ?? '{}')).toEqual({
      translatedText: '주소 https://example.com 이 들어 있는 충분히 긴 번역문이다.',
    })
    expect(within(panel).getByText('수정됨')).toBeInTheDocument()

    // 3. 저장한 내용이 기준이 되어 고친 내용이 없으므로 확인 없이 닫히고, 목록·건수를 새로 받아 실패 탭으로 옮겨 간다
    await user.click(within(panel).getByRole('button', { name: '닫기' }))
    expect(await screen.findByRole('tab', { name: '실패 (6)' })).toBeInTheDocument()
    expect(screen.getByRole('tab', { name: '성공 (29)' })).toBeInTheDocument()

    // 4. 직접 고친 초안의 [다시 번역]은 고친 글이 사라진다는 확인을 거친다
    const failedPanel = await openTab(user, '실패 (6)')
    const editedRow = (await within(failedPanel).findByText('수정됨')).closest('li')
    expect(editedRow).not.toBeNull()
    await user.click(within(editedRow as HTMLElement).getByRole('button', { name: '다시 번역' }))
    const dialog = await screen.findByRole('alertdialog')
    expect(within(dialog).getByText(/고친 번역문이 새 번역으로 바뀝니다/)).toBeInTheDocument()
    expect(
      writes.filter((w) => w.path === '/api/admin/llm/drafts' && w.method === 'POST'),
    ).toHaveLength(0)
    await user.click(within(dialog).getByRole('button', { name: '다시 번역' }))
    await waitFor(() =>
      expect(
        writes.filter((w) => w.path === '/api/admin/llm/drafts' && w.method === 'POST'),
      ).toHaveLength(1),
    )
  })

  it('고친 내용이 있는데 다른 항목의 [수정]을 누르면 저장하지 않고 닫을지 묻는다', async () => {
    const user = userEvent.setup()
    renderScreen()
    const panel = await openTab(user, '성공 (30)')
    await user.click((await within(panel).findAllByRole('button', { name: '수정' }))[0])
    const textarea = await within(panel).findByRole('textbox', { name: /한국어 번역문/ })
    await user.type(textarea, ' 덧붙인 말')

    // 첫 항목은 수정 중이라 버튼이 숨겨져, 남은 [수정] 중 첫 번째가 둘째 항목이다
    await user.click(within(panel).getAllByRole('button', { name: '수정' })[0])
    const dialog = await screen.findByRole('alertdialog')
    expect(within(dialog).getByText('고친 내용을 저장하지 않고 닫을까요?')).toBeInTheDocument()

    // [계속 고치기]: 입력한 글이 그대로 남는다
    await user.click(within(dialog).getByRole('button', { name: '계속 고치기' }))
    await waitFor(() => expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument())
    expect(
      (within(panel).getByRole('textbox', { name: /한국어 번역문/ }) as HTMLTextAreaElement).value,
    ).toContain('덧붙인 말')

    // [닫기]: 앞 칸을 버리고 새 항목의 칸이 열린다(칸은 한 번에 하나)
    await user.click(within(panel).getAllByRole('button', { name: '수정' })[0])
    await user.click(
      within(await screen.findByRole('alertdialog')).getByRole('button', { name: '닫기' }),
    )
    await waitFor(() => expect(within(panel).getAllByRole('textbox')).toHaveLength(1))
    expect(
      (within(panel).getByRole('textbox', { name: /한국어 번역문/ }) as HTMLTextAreaElement).value,
    ).not.toContain('덧붙인 말')
  })
})

describe('사용량 표시', () => {
  it('일괄 번역 버튼 옆에 남은 횟수가 보이고, 초기화 안내는 한 곳에만 있다', async () => {
    renderScreen()
    const bulk = await region('일괄 번역')
    expect(await within(bulk).findByText('여유')).toBeInTheDocument()
    expect(within(bulk).getByText('남은 363회')).toBeInTheDocument()
    expect(screen.getAllByText(/태평양 시간으로 날짜가 바뀔 때/)).toHaveLength(1)
  })

  it('80% 이상이면 "곧 상한", 상한에 닿으면 "오늘 상한 도달"이 글자로 보인다', async () => {
    renderScreen('llm-usage-near')
    const near = await region('일괄 번역')
    expect(await within(near).findByText('곧 상한')).toBeInTheDocument()
    expect(within(near).getByText('남은 60회')).toBeInTheDocument()
  })

  it('상한에 닿으면 남은 0회와 "오늘 상한 도달"이 보인다', async () => {
    renderScreen('llm-usage-reached')
    const bulk = await region('일괄 번역')
    expect(await within(bulk).findByText('오늘 상한 도달')).toBeInTheDocument()
    expect(within(bulk).getByText('남은 0회')).toBeInTheDocument()
  })
})

describe('오래된 번역 대량 되돌리기', () => {
  it('미리보기로 편수를 본 뒤 같은 기준 시각으로 확정하고, 확정하면 번역 수가 줄어든다', async () => {
    // 기준 시각 계산이 시계에 따라 달라지지 않도록 Date만 고정한다(타이머는 그대로 둔다)
    vi.useFakeTimers({ toFake: ['Date'] })
    vi.setSystemTime(new Date('2026-10-29T03:00:00Z'))
    const user = userEvent.setup()
    const writes = recordWrites()
    renderScreen()

    const section = await region('번역 통계와 오래된 번역 되돌리기')
    expect(await within(section).findByText('312편')).toBeInTheDocument()
    expect(within(section).getByLabelText('몇 개월 지난 번역을 영어로 되돌릴까요')).toHaveValue(5)

    // 1. 미리보기 전에는 되돌리기를 누를 수 없다
    expect(within(section).getByRole('button', { name: '되돌리기' })).toBeDisabled()

    // 2. 미리보기: DB를 바꾸지 않고 편수만 센다
    await user.click(within(section).getByRole('button', { name: '대상 미리보기' }))
    const status = await within(section).findByText(/미리보기: \d+편이 대상입니다/)
    expect(status).toHaveTextContent('아직 아무것도 바뀌지 않았습니다')

    // 3. 확정 대화상자에서 되돌린다
    await user.click(within(section).getByRole('button', { name: /^\d+편 되돌리기$/ }))
    const dialog = await screen.findByRole('alertdialog')
    expect(within(dialog).getByText(/영어 줄거리는 다음 수집/)).toBeInTheDocument()
    await user.click(within(dialog).getByRole('button', { name: /^\d+편 영어로 되돌리기$/ }))

    await waitFor(() => expect(writes).toHaveLength(2))
    const [previewBody, confirmBody] = writes.map((w) => JSON.parse(w.body))
    expect(writes.every((w) => w.path === '/api/admin/llm/overview-reverts')).toBe(true)
    expect(previewBody.isPreview).toBe(true)
    // 미리보기와 확정이 같은 기준 시각을 보낸다
    expect(confirmBody.translatedBefore).toBe(previewBody.translatedBefore)
    expect(confirmBody.isPreview).toBeUndefined()

    await waitFor(() => expect(within(section).queryByText('312편')).not.toBeInTheDocument())
  })

  it('입력을 바꾸면 미리보기가 풀려 다시 세어야 되돌릴 수 있다', async () => {
    const user = userEvent.setup()
    renderScreen()
    const section = await region('번역 통계와 오래된 번역 되돌리기')
    await user.click(await within(section).findByRole('button', { name: '대상 미리보기' }))
    await within(section).findByText(/미리보기: \d+편이 대상입니다/)

    const input = within(section).getByLabelText('몇 개월 지난 번역을 영어로 되돌릴까요')
    await user.clear(input)
    await user.type(input, '6')
    expect(within(section).queryByText(/미리보기: \d+편이 대상입니다/)).not.toBeInTheDocument()
    expect(within(section).getByRole('button', { name: '되돌리기' })).toBeDisabled()
  })

  it('1~24 밖의 숫자는 미리보기를 막고 안내한다', async () => {
    const user = userEvent.setup()
    const writes = recordWrites()
    renderScreen()
    const section = await region('번역 통계와 오래된 번역 되돌리기')
    const input = await within(section).findByLabelText('몇 개월 지난 번역을 영어로 되돌릴까요')
    await user.clear(input)
    await user.type(input, '30')
    expect(
      within(section).getAllByText('1부터 24 사이의 숫자를 입력해 주세요.').length,
    ).toBeGreaterThan(0)
    expect(within(section).getByRole('button', { name: '대상 미리보기' })).toBeDisabled()
    expect(writes).toHaveLength(0)
  })

  it('반영한 번역이 없으면 안내만 보이고 입력은 숨는다', async () => {
    renderScreen('llm-no-translated')
    const section = await region('번역 통계와 오래된 번역 되돌리기')
    expect(
      await within(section).findByText('아직 반영한 번역 줄거리가 없습니다.'),
    ).toBeInTheDocument()
    expect(within(section).queryByRole('button', { name: '대상 미리보기' })).not.toBeInTheDocument()
  })
})

describe('데모 계정', () => {
  it('수정 칸은 열리지만 저장과 대량 되돌리기 미리보기는 자물쇠로 막힌다', async () => {
    setMockRole('DEMO')
    const user = userEvent.setup()
    const writes = recordWrites()
    renderScreen()

    // 수정 칸은 평가자가 기능을 볼 수 있게 열린다. 저장 버튼만 막힌다
    const panel = await openTab(user, '성공 (30)')
    await user.click((await within(panel).findAllByRole('button', { name: '수정' }))[0])
    const textarea = await within(panel).findByRole('textbox', { name: /한국어 번역문/ })
    await user.type(textarea, ' 덧붙인 말')
    const save = within(panel).getByRole('button', { name: '저장하고 다시 검사' })
    expect(save).toHaveAttribute('aria-disabled', 'true')
    await user.click(save)

    // 대량 되돌리기는 미리보기도 쓰기 요청이라 막히고 대화상자가 열리지 않는다
    const section = await region('번역 통계와 오래된 번역 되돌리기')
    const preview = await within(section).findByRole('button', { name: '대상 미리보기' })
    expect(preview).toHaveAttribute('aria-disabled', 'true')
    await user.click(preview)

    expect(writes).toHaveLength(0)
    expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument()
  })

  it('쓰기 버튼이 자물쇠로 막혀 눌러도 요청이 나가지 않는다', async () => {
    setMockRole('DEMO')
    const user = userEvent.setup()
    const writes = recordWrites()
    renderScreen()

    const start = await screen.findByRole('button', { name: '일괄 번역 시작' })
    expect(start).toHaveAttribute('aria-disabled', 'true')
    await user.click(start)
    await waitFor(() => expect(applyAllButton()).toHaveAttribute('aria-disabled', 'true'))
    await user.click(applyAllButton())

    const candidates = await region('번역 후보')
    await user.click(within(candidates).getAllByRole('button', { name: /만들기/ })[0])

    expect(writes).toHaveLength(0)
    expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument()
  })
})

describe('빈 상태', () => {
  it('후보와 초안이 없으면 안내가 보인다', async () => {
    renderScreen('llm-empty')
    expect(await screen.findByText('번역할 작품이 없습니다')).toBeInTheDocument()
    expect(await screen.findByText('아직 일괄 번역을 실행한 적이 없습니다.')).toBeInTheDocument()
    expect(await screen.findByText('성공한 초안이 없습니다')).toBeInTheDocument()
    expect(await screen.findByRole('tab', { name: '성공 (0)' })).toBeInTheDocument()
    expect(screen.getByRole('tab', { name: '승인 대기 · 주의 (0)' })).toBeInTheDocument()
  })
})
