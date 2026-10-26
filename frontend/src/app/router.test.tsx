import { render, screen, within } from '@testing-library/react'
import { createMemoryRouter, RouterProvider, type RouteObject } from 'react-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { Providers } from '@/app/providers'
import { routes } from '@/app/router'
import { resetMockSession, setMockRole } from '@/mocks/mockSession'

/*
 * 화면 주소표를 테스트 쪽에 따로 적어 두고 실제 라우터 설정과 맞춰 본다.
 * - pattern: 라우터에 등록돼 있어야 하는 경로 패턴
 * - url: 실제로 열어 볼 주소(동적 경로는 예시 값을 채운다)
 * - heading: 그 화면이 그려졌다는 증거로 찾을 제목(h1)
 * 라우터에서 경로를 빠뜨리면 ① 등록 경로 비교가 실패하고 ② 그 주소가 404 화면으로 가서 제목 확인도 실패한다.
 */
const ROUTE_TABLE = [
  // 홈 1차안: `/`는 홈, 검색(SCR-01)은 `/search`로 옮겼다. 랭킹·탐색은 홈 "더보기" 목적지다
  { pattern: '/', url: '/', heading: '찜한 작품, 어느 달에 어디서 볼지 계산해 드려요' }, // 홈
  { pattern: '/search', url: '/search', heading: '작품 검색' }, // SCR-01
  { pattern: '/ranking', url: '/ranking', heading: '랭킹' }, // 홈 1차안
  { pattern: '/explore', url: '/explore', heading: '탐색' }, // 홈 1차안
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
  { pattern: '/admin/products', url: '/admin/products', heading: 'OTT 구독상품 관리' }, // SCR-14
  { pattern: '/admin/providers', url: '/admin/providers', heading: '제공처 매핑' }, // SCR-15
  { pattern: '/admin/collect', url: '/admin/collect', heading: '수집 실행·이력' }, // SCR-16
  { pattern: '/admin/monitoring', url: '/admin/monitoring', heading: '모니터링' }, // SCR-17
  { pattern: '/admin/llm', url: '/admin/llm', heading: 'LLM 검토' }, // SCR-18
  { pattern: '/*', url: '/no-such-page', heading: '페이지를 찾을 수 없음' }, // 404
]

/*
 * 개발 모드에서만 등록되는 확인용 화면(app/router.tsx의 DEV_ONLY_ROUTES). 운영 화면이 아니라 위 경로표와 나눠 적는다.
 * Vitest는 개발 모드(import.meta.env.DEV = true)로 돌아 이 경로도 등록되므로, DEV일 때만 경로표에 합쳐 비교한다.
 * 이렇게 하면 ① 확인용 화면이 실제로 그려지는지도 함께 검사하고 ② 운영 화면 경로표는 그대로 유지된다.
 */
const DEV_ROUTE_TABLE = [
  { pattern: '/__dev/components', url: '/__dev/components', heading: '공통 컴포넌트 확인' }, // Task 026
]

/*
 * 다른 주소로 보내기만 하는 라우트(홈 1차안: `/admin` → `/admin/collect`). 화면 경로표와 나눠 적는다.
 * 위 표처럼 주소를 열어 확인하지 않는 이유: 보내진 주소(`/admin/collect`)는 위 표에서 이미 한 번 열려, 같은 routes로 다시 열면
 * lazy 라우트 객체가 고쳐진 탓에 "No route matches"가 난다(아래 주석의 관찰과 같다). 그래서 라우트 설정의 보내는 곳을 직접 확인한다.
 */
const REDIRECT_TABLE = [{ pattern: '/admin', to: '/admin/collect' }]

// 경로 비교용 전체 패턴(화면 + 보내기)
const EXPECTED_PATTERNS = [
  ...(import.meta.env.DEV ? [...ROUTE_TABLE, ...DEV_ROUTE_TABLE] : ROUTE_TABLE),
  ...REDIRECT_TABLE,
].map((row) => row.pattern)

const EXPECTED_ROUTE_TABLE = import.meta.env.DEV
  ? [...ROUTE_TABLE, ...DEV_ROUTE_TABLE]
  : ROUTE_TABLE

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

// 전체 경로 패턴(collectLeafPatterns와 같은 규칙)이 pattern인 끝 라우트를 찾는다
function findRouteByPattern(
  routeObjects: RouteObject[],
  pattern: string,
  parentPath = '',
): RouteObject | undefined {
  for (const route of routeObjects) {
    const fullPath = route.path
      ? route.path.startsWith('/')
        ? route.path
        : `${parentPath.replace(/\/$/, '')}/${route.path}`
      : parentPath
    if (route.children?.length) {
      const found = findRouteByPattern(route.children, pattern, fullPath)
      if (found) return found
    } else if ((fullPath || '/') === pattern) {
      return route
    }
  }
  return undefined
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

// 보호·관리자 화면까지 그려지는지 보려고 목업 모드의 관리자로 연다(가드 동작 자체는 guards/guards.test.tsx가 확인한다)
beforeEach(() => {
  vi.stubEnv('VITE_USE_MOCK', 'true')
  setMockRole('ADMIN')
})
afterEach(() => {
  vi.unstubAllEnvs()
  resetMockSession()
})

describe('라우터 경로표', () => {
  it('라우터에 등록된 화면 경로가 경로표와 정확히 같다', () => {
    expect(collectLeafPatterns(routes).sort()).toEqual([...EXPECTED_PATTERNS].sort())
  })

  it.each(REDIRECT_TABLE)('$pattern 주소는 $to로 보낸다', ({ pattern, to }) => {
    const element = findRouteByPattern(routes, pattern)?.element as
      { props?: { to?: string; replace?: boolean } } | undefined
    expect(element?.props).toMatchObject({ to, replace: true })
  })

  it.each(EXPECTED_ROUTE_TABLE)(
    '$url 주소가 "$heading" 화면을 그린다',
    async ({ url, heading }) => {
      renderAt(url)
      // 페이지는 lazy로 불러오므로 바로 나타나지 않는다. findBy는 나타날 때까지 기다린다
      expect(await screen.findByRole('heading', { level: 1, name: heading })).toBeInTheDocument()

      // 관리자 화면 공통 틀(AdminLayout, Task 029): 관리자 5개 화면에만 메뉴가 있고 현재 메뉴에 aria-current=page가 붙는다.
      // 같은 주소를 여는 별도 테스트로 나누지 않은 이유: React Router는 lazy 라우트를 처음 열 때 라우트 객체를 직접 고쳐서,
      // 같은 routes로 만든 두 번째 라우터에서 같은 주소를 열면 "No route matches" 404가 난다(관찰). 그래서 한 번 여는 이 테스트에서 함께 검사한다.
      // 회원·비로그인 차단은 RequireAdmin이 아직 모든 요청을 통과시키는 자리표시라(Task 057) 여기서 검사하지 않는다.
      const adminNav = screen.queryByRole('navigation', { name: '관리자 메뉴' })
      if (url.startsWith('/admin/')) {
        expect(adminNav).toBeInTheDocument()
        expect(within(adminNav!).getAllByRole('link')).toHaveLength(5)
        expect(within(adminNav!).getByRole('link', { current: 'page' })).toHaveAttribute(
          'href',
          url,
        )
      } else if (!url.startsWith('/__dev/')) {
        // 개발 확인 화면(__dev)은 부품 보기용으로 AdminNav를 직접 그리므로 제외한다
        expect(adminNav).not.toBeInTheDocument()
      }
    },
  )
})
