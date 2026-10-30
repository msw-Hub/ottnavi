import { render, screen } from '@testing-library/react'
import { setupServer } from 'msw/node'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { afterAll, afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest'
import { Providers } from '@/app/providers'
import { routes } from '@/app/router'
import { handlers } from '@/mocks/handlers'
import { resetMockSession, setMockRole } from '@/mocks/mockSession'

/*
 * 개인정보처리방침 화면 테스트.
 * 확인하는 것: 로그인하지 않아도 실제 라우터로 /privacy가 열리고, 시행일·운영자 이름·문의 이메일과 주요 구역 제목이 보이는지.
 * 막으려는 사고: 방침 주소가 로그인 가드 안에 들어가 Google 심사·방문자가 볼 수 없게 되는 것, 연락처가 빠지는 것.
 */

const server = setupServer(...handlers)

beforeAll(() => server.listen({ onUnhandledFrame: 'error' })) // 핸들러 없는 요청이 실제 네트워크로 나가지 않게 막는다
beforeEach(() => {
  vi.stubEnv('VITE_USE_MOCK', 'true')
  setMockRole('GUEST') // 비로그인 방문자로 연다
})
afterEach(() => {
  server.resetHandlers()
  vi.unstubAllEnvs()
  resetMockSession()
})
afterAll(() => server.close())

describe('개인정보처리방침 화면', () => {
  it('비로그인으로 /privacy를 열면 시행일·연락처와 주요 구역 제목이 보인다', async () => {
    const router = createMemoryRouter(routes, { initialEntries: ['/privacy'] })
    render(
      <Providers>
        <RouterProvider router={router} />
      </Providers>,
    )

    // 1. 페이지는 lazy로 불러오므로 제목이 나타날 때까지 기다린다(로그인 화면으로 보내지지 않았다는 증거)
    expect(
      await screen.findByRole('heading', { level: 1, name: '개인정보처리방침' }),
    ).toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/privacy')

    // 2. 시행일(한국어 날짜)과 운영자·연락처
    expect(screen.getByText('2026년 10월 31일', { selector: 'time' })).toHaveAttribute(
      'dateTime',
      '2026-10-31',
    )

    // 2-0. 변경 이력 두 줄의 날짜: 알림 메일 변경은 이전 시행일(10/30), 방문 통계 변경은 현재 시행일(10/31)이다.
    //      자리표시자가 화면에 남지 않았는지도 함께 확인한다(배포 전 빈 값이 노출되는 사고 방지)
    expect(screen.getByText('2026년 10월 30일')).toBeInTheDocument()
    expect(screen.getByText(/변경: 알림 메일 제공에 맞춰/).textContent).toContain(
      '2026년 10월 30일',
    )
    expect(screen.getByText(/변경: 방문\s*통계\(Google Analytics\)/).textContent).toContain(
      '2026년 10월 31일',
    )
    expect(screen.queryByText(/시행일 확정 필요/)).not.toBeInTheDocument()
    expect(screen.getByText(/문선우/)).toBeInTheDocument()

    // 2-1. 알림 메일 제공에 맞춘 문구: 예전의 "아직 보내지 않습니다" 문구는 없고, 직접 켠 경우에만 보낸다는 안내가 있다
    expect(screen.queryByText(/아직 알림 메일을 보내지 않습니다/)).not.toBeInTheDocument()
    // "알림 메일:"만 굵은 글자(span)로 나뉘어 있어 나머지 문장으로 찾는다
    expect(screen.getByText(/이용자가 알림 설정에서 직접 켠 경우에만/)).toBeInTheDocument()
    expect(
      screen.getByText(/확정한 플랜의 작품이 배정된 서비스에서 빠졌을 때의 변경 안내/),
    ).toBeInTheDocument()
    expect(screen.getAllByRole('link', { name: 'dalsw7@gmail.com' })[0]).toHaveAttribute(
      'href',
      'mailto:dalsw7@gmail.com',
    )

    // 2-2. 방문 통계(Google Analytics)·서버 로그 보관 문구: 예전의 "추적 도구를 쓰지 않습니다" 문구는 없다
    expect(
      screen.queryByText(/별도의 광고·행동 추적 도구를 사용하지 않습니다/),
    ).not.toBeInTheDocument()
    expect(screen.getByText(/방문 통계 목적으로만 Google Analytics를 씁니다/)).toBeInTheDocument()
    expect(screen.getByText(/방문 통계 쿠키\(Google Analytics\)/)).toBeInTheDocument()
    expect(screen.getByText(/Grafana Cloud\(Grafana Labs\)에 보관합니다/)).toBeInTheDocument()
    expect(screen.getByText(/Grafana Labs\(Grafana Cloud\): 서비스 운영 상태/)).toBeInTheDocument()

    // 3. 주요 구역 제목
    for (const name of [
      '1. 처리하는 개인정보',
      '3. 개인정보를 이용하는 목적',
      '5. 보관 기간과 파기',
      '6. 처리 위탁과 해외 이전',
      '9. 개인정보 보호책임자',
    ]) {
      expect(screen.getByRole('heading', { level: 2, name })).toBeInTheDocument()
    }
  })
})
