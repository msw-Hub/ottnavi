import { useState, type ReactNode } from 'react'
import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router'
import { describe, expect, it, vi } from 'vitest'
import type { ServiceStatusEntry } from '@/components/common/ServiceStatusRow'
import { WishlistRow } from '@/features/wishlist/components/WishlistRow'
import type { WishlistListItem } from '@/features/wishlist/mockTypes'

const ITEM: WishlistListItem = {
  wishlistItemId: 1,
  mediaType: 'movie',
  tmdbId: 496243,
  title: '기생충',
  posterUrl: null,
  seasonNumber: null,
  seasonName: null,
  priority: 'MUST',
  requiredMinutes: 132,
  isRuntimeEstimated: false,
  availabilities: [],
  isWatchableNow: false,
  watchedAt: null,
}

function entry(
  id: number,
  name: string,
  status: ServiceStatusEntry['status'],
  dataQuality: 'NORMAL' | 'INSUFFICIENT' = 'NORMAL',
): ServiceStatusEntry {
  return { service: { ottServiceId: id, name, dataQuality }, status }
}

// 펼침 상태는 실제로는 목록(WishlistScreen)이 들고 있다. 행 단독 테스트에서는 같은 규칙의 작은 상태 보관 컴포넌트로 대신한다
function ExpandedStateHost({ children }: { children: (props: RowStateProps) => ReactNode }) {
  const [expandedId, setExpandedId] = useState<number | null>(null)
  return children({
    expandedId,
    onToggleExpanded: (id) => setExpandedId((current) => (current === id ? null : id)),
  })
}
interface RowStateProps {
  expandedId: number | null
  onToggleExpanded: (id: number) => void
}

// 있음 2곳, 없음 1곳, 모름 1곳(쿠팡플레이, 데이터 부족)
function renderCard(entries: ServiceStatusEntry[], items: WishlistListItem[] = [ITEM]) {
  render(
    <MemoryRouter>
      <ExpandedStateHost>
        {(rowState) => (
          <WishlistRow
            {...rowState}
            items={items}
            getCardProps={(item) => ({
              item,
              statusEntries: entries,
              detailSearch: '',
              isBusy: false,
              onChangePriority: vi.fn(),
              onToggleWatched: vi.fn(),
              onRemove: vi.fn(),
            })}
          />
        )}
      </ExpandedStateHost>
    </MemoryRouter>,
  )
}

describe('WishlistRow·WishlistCard 서비스별 시청 가능 여부', () => {
  it('접혀 있을 때는 묶음이 보이지 않고, 펼치면 상태별 세 묶음으로 나뉘어 보인다', async () => {
    renderCard([
      entry(1, '넷플릭스', 'AVAILABLE'),
      entry(2, '웨이브', 'NOT_AVAILABLE'),
      entry(3, '티빙', 'AVAILABLE'),
      entry(4, '쿠팡플레이', 'UNKNOWN', 'INSUFFICIENT'),
    ])
    expect(screen.queryByText('볼 수 있음(있음)')).not.toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: /자세히 보기/ }))

    const available = screen.getByRole('list', { name: /볼 수 있음\(있음\)/ })
    expect(
      within(available)
        .getAllByRole('listitem')
        .map((li) => li.textContent),
    ).toEqual(['넷플릭스있음', '티빙있음'])
    const unavailable = screen.getByRole('list', { name: /볼 수 없음\(없음\)/ })
    expect(within(unavailable).getByText('웨이브')).toBeInTheDocument()
    // 모름 묶음의 쿠팡플레이는 "데이터 부족" 표시를 유지한다
    const unknown = screen.getByRole('list', { name: /확인 안 됨\(모름\)/ })
    expect(within(unknown).getByText('쿠팡플레이')).toBeInTheDocument()
    expect(within(unknown).getByText('데이터 부족')).toBeInTheDocument()
  })

  it('해당하는 서비스가 없는 묶음은 보이지 않는다', async () => {
    renderCard([entry(1, '넷플릭스', 'AVAILABLE'), entry(2, '웨이브', 'AVAILABLE')])
    await userEvent.click(screen.getByRole('button', { name: /자세히 보기/ }))

    expect(screen.getByText('볼 수 있음(있음)')).toBeInTheDocument()
    expect(screen.queryByText('볼 수 없음(없음)')).not.toBeInTheDocument()
    expect(screen.queryByText('확인 안 됨(모름)')).not.toBeInTheDocument()
  })

  it('접힌 상태에서도 한 줄 요약에 볼 수 있는 서비스 이름과 없음·모름 개수가 보인다', () => {
    renderCard([
      entry(1, '넷플릭스', 'AVAILABLE'),
      entry(2, '웨이브', 'NOT_AVAILABLE'),
      entry(3, '티빙', 'AVAILABLE'),
      entry(4, '왓챠', 'NOT_AVAILABLE'),
      entry(5, '쿠팡플레이', 'UNKNOWN', 'INSUFFICIENT'),
    ])
    expect(
      screen.getByText('넷플릭스, 티빙에서 볼 수 있음 · 2곳 없음 · 1곳 모름'),
    ).toBeInTheDocument()
  })

  it('볼 수 있는 곳이 없으면 확인되지 않았다고 알리고, 개수가 0인 항목은 생략한다', () => {
    renderCard([entry(1, '넷플릭스', 'NOT_AVAILABLE'), entry(2, '웨이브', 'NOT_AVAILABLE')])
    expect(screen.getByText('볼 수 있는 곳이 확인되지 않았어요 · 2곳 없음')).toBeInTheDocument()
  })

  it('자세히 보기 버튼은 펼침 상태를 aria-expanded로 알리고 눌러서 접고 펼 수 있다', async () => {
    renderCard([entry(1, '넷플릭스', 'AVAILABLE')])
    const toggle = screen.getByRole('button', { name: /자세히 보기/ })
    expect(toggle).toHaveAttribute('aria-expanded', 'false')

    await userEvent.click(toggle)
    expect(screen.getByRole('button', { name: /자세히 접기/ })).toHaveAttribute(
      'aria-expanded',
      'true',
    )
    expect(screen.getByRole('list', { name: /볼 수 있음\(있음\)/ })).toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: /자세히 접기/ }))
    expect(screen.queryByRole('list', { name: /볼 수 있음\(있음\)/ })).not.toBeInTheDocument()
  })

  it('한 행의 두 카드 중 하나를 펼치면 작품 이름이 붙은 패널이 하나만 열리고 다른 카드는 접힌 채 남는다', async () => {
    const second: WishlistListItem = {
      ...ITEM,
      wishlistItemId: 2,
      tmdbId: 1,
      title: '오징어 게임',
    }
    renderCard([entry(1, '넷플릭스', 'AVAILABLE')], [ITEM, second])

    const toggles = screen.getAllByRole('button', { name: /자세히 보기/ })
    expect(toggles).toHaveLength(2)
    await userEvent.click(toggles[1])

    // 패널은 role=region이며 제목(작품 이름 + 서비스별 시청 가능 여부)으로 이름이 붙는다
    const region = screen.getByRole('region', { name: '오징어 게임의 서비스별 시청 가능 여부' })
    expect(region).toBeInTheDocument()
    expect(screen.getAllByRole('region')).toHaveLength(1)
    // 누른 버튼은 펼침(true), 옆 카드의 버튼은 접힘(false)이고 aria-controls가 패널을 가리킨다
    expect(screen.getByRole('button', { name: /자세히 접기/ })).toHaveAttribute(
      'aria-controls',
      region.id,
    )
    expect(screen.getByRole('button', { name: /자세히 보기/ })).toHaveAttribute(
      'aria-expanded',
      'false',
    )
  })
})
