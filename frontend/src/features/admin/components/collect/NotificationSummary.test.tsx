import { cleanup, render, screen } from '@testing-library/react'
import { setupServer } from 'msw/node'
import { afterAll, afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest'
import { Providers } from '@/app/providers'
import { NotificationSummary } from '@/features/admin/components/collect/NotificationSummary'
import { handlers } from '@/mocks/handlers'

/*
 * 관리자 수집 화면의 알림 발송 상태 요약 테스트(Task 071, SCR-16). API는 앱의 목업 핸들러(MSW)다.
 * 확인하는 것: 건수가 "아이콘 + 글자"(대기 N건 / 발송 N건 / 실패 N건)로 보이고 마지막 발송 시각과 기간이 함께 나오는지, 오류 안내.
 * 시나리오(?mockScenario=)는 목업 모드의 요청 인터셉터가 window 주소에서 읽는다.
 */
const server = setupServer(...handlers)

beforeAll(() => server.listen({ onUnhandledFrame: 'error' }))
beforeEach(() => vi.stubEnv('VITE_USE_MOCK', 'true'))
afterEach(() => {
  cleanup()
  server.resetHandlers()
  vi.unstubAllEnvs()
  window.history.pushState({}, '', '/')
})
afterAll(() => server.close())

function renderSummary(scenario?: string) {
  window.history.pushState({}, '', scenario ? `/?mockScenario=${scenario}` : '/')
  render(
    <Providers>
      <NotificationSummary />
    </Providers>,
  )
}

describe('알림 발송 상태 요약', () => {
  it('최근 7일의 대기·발송·실패 건수와 마지막 발송 시각이 글자로 보인다', async () => {
    renderSummary()

    expect(await screen.findByText('발송 41건')).toBeInTheDocument()
    expect(screen.getByText('대기 2건')).toBeInTheDocument()
    expect(screen.getByText('실패 1건')).toBeInTheDocument()
    expect(screen.getByText('최근 7일 기준')).toBeInTheDocument()
    expect(screen.getByText('마지막 발송: 2026-11-01 03:51')).toBeInTheDocument()
  })

  it('알림이 없으면 건수는 0건이고 마지막 발송은 아직 없음으로 보인다', async () => {
    renderSummary('empty')

    expect(await screen.findByText('발송 0건')).toBeInTheDocument()
    expect(screen.getByText('마지막 발송: 아직 없음')).toBeInTheDocument()
  })

  it('조회에 실패하면 오류 문장과 다시 시도가 보인다', async () => {
    renderSummary('error')

    expect(await screen.findByRole('alert')).toHaveTextContent('일시적인 오류가 발생했습니다')
    expect(screen.getByRole('button', { name: '다시 시도' })).toBeInTheDocument()
  })
})
