import { render, screen } from '@testing-library/react'
import { createMemoryRouter, RouterProvider, type RouteObject } from 'react-router'
import { describe, expect, it } from 'vitest'
import { Providers } from '@/app/providers'
import { routes } from '@/app/router'

/*
 * 화면 주소표(TASK018_031_PLAN 5절)를 테스트 쪽에 따로 적어 두고 실제 라우터 설정과 맞춰 본다.
 * - pattern: 라우터에 등록돼 있어야 하는 경로 패턴
 * - url: 실제로 열어 볼 주소(동적 경로는 예시 값을 채운다)
 * - heading: 그 화면이 그려졌다는 증거로 찾을 제목(h1)
 * 라우터에서 경로를 빠뜨리면 ① 등록 경로 비교가 실패하고 ② 그 주소가 404 화면으로 가서 제목 확인도 실패한다.
 */
const ROUTE_TABLE = [
  { pattern: '/', url: '/', heading: '작품 검색' }, // SCR-01
  { pattern: '/titles/:mediaType/:tmdbId', url: '/titles/movie/550', heading: '작품 상세' }, // SCR-02
  { pattern: '/exclusives', url: '/exclusives', heading: '독점 작품 목록' }, // SCR-03
  { pattern: '/about', url: '/about', heading: 'OTT내비 소개' }, // SCR-04
  { pattern: '/accuracy', url: '/accuracy', heading: '데이터 정확도 안내' }, // SCR-05
  { pattern: '/login', url: '/login', heading: '로그인' }, // SCR-06
  { pattern: '/auth/callback', url: '/auth/callback', heading: '로그인 처리 중' }, // SCR-06 콜백
  { pattern: '/settings', url: '/settings', heading: '내 설정' }, // SCR-07
  { pattern: '/wishlist', url: '/wishlist', heading: '찜 목록' }, // SCR-08
  { pattern: '/pricing', url: '/pricing', heading: '요금 확인' }, // SCR-09
  { pattern: '/plan', url: '/plan', heading: '플랜 결과' }, // SCR-10
  { pattern: '/plan/history', url: '/plan/history', heading: '플랜 변경 내역' }, // SCR-11
  { pattern: '/settings/notifications', url: '/settings/notifications', heading: '알림 설정' }, // SCR-12
  { pattern: '/settings/withdraw', url: '/settings/withdraw', heading: '회원 탈퇴' }, // SCR-13
  { pattern: '/admin/products', url: '/admin/products', heading: '상품·가격 관리' }, // SCR-14
  { pattern: '/admin/providers', url: '/admin/providers', heading: '제공처 매핑' }, // SCR-15
  { pattern: '/admin/collect', url: '/admin/collect', heading: '수집 관리' }, // SCR-16
  { pattern: '/admin/monitoring', url: '/admin/monitoring', heading: '모니터링' }, // SCR-17
  { pattern: '/admin/llm', url: '/admin/llm', heading: 'LLM 검토' }, // SCR-18
  { pattern: '/*', url: '/no-such-page', heading: '페이지를 찾을 수 없음' }, // 404
]

// 라우트 설정을 따라 내려가며 "화면이 그려지는 끝 라우트(자식 없음)"의 전체 경로 패턴을 모은다.
// 경로 없는 부모(가드·오류 경계)는 경로를 더하지 않고, index 라우트는 부모 경로를 그대로 쓴다.
function collectLeafPatterns(routeObjects: RouteObject[], parentPath = ''): string[] {
  return routeObjects.flatMap((route) => {
    const fullPath = route.path
      ? route.path.startsWith('/')
        ? route.path
        : `${parentPath.replace(/\/$/, '')}/${route.path}`
      : parentPath
    if (route.children?.length) return collectLeafPatterns(route.children, fullPath)
    return [fullPath || '/']
  })
}

// 메모리 라우터(브라우저 주소 대신 메모리에 주소를 두는 라우터)로 지정한 주소를 연다. 실제 앱과 같은 Providers로 감싼다.
function renderAt(url: string) {
  const router = createMemoryRouter(routes, { initialEntries: [url] })
  render(
    <Providers>
      <RouterProvider router={router} />
    </Providers>,
  )
}

describe('라우터 경로표', () => {
  it('라우터에 등록된 화면 경로가 경로표와 정확히 같다', () => {
    expect(collectLeafPatterns(routes).sort()).toEqual(ROUTE_TABLE.map((row) => row.pattern).sort())
  })

  it.each(ROUTE_TABLE)('$url 주소가 "$heading" 화면을 그린다', async ({ url, heading }) => {
    renderAt(url)
    // 페이지는 lazy로 불러오므로 바로 나타나지 않는다. findBy는 나타날 때까지 기다린다
    expect(await screen.findByRole('heading', { level: 1, name: heading })).toBeInTheDocument()
  })
})
