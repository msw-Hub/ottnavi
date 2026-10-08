import { createBrowserRouter, type RouteObject } from 'react-router'
import { RequireAdmin } from '@/app/guards/RequireAdmin'
import { RequireAuth } from '@/app/guards/RequireAuth'
import { RootLayout } from '@/app/RootLayout'
import { RouteErrorBoundary } from '@/app/RouteErrorBoundary'
import { RouteLoadingFallback } from '@/app/RouteLoadingFallback'

/*
 * 화면 주소표(TASK018_031_PLAN 5절, 사용자 결정 P6). 여기 주소는 브라우저 주소창에 보이는 프론트 화면 주소이며
 * 백엔드 API 주소(/api/...)와 다르다. 주소를 바꾸거나 더하면 src/app/router.test.tsx의 표도 함께 고쳐야 테스트가 통과한다.
 *
 * 페이지를 lazy로 나누는 이유: 각 페이지가 별도 파일 조각(chunk)으로 빌드되어 그 주소에 처음 들어갈 때만 내려받는다.
 * 특히 관리자 화면 코드가 일반 사용자가 받는 첫 번들에 섞이지 않는다.
 * React Router 8의 객체형 lazy(`lazy: { Component: ... }`)를 쓰면 페이지 파일이 named export여도 그대로 가져올 수 있다
 * (frontend.md의 named export 원칙 유지, context7 reactrouter 문서 "Lazy load route properties with lazy" 확인).
 *
 * 가드는 "경로 없는 부모 라우트"로 둔다. 부모(RequireAuth·RequireAdmin)가 <Outlet />을 돌려줄 때만 자식 화면이 그려진다.
 */
export const routes: RouteObject[] = [
  {
    path: '/',
    Component: RootLayout,
    HydrateFallback: RouteLoadingFallback,
    // 레이아웃 자체가 실패했을 때를 받는 마지막 오류 경계(이 경우 헤더·푸터 없이 안내만 보인다)
    ErrorBoundary: RouteErrorBoundary,
    children: [
      {
        // 경로 없는 오류 경계: 화면 하나가 실패해도 헤더·푸터는 남기고 본문(<Outlet />) 자리에만 오류 안내를 그린다.
        // 위 RootLayout의 경계만 두면 화면 오류 하나로 헤더까지 사라져 다른 화면으로 이동하기 어렵다.
        ErrorBoundary: RouteErrorBoundary,
        children: [
          // 공개 화면
          {
            index: true, // SCR-01 검색 (`/`, 검색어는 ?q=)
            lazy: { Component: () => import('@/pages/SearchPage').then((m) => m.SearchPage) },
          },
          {
            path: 'titles/:mediaType/:tmdbId', // SCR-02 작품 상세
            lazy: {
              Component: () => import('@/pages/TitleDetailPage').then((m) => m.TitleDetailPage),
            },
          },
          {
            path: 'exclusives', // SCR-03 독점 목록 (2단계, 준비 중)
            lazy: {
              Component: () => import('@/pages/ExclusivesPage').then((m) => m.ExclusivesPage),
            },
          },
          {
            path: 'about', // SCR-04 About
            lazy: { Component: () => import('@/pages/AboutPage').then((m) => m.AboutPage) },
          },
          {
            path: 'accuracy', // SCR-05 정확도 안내 (3단계, 준비 중)
            lazy: { Component: () => import('@/pages/AccuracyPage').then((m) => m.AccuracyPage) },
          },
          {
            path: 'login', // SCR-06 로그인
            lazy: { Component: () => import('@/pages/LoginPage').then((m) => m.LoginPage) },
          },
          {
            // SCR-06 로그인 콜백(TECH 4절). 토큰을 받기 전 단계라 RequireAuth 밖(공개)에 둔다(AuthCallbackPage 주석 참고)
            path: 'auth/callback',
            lazy: {
              Component: () => import('@/pages/AuthCallbackPage').then((m) => m.AuthCallbackPage),
            },
          },

          // 로그인이 필요한 화면 (동작은 Task 057, 지금은 통과)
          {
            Component: RequireAuth,
            children: [
              {
                path: 'settings', // SCR-07 온보딩·내 설정
                lazy: {
                  Component: () => import('@/pages/SettingsPage').then((m) => m.SettingsPage),
                },
              },
              {
                path: 'wishlist', // SCR-08 찜 목록
                lazy: {
                  Component: () => import('@/pages/WishlistPage').then((m) => m.WishlistPage),
                },
              },
              {
                path: 'pricing', // SCR-09 요금 확인
                lazy: { Component: () => import('@/pages/PricingPage').then((m) => m.PricingPage) },
              },
              {
                path: 'plan', // SCR-10 플랜 결과
                lazy: { Component: () => import('@/pages/PlanPage').then((m) => m.PlanPage) },
              },
              {
                path: 'plan/history', // SCR-11 플랜 변경 내역 (2단계, 준비 중)
                lazy: {
                  Component: () => import('@/pages/PlanHistoryPage').then((m) => m.PlanHistoryPage),
                },
              },
              {
                path: 'settings/notifications', // SCR-12 알림 설정 (2단계, 준비 중)
                lazy: {
                  Component: () =>
                    import('@/pages/NotificationSettingsPage').then(
                      (m) => m.NotificationSettingsPage,
                    ),
                },
              },
              {
                path: 'settings/withdraw', // SCR-13 탈퇴 (2단계, 준비 중)
                lazy: {
                  Component: () => import('@/pages/WithdrawPage').then((m) => m.WithdrawPage),
                },
              },
            ],
          },

          // 관리자 화면 (동작은 Task 057, 지금은 통과)
          // 'admin'을 부모 경로로 두지 않고 자식마다 전체 경로를 적은 이유: 부모를 path: 'admin'으로 두면
          // `/admin`만 입력했을 때 가드만 그려지고 본문이 빈 화면이 된다. 지금 구조에서는 `/admin`이 아래 '*'로 가 404가 보인다.
          {
            Component: RequireAdmin,
            children: [
              {
                path: 'admin/products', // SCR-14 상품·가격
                lazy: {
                  Component: () =>
                    import('@/pages/AdminProductsPage').then((m) => m.AdminProductsPage),
                },
              },
              {
                path: 'admin/providers', // SCR-15 제공처 매핑
                lazy: {
                  Component: () =>
                    import('@/pages/AdminProvidersPage').then((m) => m.AdminProvidersPage),
                },
              },
              {
                path: 'admin/collect', // SCR-16 수집
                lazy: {
                  Component: () =>
                    import('@/pages/AdminCollectPage').then((m) => m.AdminCollectPage),
                },
              },
              {
                path: 'admin/monitoring', // SCR-17 모니터링 (3단계, 준비 중)
                lazy: {
                  Component: () =>
                    import('@/pages/AdminMonitoringPage').then((m) => m.AdminMonitoringPage),
                },
              },
              {
                path: 'admin/llm', // SCR-18 LLM 검토 (3단계, 준비 중)
                lazy: {
                  Component: () => import('@/pages/AdminLlmPage').then((m) => m.AdminLlmPage),
                },
              },
            ],
          },

          // 위 어디에도 맞지 않는 주소는 404 화면(레이아웃 안)
          {
            path: '*',
            lazy: { Component: () => import('@/pages/NotFoundPage').then((m) => m.NotFoundPage) },
          },
        ],
      },
    ],
  },
]

/**
 * 브라우저 주소(History API)를 쓰는 라우터를 만든다.
 *
 * 모듈을 불러올 때 바로 만들지 않고 함수로 둔 이유: createBrowserRouter는 만들어지는 즉시 현재 주소의 화면 준비를 시작한다.
 * main.tsx가 MSW 목업 워커를 켠 뒤에 호출하게 해서, 이후 화면 준비 단계에서 API를 부르더라도 목업을 거치게 한다.
 * 테스트는 이 함수 대신 같은 routes로 메모리 라우터를 만든다.
 */
export function createAppRouter() {
  return createBrowserRouter(routes)
}
