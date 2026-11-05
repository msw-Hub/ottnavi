import { cleanup, render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import { setupServer } from 'msw/node'
import { afterAll, afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest'
import { Providers } from '@/app/providers'
import {
  DEMO_MONITORING_LINK_URLS,
  GRAFANA_PUBLIC_DASHBOARD_URL,
  MONITORING_LINK_URLS,
} from '@/features/admin/components/monitoring/grafanaLink'
import { MonitoringScreen } from '@/features/admin/components/monitoring/MonitoringScreen'
import { handlers } from '@/mocks/handlers'
import { resetMockSession, setMockRole } from '@/mocks/mockSession'

/*
 * 관리자 모니터링 화면 연결 테스트(SCR-17). API는 앱의 목업 핸들러(MSW)다.
 * 확인하는 것: 관리자 역할에서 회원 현황·방문자·메일 발송 숫자가 실제로 그려지는지,
 * 방문자 카드만 연동 안 됨/오류여도 다른 카드는 숫자를 그대로 보이는지.
 * 시나리오(?mockScenario=)는 목업 모드의 요청 인터셉터가 window 주소에서 읽는다.
 */
const server = setupServer(...handlers)

beforeAll(() => server.listen({ onUnhandledFrame: 'error' }))
beforeEach(() => {
  vi.stubEnv('VITE_USE_MOCK', 'true')
  setMockRole('ADMIN')
})
afterEach(() => {
  cleanup()
  server.resetHandlers()
  vi.unstubAllEnvs()
  resetMockSession()
  window.history.pushState({}, '', '/')
})
afterAll(() => server.close())

function renderScreen(scenario?: string) {
  window.history.pushState({}, '', scenario ? `/?mockScenario=${scenario}` : '/')
  render(
    <Providers>
      <MemoryRouter>
        <MonitoringScreen />
      </MemoryRouter>
    </Providers>,
  )
}

describe('모니터링 바로가기 주소', () => {
  it('값이 있는 주소는 https이고 토큰·비밀번호처럼 보이는 값이 붙지 않는다', () => {
    for (const url of [
      ...Object.values(MONITORING_LINK_URLS),
      ...Object.values(DEMO_MONITORING_LINK_URLS),
    ]) {
      if (url === '') continue
      expect(url).toMatch(/^https:\/\//)
      expect(url).not.toMatch(/token|password|passwd|secret|api_?key|auth=/i)
    }
  })

  it('연결된 바깥 도구(대시보드·로그 탐색·알림 규칙·Cloudtype)는 새 탭 링크다', async () => {
    window.history.pushState({}, '', '/')
    render(
      <Providers>
        <MemoryRouter>
          <MonitoringScreen />
        </MemoryRouter>
      </Providers>,
    )

    const dashboard = await screen.findByRole('link', { name: /Grafana 대시보드 열기/ })
    expect(dashboard).toHaveAttribute(
      'href',
      expect.stringContaining('ottnavi.grafana.net/d/ottnavi-overview'),
    )
    expect(dashboard).toHaveAttribute('target', '_blank')
    expect(screen.getByRole('link', { name: /Grafana 로그 탐색 열기/ })).toHaveAttribute(
      'href',
      expect.stringContaining('ottnavi.grafana.net/explore'),
    )
    expect(screen.getByRole('link', { name: /Cloudtype 콘솔 열기/ })).toHaveAttribute(
      'href',
      expect.stringContaining('app.cloudtype.io'),
    )
    expect(screen.getByRole('link', { name: /Grafana 알림 규칙 열기/ })).toHaveAttribute(
      'href',
      expect.stringContaining('ottnavi.grafana.net/alerting/list'),
    )
  })
})

describe('데모 계정의 모니터링 바로가기', () => {
  it('대시보드는 읽기 전용 공개 주소로 열리고 로그·알림·콘솔은 비활성 "관리자 전용"이다', async () => {
    setMockRole('DEMO')
    renderScreen()

    const dashboard = await screen.findByRole('link', { name: /Grafana 대시보드 열기/ })
    expect(dashboard).toHaveAttribute('href', GRAFANA_PUBLIC_DASHBOARD_URL)
    expect(dashboard).toHaveAttribute('target', '_blank')

    for (const name of [
      'Grafana 로그 탐색 관리자 전용',
      'Grafana 알림 규칙 관리자 전용',
      'Cloudtype 콘솔 관리자 전용',
    ]) {
      const button = screen.getByRole('button', { name })
      expect(button).toBeDisabled()
      expect(button).toHaveAccessibleDescription(/데모 계정은 지표 대시보드만 볼 수 있습니다/)
    }
    // 비공개 주소가 데모 화면 어디에도 링크로 남지 않는다
    expect(screen.queryByRole('link', { name: /Grafana 로그 탐색 열기/ })).not.toBeInTheDocument()
    expect(screen.queryByRole('link', { name: /Cloudtype 콘솔 열기/ })).not.toBeInTheDocument()
    expect(screen.queryByRole('link', { name: /Grafana 알림 규칙 열기/ })).not.toBeInTheDocument()
    // 앱 안 이동(수집 탭)은 그대로다
    expect(screen.getByRole('link', { name: '수집 탭' })).toHaveAttribute('href', '/admin/collect')
  })

  it('관리자에게는 안내 문구와 "관리자 전용" 표시가 없다', async () => {
    renderScreen()

    expect(await screen.findByRole('link', { name: /Grafana 로그 탐색 열기/ })).toBeInTheDocument()
    expect(screen.queryByText(/데모 계정은 지표 대시보드만/)).not.toBeInTheDocument()
    expect(screen.queryByText(/관리자 전용/)).not.toBeInTheDocument()
  })
})

describe('모니터링 화면의 운영 현황', () => {
  it('관리자에게 회원 현황·방문자 수·메일 발송 숫자가 모두 보인다', async () => {
    renderScreen()

    expect(await screen.findByText('42명')).toBeInTheDocument()
    expect(screen.getByText('활성 플랜 보유').nextSibling).toHaveTextContent('17명')
    expect(await screen.findByText('402명')).toBeInTheDocument()
    expect(await screen.findByText('발송 41건')).toBeInTheDocument()
  })

  it('방문 통계가 연동 안 됨이어도 회원 현황은 그대로 보인다', async () => {
    renderScreen('visitor-disabled')

    expect(await screen.findByText('연동 안 됨')).toBeInTheDocument()
    expect(await screen.findByText('42명')).toBeInTheDocument()
  })

  it('방문 통계를 일시적으로 못 받아도 다시 시도 버튼이 보이고 회원 현황은 그대로 보인다', async () => {
    renderScreen('visitor-unavailable')

    expect(await screen.findByText('지금 불러올 수 없음')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /다시 시도/ })).toBeInTheDocument()
    expect(await screen.findByText('42명')).toBeInTheDocument()
  })

  it('방문자 요청만 실패하면 방문자 카드만 오류 안내를 보인다', async () => {
    renderScreen('visitor-error')

    expect(await screen.findByText('불러오지 못했습니다')).toBeInTheDocument()
    expect(await screen.findByText('42명')).toBeInTheDocument()
  })
})
