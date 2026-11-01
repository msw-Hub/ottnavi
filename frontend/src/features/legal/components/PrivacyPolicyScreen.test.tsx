import { render, screen, within } from '@testing-library/react'
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
  it('비로그인으로 /privacy를 열면 시행일·연락처, 한눈에 보기, 업체 표와 주요 구역 제목이 보인다', async () => {
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
    // 현재 시행일은 변경 이력(PRIVACY_REVISIONS)의 맨 뒤 항목 날짜다
    expect(screen.getByText('2026년 11월 2일', { selector: 'time' })).toHaveAttribute(
      'dateTime',
      '2026-11-02',
    )

    // 2-0. 변경 이력 네 줄: 알림 메일 변경(10/30), 방문 통계·서버 로그 보관 추가(10/31), 동의 방식 변경(11/1),
    //      업체별 표·새 절 추가(현재 시행일 11/2).
    //      각 줄의 날짜는 그 변경의 날짜로 고정되어 있어야 한다(시행일이 바뀌어도 앞 줄이 따라 바뀌면 안 된다).
    //      자리표시자가 화면에 남지 않았는지도 함께 확인한다(배포 전 빈 값이 노출되는 사고 방지)
    expect(screen.getByText(/변경: 알림 메일 제공에 맞춰/).textContent).toContain(
      '2026년 10월 30일',
    )
    expect(screen.getByText(/변경: 방문\s*통계\(Google Analytics\)/).textContent).toContain(
      '2026년 10월 31일',
    )
    expect(screen.getByText(/변경: 방문 통계 수집을\s*동의를 받은 경우에만/).textContent).toContain(
      '2026년 11월 1일',
    )
    expect(screen.getByText(/변경: 처리 위탁·해외 이전을\s*업체별 표로/).textContent).toContain(
      '2026년 11월 2일',
    )
    expect(screen.queryByText(/시행일 확정 필요/)).not.toBeInTheDocument()
    // 지금은 이전 방침 전문을 두지 않아 "이전 방침 보기" 링크가 없다
    expect(screen.queryByRole('link', { name: '이전 방침 보기' })).not.toBeInTheDocument()
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

    // 2-3. 동의 방식 문구: 동의를 받은 경우에만 수집, 거부해도 제한 없음, 6개월 뒤 다시 묻기, 변경 이력 한 줄
    expect(screen.getByText(/방문 통계 수집에 동의한 경우에만 수집합니다/)).toBeInTheDocument()
    expect(
      screen.getByText(/거부하거나 동의를 철회하면 그 시점부터 6개월 뒤에 한 번 다시 여쭙니다/),
    ).toBeInTheDocument()
    expect(screen.getByText(/변경: 방문 통계 수집을\s*동의를 받은 경우에만/)).toBeInTheDocument()
    // 목업(측정 ID 없음)에서는 설정 카드가 없다
    expect(screen.queryByRole('heading', { name: '방문 통계 설정' })).not.toBeInTheDocument()

    // 3. 주요 구역 제목. 10~12절이 새로 생겨 "방침의 변경"은 13절이 된다(옛 "10. 방침의 변경"은 없다)
    for (const name of [
      '1. 처리하는 개인정보',
      '3. 개인정보를 이용하는 목적',
      '5. 보관 기간과 파기',
      '6. 처리 위탁과 해외 이전',
      '9. 개인정보 보호책임자',
      '10. 개인정보의 안전성 확보 조치',
      '11. 권익침해 구제방법',
      '12. 만 14세 미만 아동',
      '13. 방침의 변경',
    ]) {
      expect(screen.getByRole('heading', { level: 2, name })).toBeInTheDocument()
    }
    expect(screen.queryByRole('heading', { name: '10. 방침의 변경' })).not.toBeInTheDocument()

    // 같은 렌더에서 이어서 확인한다(실제 라우터의 lazy 경로는 한 테스트 파일에서 두 번 렌더하면 다시 찾지 못해서다)
    // 4. 맨 위 한눈에 보기 단락
    const summary = screen.getByRole('region', { name: '한눈에 보기' })
    expect(within(summary).getByText(/일부는 해외에서 처리됩니다\(6절\)/)).toBeInTheDocument()

    // 5. 6절 표: 업체 6곳이 행 머리로 읽히고, Grafana 줄에는 서버 위치(일본)가 들어간다
    const table = screen.getByRole('table', { name: '처리 위탁 업체와 처리하는 정보' })
    expect(
      within(table)
        .getAllByRole('rowheader')
        .map((cell) => cell.textContent),
    ).toEqual(['Supabase', 'Redis Cloud', 'Cloudtype', 'Vercel', 'Google', 'Grafana Labs'])
    expect(within(table).getByText('Raintank, Inc.(미국). 서버(스택)는 일본')).toBeInTheDocument()

    // 6. 업체별 공식 개인정보 안내는 새 탭으로 연다
    const googleLink = screen.getByRole('link', { name: /^Google\s*\(새 탭에서 열림\)$/ })
    expect(googleLink).toHaveAttribute('href', 'https://policies.google.com/privacy')
    expect(googleLink).toHaveAttribute('target', '_blank')
    expect(googleLink).toHaveAttribute('rel', 'noopener noreferrer')

    // 7. 8절 권리 처리 기한(예전의 "지체 없이 처리합니다" 문장은 없다)
    expect(screen.getByText(/열람 요구는 받은 날부터 10일 이내에 처리하고/)).toBeInTheDocument()
    expect(screen.queryByText(/요청을 받으면 지체 없이 처리합니다/)).not.toBeInTheDocument()
  })
})
