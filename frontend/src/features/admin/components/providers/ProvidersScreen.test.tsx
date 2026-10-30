import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { setupServer } from 'msw/node'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { afterAll, afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest'
import { Providers } from '@/app/providers'
import { AdminLayout } from '@/app/AdminLayout'
import { ProvidersScreen } from '@/features/admin/components/providers/ProvidersScreen'
import { resetMockAdminState } from '@/mocks/adminHandlers'
import { handlers } from '@/mocks/handlers'

/*
 * 제공처 매핑 화면 테스트(Task 029, SCR-15): 미매핑 먼저 정렬, 필터 전환과 주소, 저장 버튼 활성, 저장 성공(배지·메뉴 숫자 변화),
 * 저장 실패, 관련 없음 선택 시 등급 비활성, 오류·빈 상태를 본다. API는 앱의 목업 핸들러(MSW)다.
 * 표(md 이상)와 카드(좁은 화면)가 CSS로만 갈리므로 테스트(CSS 없음)에서는 둘 다 보인다. 그래서 표 안으로 범위를 좁혀 찾는다.
 */

const server = setupServer(...handlers)

beforeAll(() => {
  server.listen({ onUnhandledFrame: 'error' })
})
beforeEach(() => vi.stubEnv('VITE_USE_MOCK', 'true'))
afterEach(() => {
  server.resetHandlers()
  vi.unstubAllEnvs()
  resetMockAdminState()
  window.history.replaceState({}, '', '/')
})
afterAll(() => server.close())

// 관리자 메뉴(AdminLayout)까지 함께 그려 "미매핑 n건" 숫자 변화도 본다
function renderProviders(initialPath = '/admin/providers?filter=unmapped-first') {
  const router = createMemoryRouter(
    [
      {
        Component: AdminLayout,
        children: [{ path: '/admin/providers', element: <ProvidersScreen /> }],
      },
    ],
    { initialEntries: [initialPath] },
  )
  render(
    <Providers>
      <RouterProvider router={router} />
    </Providers>,
  )
  return router
}

async function findTable() {
  return screen.findByRole('table', { name: 'TMDB 제공처 매핑 목록' })
}

// 표의 본문 행(헤더 제외, 오류 줄 포함)
function bodyRows(table: HTMLElement) {
  return within(table).getAllByRole('row').slice(1)
}

describe('목록과 정렬', () => {
  it('미매핑 제공처가 위에 오고, 그 행에 "미매핑" 글자가 보인다', async () => {
    renderProviders()
    const table = await findTable()

    const rows = bodyRows(table)
    expect(rows).toHaveLength(13) // 매핑됨 8 + 관련 없음 2 + 미매핑 3
    // 미매핑 3개가 맨 위, 최근 발견한 순(Crunchyroll → MUBI → Plex)
    expect(within(rows[0]).getByText('Crunchyroll')).toBeInTheDocument()
    expect(within(rows[1]).getByText('MUBI')).toBeInTheDocument()
    expect(within(rows[2]).getByText('Plex')).toBeInTheDocument()
    for (const row of rows.slice(0, 3)) expect(within(row).getByText('미매핑')).toBeInTheDocument()
    expect(within(rows[3]).queryByText('미매핑')).not.toBeInTheDocument()
  })

  it('관리자 메뉴에 미매핑 수가 붙는다', async () => {
    renderProviders()
    await findTable()
    const nav = screen.getByRole('navigation', { name: '관리자 메뉴' })
    await waitFor(() =>
      expect(within(nav).getByText('미매핑 3건', { exact: false })).toBeInTheDocument(),
    )
  })
})

describe('보기 필터', () => {
  it('"미매핑만"을 고르면 3개만 보이고 주소에 ?filter=unmapped가 붙는다', async () => {
    const user = userEvent.setup()
    const router = renderProviders()
    const table = await findTable()
    expect(bodyRows(table)).toHaveLength(13)

    await user.click(screen.getByRole('radio', { name: '미매핑만 (3)' }))

    await waitFor(() => expect(bodyRows(within(document.body).getByRole('table'))).toHaveLength(3))
    expect(router.state.location.search).toBe('?filter=unmapped')
  })

  it('주소에 filter가 없으면 "전체"가 기본이고 받은 순서대로 보이며, 이름 없는 값도 전체로 본다', async () => {
    renderProviders('/admin/providers')
    const table = await findTable()
    expect(within(bodyRows(table)[0]).getByText('Netflix')).toBeInTheDocument()
    expect(screen.getByRole('radio', { name: '전체 (13)' })).toBeChecked()
  })

  it('"미매핑 먼저"를 고르면 주소에 ?filter=unmapped-first가 붙고, 다시 "전체"를 고르면 파라미터가 사라진다', async () => {
    const user = userEvent.setup()
    const router = renderProviders('/admin/providers')
    await findTable()

    await user.click(screen.getByRole('radio', { name: '미매핑 먼저' }))
    expect(router.state.location.search).toBe('?filter=unmapped-first')
    await user.click(screen.getByRole('radio', { name: '전체 (13)' }))
    expect(router.state.location.search).toBe('')
  })

  it('주소에 ?filter=all이 있으면 전체를 받은 순서대로 보인다', async () => {
    renderProviders('/admin/providers?filter=all')
    const table = await findTable()
    const rows = bodyRows(table)
    expect(rows).toHaveLength(13)
    expect(within(rows[0]).getByText('Netflix')).toBeInTheDocument() // 미매핑이 위로 올라가지 않는다
    expect(screen.getByRole('radio', { name: '전체 (13)' })).toBeChecked()
  })
})

describe('매핑 저장', () => {
  it('값을 바꾸면 "저장 안 됨"과 [저장]이 켜지고, 저장하면 배지가 바뀌고 메뉴의 미매핑 수가 줄어든다', async () => {
    const user = userEvent.setup()
    renderProviders()
    const table = await findTable()
    const nav = screen.getByRole('navigation', { name: '관리자 메뉴' })

    const row = bodyRows(table)[0] // Crunchyroll(미매핑)
    const saveButton = within(row).getByRole('button', { name: /^저장/ })
    expect(saveButton).toBeDisabled() // 바뀐 것이 없으면 비활성
    expect(within(row).queryByText('저장 안 됨')).not.toBeInTheDocument()

    await user.selectOptions(within(row).getByLabelText('Crunchyroll 매핑 서비스'), '웨이브')
    expect(within(row).getByText('저장 안 됨')).toBeInTheDocument()
    expect(saveButton).toBeEnabled()

    await user.click(saveButton)

    // 저장되면 이 제공처는 매핑됨이 되어 미매핑 위쪽 묶음에서 빠진다
    await waitFor(() => expect(within(nav).getByText(/미매핑 2건/)).toBeInTheDocument())
    const crunchyrollRow = within(table)
      .getAllByRole('row')
      .find((r) => within(r).queryByText('Crunchyroll'))!
    expect(within(crunchyrollRow).getByText('매핑됨')).toBeInTheDocument()
    expect(within(crunchyrollRow).queryByText('저장 안 됨')).not.toBeInTheDocument()
  })

  it('"관련 없음"을 고르면 등급 선택이 사라지고 "—"가 보이며, 저장하면 관련 없음 배지가 된다', async () => {
    const user = userEvent.setup()
    renderProviders()
    const table = await findTable()

    const row = bodyRows(table)[0] // Crunchyroll
    await user.selectOptions(within(row).getByLabelText('Crunchyroll 매핑 서비스'), '넷플릭스')
    expect(within(row).getByLabelText('Crunchyroll 등급')).toBeEnabled()

    await user.selectOptions(
      within(row).getByLabelText('Crunchyroll 매핑 서비스'),
      '관련 없음(쓰지 않음)',
    )
    expect(within(row).queryByLabelText('Crunchyroll 등급')).not.toBeInTheDocument()
    expect(within(row).getByText('—')).toBeInTheDocument()

    await user.click(within(row).getByRole('button', { name: /^저장/ }))
    await waitFor(() => {
      const saved = within(table)
        .getAllByRole('row')
        .find((r) => within(r).queryByText('Crunchyroll'))!
      expect(within(saved).getByText('관련 없음')).toBeInTheDocument()
    })
  })

  it('AD 등급 선택지는 "AD(광고형)"이고 "2단계부터" 태그는 붙지 않는다', async () => {
    const user = userEvent.setup()
    renderProviders()
    const table = await findTable()

    const row = bodyRows(table)[0]
    await user.selectOptions(within(row).getByLabelText('Crunchyroll 매핑 서비스'), '넷플릭스')
    await user.selectOptions(within(row).getByLabelText('Crunchyroll 등급'), 'AD(광고형)')
    expect(within(row).queryByText('2단계부터')).not.toBeInTheDocument()
  })

  it('저장이 실패하면 행 아래에 오류가 보이고 고른 값은 그대로 남는다', async () => {
    window.history.replaceState({}, '', '/?mockScenario=save-error')
    const user = userEvent.setup()
    renderProviders()
    const table = await findTable()

    const row = bodyRows(table)[0]
    const select = within(row).getByLabelText('Crunchyroll 매핑 서비스')
    await user.selectOptions(select, '티빙')
    await user.click(within(row).getByRole('button', { name: /^저장/ }))

    expect(await within(table).findByRole('alert')).toHaveTextContent(
      '일시적인 오류가 발생했습니다',
    )
    expect(select).toHaveValue('4') // 사용자가 고른 티빙(서비스 ID 4)이 유지된다
    expect(within(row).getByRole('button', { name: /^저장/ })).toBeEnabled() // 다시 눌러 재시도할 수 있다
  })
})

describe('빈 상태·오류', () => {
  it('?mockScenario=empty면 빈 상태 안내와 수집 화면 링크가 보인다', async () => {
    window.history.replaceState({}, '', '/?mockScenario=empty')
    renderProviders()
    expect(await screen.findByText('아직 발견된 제공처가 없습니다')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: '수집 화면으로 이동' })).toHaveAttribute(
      'href',
      '/admin/collect',
    )
  })

  it('?mockScenario=error면 오류 안내가 보이고, 이때도 관리자 메뉴는 숫자 없이 나온다', async () => {
    window.history.replaceState({}, '', '/?mockScenario=error')
    renderProviders()
    expect(
      await screen.findByText('불러오지 못했습니다', {}, { timeout: 8000 }),
    ).toBeInTheDocument()
    const nav = screen.getByRole('navigation', { name: '관리자 메뉴' })
    expect(within(nav).queryByText(/미매핑 \d+건/)).not.toBeInTheDocument()
  }, 15000)

  it('"미매핑만"에서 미매핑을 모두 정리하면 "미매핑 제공처가 없습니다" 안내가 나온다', async () => {
    const user = userEvent.setup()
    renderProviders('/admin/providers?filter=unmapped')
    const names = ['Crunchyroll', 'MUBI', 'Plex'] // 최근 발견한 순
    for (const name of names) {
      const table = await findTable()
      const row = bodyRows(table)[0]
      expect(within(row).getByText(name)).toBeInTheDocument()
      await user.selectOptions(
        within(row).getByLabelText(`${name} 매핑 서비스`),
        '관련 없음(쓰지 않음)',
      )
      await user.click(within(row).getByRole('button', { name: /^저장/ }))
      if (name !== 'Plex') {
        await waitFor(() => expect(within(table).queryByText(name)).not.toBeInTheDocument())
      }
    }
    expect(await screen.findByText('미매핑 제공처가 없습니다')).toBeInTheDocument()
    expect(screen.getByText('모두 검토했습니다.')).toBeInTheDocument()
  })
})
