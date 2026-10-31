import { cleanup, render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import { afterEach, describe, expect, it } from 'vitest'
import { MonitoringScreenView } from '@/features/admin/components/monitoring/MonitoringScreenView'
import type { MonitoringLinkUrls } from '@/features/admin/components/monitoring/grafanaLink'

/*
 * 관리자 모니터링 화면(Task 097, SCR-17) 테스트.
 * 확인하는 것: 항목마다 주소 유무에 따라 비활성 "준비 중" / 새 탭 링크가 따로 정해지는지, 수집 탭 링크.
 */
afterEach(() => cleanup())

const EMPTY_URLS: MonitoringLinkUrls = {
  grafanaDashboard: '',
  grafanaExplore: '',
  grafanaAlertRules: '',
  cloudtypeConsole: '',
}

// 바로가기 카드에 앱 안 링크(수집 탭)가 있어 라우터 안에서 그린다
function renderView(urls: MonitoringLinkUrls) {
  render(
    <MemoryRouter>
      <MonitoringScreenView urls={urls} />
    </MemoryRouter>,
  )
}

describe('모니터링 화면의 바로가기', () => {
  it('주소가 모두 비어 있으면 네 항목 모두 비활성 "준비 중"이고 바깥 링크는 없다', () => {
    renderView(EMPTY_URLS)
    for (const name of [
      'Grafana 대시보드 준비 중',
      'Grafana 로그 탐색 준비 중',
      'Grafana 알림 규칙 준비 중',
      'Cloudtype 콘솔 준비 중',
    ]) {
      const button = screen.getByRole('button', { name })
      expect(button).toBeDisabled()
      // 비활성 이유 문장이 연결돼 있어야 "왜 안 눌리지"가 남지 않는다
      expect(button).toHaveAccessibleDescription(/주소가 아직 등록되지 않았습니다/)
    }
    expect(screen.queryByRole('link', { name: /열기/ })).not.toBeInTheDocument()
  })

  it('주소가 있는 항목만 새 탭 링크가 되고 나머지는 준비 중으로 남는다', () => {
    renderView({ ...EMPTY_URLS, grafanaDashboard: 'https://example.grafana.net/d/abc' })
    const link = screen.getByRole('link', { name: /Grafana 대시보드 열기/ })
    expect(link).toHaveAttribute('href', 'https://example.grafana.net/d/abc')
    expect(link).toHaveAttribute('target', '_blank')
    expect(link).toHaveAttribute('rel', expect.stringContaining('noopener'))
    expect(screen.getByRole('button', { name: 'Grafana 로그 탐색 준비 중' })).toBeDisabled()
  })

  it('공백만 있는 주소는 비어 있는 것으로 보고 준비 중으로 남는다', () => {
    renderView({ ...EMPTY_URLS, grafanaDashboard: '   ' })
    expect(screen.getByRole('button', { name: 'Grafana 대시보드 준비 중' })).toBeDisabled()
    expect(screen.queryByRole('link', { name: /Grafana 대시보드 열기/ })).not.toBeInTheDocument()
  })

  it('수집 실행 결과 줄은 주소 상수와 상관없이 수집 탭으로 이어진다', () => {
    renderView(EMPTY_URLS)
    expect(screen.getByRole('link', { name: '수집 탭' })).toHaveAttribute('href', '/admin/collect')
  })
})
