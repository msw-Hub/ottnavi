import type { FormEvent } from 'react'
import { createSearchParams, Link, Outlet, useNavigate, useSearchParams } from 'react-router'
import { Button } from '@/components/ui/button'

/**
 * 모든 화면이 공유하는 바깥 틀(헤더 · 본문 · 푸터)이다.
 *
 * 라우터의 최상위 라우트가 이 컴포넌트를 그리고, 주소에 맞는 화면은 <Outlet /> 자리에 들어간다.
 * 그래서 화면을 옮겨 다녀도 헤더·푸터는 다시 만들어지지 않고 본문만 바뀐다.
 * 시각 디자인은 Task 037에서 확정하므로 지금은 shadcn 기본 토큰(bg-background, text-muted-foreground 등)만 쓴다.
 */
export function RootLayout() {
  return (
    <div className="flex min-h-svh flex-col">
      {/* 키보드 사용자가 헤더 링크를 매번 거치지 않고 본문으로 바로 가도록 하는 링크. 포커스를 받을 때만 보인다 */}
      <a
        href="#main"
        className="sr-only focus:not-sr-only focus:absolute focus:top-2 focus:left-2 focus:bg-background focus:px-3 focus:py-2"
      >
        본문으로 건너뛰기
      </a>

      <header className="border-b">
        <div className="mx-auto flex max-w-5xl flex-wrap items-center gap-3 px-4 py-3">
          <Link to="/" className="font-semibold">
            OTT내비
          </Link>
          <HeaderSearchForm />
          <nav aria-label="계정" className="ml-auto">
            {/* TODO(Task 057): 로그인 상태면 로그인 링크 대신 내 메뉴(설정·찜·로그아웃)를 보여준다 */}
            <Link to="/login" className="text-sm underline-offset-4 hover:underline">
              로그인
            </Link>
          </nav>
        </div>
      </header>

      {/* tabIndex={-1}: 건너뛰기 링크로 이동했을 때 main이 포커스를 받을 수 있게 한다(Tab 순서에는 들어가지 않음) */}
      <main
        id="main"
        tabIndex={-1}
        className="mx-auto w-full max-w-5xl flex-1 px-4 py-8 outline-none"
      >
        <Outlet />
      </main>

      <footer className="border-t">
        <div className="mx-auto flex max-w-5xl flex-wrap items-center gap-x-4 gap-y-1 px-4 py-4 text-sm text-muted-foreground">
          <Link to="/about" className="underline-offset-4 hover:underline">
            서비스 소개(About)
          </Link>
          {/* 출처 표기 자리. 화면별 JustWatch 출처는 SourceAttribution(Task 026), TMDB 로고·고지문은 About 화면(Task 027)에서 채운다 */}
          <p>작품·제공처 정보 출처: TMDB, JustWatch</p>
        </div>
      </footer>
    </div>
  )
}

/**
 * 헤더의 작품 검색창이다. 제출하면 검색 화면 주소(`/?q=검색어`)로 이동만 하고, 결과 표시는 검색 화면(Task 027)이 맡는다.
 *
 * 검색어를 상태(state)가 아니라 URL에 두는 이유: 새로고침·뒤로 가기·주소 공유 때도 같은 검색 결과가 보여야 하기 때문이다(frontend.md 상태 관리 원칙).
 */
function HeaderSearchForm() {
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()
  const currentQuery = searchParams.get('q') ?? ''

  // 제출 시 입력값을 읽어 검색 화면으로 이동한다.
  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    // 기본 form 제출은 페이지 전체를 새로 불러오므로 막고, 라우터 이동으로 바꾼다
    event.preventDefault()
    const query = String(new FormData(event.currentTarget).get('q') ?? '').trim()
    // 빈 검색어는 ?q= 를 붙이지 않고 검색 홈으로 보낸다.
    // createSearchParams가 한글·특수문자를 주소에 맞게 인코딩한다(직접 문자열을 이어 붙이면 & 같은 문자에서 깨진다)
    navigate(query ? { pathname: '/', search: `?${createSearchParams({ q: query })}` } : '/')
  }

  return (
    <form role="search" onSubmit={handleSubmit} className="flex min-w-0 flex-1 items-center gap-2">
      {/*
        입력창을 제어 컴포넌트(useState) 대신 defaultValue로 두고 key에 현재 검색어를 넣었다.
        단순히 useState로 두면 뒤로 가기로 주소의 검색어가 바뀌어도 입력창 글자는 그대로 남는다.
        key가 바뀌면 React가 입력창을 새로 만들어 주소의 검색어로 다시 채우므로 둘이 어긋나지 않는다.
      */}
      <input
        key={currentQuery}
        type="search"
        name="q"
        defaultValue={currentQuery}
        aria-label="작품 제목 검색"
        placeholder="작품 제목 검색"
        className="h-8 min-w-0 flex-1 rounded-lg border border-input bg-background px-2.5 text-sm placeholder:text-muted-foreground focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/50 focus-visible:outline-none"
      />
      <Button type="submit" variant="outline">
        검색
      </Button>
    </form>
  )
}
