import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { render, screen, waitFor } from '@testing-library/react'
import { http as mswHttp, HttpResponse } from 'msw'
import { setupServer } from 'msw/node'
import { createMemoryRouter, RouterProvider, type RouteObject } from 'react-router'
import { afterAll, afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest'
import { Providers } from '@/app/providers'
import { routes } from '@/app/router'
import { HOME_TITLE, PageTitle } from '@/components/common/PageTitle'
import { TitleDetailPage } from '@/pages/TitleDetailPage'
import { handlers } from '@/mocks/handlers'
import { resetMockSession, setMockRole } from '@/mocks/mockSession'

/*
 * 탭 제목(<title>) 테스트. 화면 이름이 "{화면 이름} · OTT내비"로 문서 제목에 반영되는지,
 * 검색어·작품명이 제목에 들어가는지, 홈은 서비스 소개 제목을 쓰는지 확인한다.
 * 또 public/sitemap.xml·robots.txt가 라우터와 어긋나지 않는지도 함께 본다(검색 노출 설정이라 화면이 바뀌면 같이 고쳐야 한다).
 * API는 앱의 목업 핸들러(MSW)로 대체한다.
 */

const server = setupServer(...handlers)
beforeAll(() => server.listen({ onUnhandledFrame: 'error' }))
afterEach(() => {
  server.resetHandlers()
  resetMockSession()
})
afterAll(() => server.close())

beforeEach(() => {
  vi.stubEnv('VITE_USE_MOCK', 'true')
  setMockRole('ADMIN')
})
afterEach(() => vi.unstubAllEnvs())

// 실제 라우트로 메모리 라우터를 연다. 같은 routes로 같은 주소를 두 번 열면 lazy 라우트 객체가 고쳐져 실패하므로(router.test.tsx 주석) 주소마다 한 번만 쓴다
function renderAt(url: string) {
  const router = createMemoryRouter(routes, { initialEntries: [url] })
  render(
    <Providers>
      <RouterProvider router={router} />
    </Providers>,
  )
}

describe('PageTitle', () => {
  it('화면 이름 뒤에 서비스 이름을 붙여 문서 제목으로 올린다', async () => {
    render(<PageTitle name="랭킹" />)
    await waitFor(() => expect(document.title).toBe('랭킹 · OTT내비'))
  })

  it('이름이 없으면 홈 제목을 쓴다', async () => {
    render(<PageTitle />)
    await waitFor(() => expect(document.title).toBe(HOME_TITLE))
  })
})

describe('화면별 탭 제목', () => {
  it('공개 화면: 랭킹', async () => {
    renderAt('/ranking')
    await waitFor(() => expect(document.title).toBe('랭킹 · OTT내비'))
  })

  it('검색어가 있으면 제목에 넣는다', async () => {
    renderAt('/search?q=기생충')
    await waitFor(() => expect(document.title).toBe('"기생충" 검색 결과 · OTT내비'))
  })

  it('작품 상세는 성공하면 작품명을 제목으로 쓴다', async () => {
    renderAt('/titles/tv/93405')
    await waitFor(() => expect(document.title).toBe('오징어 게임 · OTT내비'))
  })

  it('관리자 화면은 [관리] 표시를 붙인다', async () => {
    renderAt('/admin/collect')
    await waitFor(() => expect(document.title).toBe('[관리] 수집 · OTT내비'))
  })

  it('없는 주소는 404 제목을 쓴다', async () => {
    renderAt('/no-such-page')
    await waitFor(() => expect(document.title).toBe('페이지를 찾을 수 없음 · OTT내비'))
  })

  it('작품 조회가 실패하면 "작품 상세" 제목으로 돌아간다', async () => {
    server.use(
      mswHttp.get('/api/public/titles/:mediaType/:tmdbId', () =>
        HttpResponse.json(
          { type: 'about:blank', title: 'Not Found', status: 404, errorCode: 'TITLE_NOT_FOUND' },
          { status: 404, headers: { 'Content-Type': 'application/problem+json' } },
        ),
      ),
    )
    // 같은 작품 상세 라우트를 이미 위에서 열었으므로(lazy 라우트 재사용 불가) 페이지를 직접 묶은 라우트로 연다
    const router = createMemoryRouter(
      [{ path: '/titles/:mediaType/:tmdbId', element: <TitleDetailPage /> }],
      { initialEntries: ['/titles/movie/1'] },
    )
    render(
      <Providers>
        <RouterProvider router={router} />
      </Providers>,
    )
    expect(await screen.findByRole('heading', { level: 1, name: '작품 상세' })).toBeInTheDocument()
    await waitFor(() => expect(document.title).toBe('작품 상세 · OTT내비'))
  })
})

// 라우트 설정에서 화면이 그려지는 끝 라우트의 전체 경로를 모은다(경로 없는 부모는 경로를 더하지 않는다)
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

describe('sitemap.xml · robots.txt', () => {
  const sitemap = readFileSync(resolve(import.meta.dirname, '../../public/sitemap.xml'), 'utf8')
  const robots = readFileSync(resolve(import.meta.dirname, '../../public/robots.txt'), 'utf8')
  const sitemapPaths = [...sitemap.matchAll(/<loc>https:\/\/ottnavi\.shop(\/[^<]*)<\/loc>/g)].map(
    (m) => m[1],
  )
  const disallowed = [...robots.matchAll(/^Disallow:\s*(\S+)/gm)].map((m) => m[1])

  it('sitemap의 주소는 모두 라우터에 있는 고정 주소다', () => {
    const patterns = collectLeafPatterns(routes)
    expect(sitemapPaths.length).toBeGreaterThan(0)
    for (const path of sitemapPaths) {
      expect(patterns, path).toContain(path)
    }
  })

  it('sitemap의 주소는 robots.txt가 막은 경로 아래에 있지 않다', () => {
    for (const path of sitemapPaths) {
      expect(
        disallowed.some((prefix) => path.startsWith(prefix)),
        path,
      ).toBe(false)
    }
  })

  it('robots.txt가 Sitemap 위치를 알린다', () => {
    expect(robots).toContain('Sitemap: https://ottnavi.shop/sitemap.xml')
  })
})
