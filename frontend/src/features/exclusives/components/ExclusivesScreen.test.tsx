import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { ExclusivesScreen } from '@/features/exclusives/components/ExclusivesScreen'
import { ExclusivesPage } from '@/pages/ExclusivesPage'

/*
 * 서비스별 독점 목록 1차안(Task 075) 렌더링 테스트. 정적 샘플 데이터라 API 대역(MSW)은 쓰지 않는다.
 * 확인하는 것: "확인된 범위"·구조적 공백 안내·출처·기준일이 보이는지, 쿠팡플레이 탭은 목록 대신 데이터 부족 안내인지,
 * 운영(목업 아님)에서는 샘플 대신 준비 중 화면인지.
 * 막으려는 사고: 레이아웃을 바꾸다 출처·기준일·"확인된 범위" 표현이 빠지거나, 지어낸 샘플이 운영에 나가는 것.
 */
function renderScreen(path = '/exclusives', element = <ExclusivesScreen />) {
  const router = createMemoryRouter([{ path: '/exclusives', element }], {
    initialEntries: [path],
  })
  render(<RouterProvider router={router} />)
}

afterEach(() => {
  vi.unstubAllEnvs()
})

describe('서비스별 독점 목록(1차안)', () => {
  it('확인된 범위·구조적 공백·출처·기준일을 보이고, 쿠팡플레이 탭은 목록 대신 데이터 부족 안내를 보인다', async () => {
    const user = userEvent.setup()
    renderScreen()

    expect(
      screen.getByRole('heading', { level: 2, name: '확인된 범위의 독점이에요' }),
    ).toBeInTheDocument()
    expect(screen.getByText('영화는 쿠팡플레이와 비교하지 않았어요.')).toBeInTheDocument()
    expect(screen.getByText(/제공처 정보 출처/)).toBeInTheDocument()
    expect(screen.getByText(/데이터 기준일/)).toBeInTheDocument()
    // 첫 탭(넷플릭스)의 작품 목록
    expect(screen.getByRole('list', { name: '넷플릭스 독점 작품' })).toBeInTheDocument()

    await user.click(screen.getByRole('tab', { name: /쿠팡플레이/ }))
    expect(screen.getByText('쿠팡플레이는 독점 여부를 판단하지 않아요')).toBeInTheDocument()
    expect(screen.queryByRole('list', { name: '쿠팡플레이 독점 작품' })).not.toBeInTheDocument()
  })

  it('목업 모드에서 ?exclusivesPreview=empty면 빈 상태 문구를 보인다', () => {
    vi.stubEnv('VITE_USE_MOCK', 'true')
    renderScreen('/exclusives?service=WATCHA&exclusivesPreview=empty')
    expect(screen.getByText('왓챠 독점으로 확인된 작품이 아직 없어요')).toBeInTheDocument()
  })

  it('운영(목업 아님)에서는 샘플 대신 준비 중 화면을 보인다', () => {
    vi.stubEnv('VITE_USE_MOCK', 'false')
    renderScreen('/exclusives', <ExclusivesPage />)
    expect(screen.queryByRole('tablist')).not.toBeInTheDocument()
  })
})
