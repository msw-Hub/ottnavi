import { cleanup, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import type { AdminUserStats, AdminVisitorStats } from '@/api/generated/model'
import { UserStatsCard } from '@/features/admin/components/monitoring/UserStatsCard'
import { VisitorStatsCard } from '@/features/admin/components/monitoring/VisitorStatsCard'

/*
 * 모니터링 "운영 현황" 카드(회원 현황·방문자 수) 테스트.
 * 확인하는 것: 로딩·오류·성공 모양, 방문자 세 상태(OK / DISABLED / UNAVAILABLE)가 글자로 구분되는지,
 * 값이 없는(null) 칸이 "-"로 보이는지.
 */
afterEach(() => cleanup())

const USER_STATS: AdminUserStats = {
  totalUserCount: 1234,
  todayNewUserCount: 1,
  monthNewUserCount: 9,
  activePlanUserCount: 17,
  notificationEnabledUserCount: 0,
  measuredAt: '2026-11-01T10:30:00+09:00',
}

const VISITOR_OK: AdminVisitorStats = {
  status: 'OK',
  todayVisitorCount: 12,
  yesterdayVisitorCount: 31,
  last7DaysVisitorCount: 118,
  last30DaysVisitorCount: 402,
  fetchedAt: '2026-11-01T10:30:00+09:00',
}

const VISITOR_WITHOUT_NUMBERS: Omit<AdminVisitorStats, 'status'> = {
  todayVisitorCount: null,
  yesterdayVisitorCount: null,
  last7DaysVisitorCount: null,
  last30DaysVisitorCount: null,
  fetchedAt: null,
}

describe('회원 현황 카드', () => {
  it('불러오는 중에는 상태 문장이 있고 숫자는 없다', () => {
    render(<UserStatsCard status="pending" />)
    expect(screen.getByRole('status')).toHaveTextContent('회원 현황을 불러오는 중입니다')
    expect(screen.queryByText('가입 회원')).not.toBeInTheDocument()
  })

  it('실패하면 오류 안내와 다시 시도 버튼이 보이고, 누르면 onRetry가 불린다', async () => {
    const onRetry = vi.fn()
    render(<UserStatsCard status="error" error={new Error('x')} onRetry={onRetry} />)
    expect(screen.getByRole('alert')).toHaveTextContent('불러오지 못했습니다')
    await userEvent.click(screen.getByRole('button', { name: /다시 시도/ }))
    expect(onRetry).toHaveBeenCalledTimes(1)
  })

  it('성공하면 숫자와 기준 시각이 보이고 0도 "0명"으로 보인다', () => {
    render(<UserStatsCard status="success" stats={USER_STATS} />)
    expect(screen.getByText('1,234명')).toBeInTheDocument()
    expect(screen.getByText('오늘 신규').nextSibling).toHaveTextContent('1명')
    expect(screen.getByText('알림 켠 회원').nextSibling).toHaveTextContent('0명')
    expect(screen.getByText('2026-11-01 10:30')).toBeInTheDocument()
  })
})

describe('방문자 카드', () => {
  it('불러오는 중에는 상태 문장이 보인다', () => {
    render(<VisitorStatsCard status="pending" />)
    expect(screen.getByRole('status')).toHaveTextContent('방문자 수를 불러오는 중입니다')
  })

  it('우리 서버 요청이 실패하면 오류 안내가 보인다', () => {
    render(<VisitorStatsCard status="error" error={new Error('x')} />)
    expect(screen.getByRole('alert')).toHaveTextContent('불러오지 못했습니다')
  })

  it('OK이면 네 칸의 숫자와 "잠정" 태그가 보인다', () => {
    render(<VisitorStatsCard status="success" stats={VISITOR_OK} />)
    expect(screen.getByText('12명')).toBeInTheDocument()
    expect(screen.getByText('31명')).toBeInTheDocument()
    expect(screen.getByText('118명')).toBeInTheDocument()
    expect(screen.getByText('402명')).toBeInTheDocument()
    expect(screen.getByText('잠정')).toBeInTheDocument()
  })

  it('OK여도 값이 null인 칸은 "-"로 보인다', () => {
    render(
      <VisitorStatsCard
        status="success"
        stats={{ ...VISITOR_OK, yesterdayVisitorCount: null, last30DaysVisitorCount: null }}
      />,
    )
    expect(screen.getByText('어제').nextSibling).toHaveTextContent('-')
    expect(screen.getByText('최근 30일').nextSibling).toHaveTextContent('-')
    expect(screen.queryByText(/-명/)).not.toBeInTheDocument()
  })

  it('DISABLED이면 "연동 안 됨" 글자가 보이고 숫자와 다시 시도 버튼은 없다', () => {
    render(
      <VisitorStatsCard
        status="success"
        stats={{ status: 'DISABLED', ...VISITOR_WITHOUT_NUMBERS }}
        onRetry={vi.fn()}
      />,
    )
    expect(screen.getByText('연동 안 됨')).toBeInTheDocument()
    expect(screen.queryByText('잠정')).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /다시 시도/ })).not.toBeInTheDocument()
  })

  it('UNAVAILABLE이면 "지금 불러올 수 없음" 글자와 다시 시도 버튼이 보이고 누르면 onRetry가 불린다', async () => {
    const onRetry = vi.fn()
    render(
      <VisitorStatsCard
        status="success"
        stats={{ status: 'UNAVAILABLE', ...VISITOR_WITHOUT_NUMBERS }}
        onRetry={onRetry}
      />,
    )
    expect(screen.getByText('지금 불러올 수 없음')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: /다시 시도/ }))
    expect(onRetry).toHaveBeenCalledTimes(1)
  })
})
