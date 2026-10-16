import type { FormEvent } from 'react'
import { SearchIcon } from 'lucide-react'
import { createSearchParams, useNavigate, useSearchParams } from 'react-router'
import { Button } from '@/components/ui/button'
import { cn } from '@/lib/utils'

// 검색 화면 주소. 홈(`/`)이 따로 생기면서 검색 결과는 `/search?q=검색어`로 옮겼다(홈 1차안).
// export하지 않는 이유: 컴포넌트 파일에서 상수를 내보내면 빠른 새로고침(react-refresh) 린트 규칙에 걸린다
const SEARCH_PATH = '/search'

interface TitleSearchFormProps {
  variant?: 'compact' | 'hero' // compact: 머리글 안의 검색창(높이 40px, 지금 모든 화면이 쓰는 모양) / hero: 본문용 큰 검색창(높이 48px). 홈 1차안 피드백으로 검색창을 머리글로 통일해 hero는 지금 쓰는 곳이 없다(다시 쓸 때를 위해 남김)
  className?: string // 놓이는 자리에 맞춘 폭·여백
}

/**
 * 작품 제목 검색창이다. 제출하면 검색 화면 주소(`/search?q=검색어`)로 이동만 하고, 결과 표시는 검색 화면(features/search)이 맡는다.
 *
 * 머리글(RootLayout)에만 있던 검색창을 여기로 옮긴 이유: 홈 1차안에서 홈 본문의 큰 검색창, 검색 화면의 검색창, 머리글의 작은 검색창이
 * 같은 동작을 해야 한다. 한 컴포넌트를 모양(variant)만 바꿔 쓰면 이동 규칙이 한 곳에만 있어 어긋나지 않는다.
 *
 * 검색어를 상태(state)가 아니라 URL에 두는 이유: 새로고침·뒤로 가기·주소 공유 때도 같은 검색 결과가 보여야 하기 때문이다(frontend.md 상태 관리 원칙).
 */
export function TitleSearchForm({ variant = 'compact', className }: TitleSearchFormProps) {
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()
  const currentQuery = searchParams.get('q') ?? ''
  const isHero = variant === 'hero'

  // 제출 시 입력값을 읽어 검색 화면으로 이동한다.
  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    // 1. 기본 form 제출은 페이지 전체를 새로 불러오므로 막고, 라우터 이동으로 바꾼다
    event.preventDefault()
    // 2. 입력값을 읽고 앞뒤 공백을 지운다
    const query = String(new FormData(event.currentTarget).get('q') ?? '').trim()
    // 3. 빈 검색어는 ?q= 를 붙이지 않고 검색 화면 첫 상태로 보낸다.
    //    createSearchParams가 한글·특수문자를 주소에 맞게 인코딩한다(직접 문자열을 이어 붙이면 & 같은 문자에서 깨진다)
    navigate(
      query
        ? { pathname: SEARCH_PATH, search: `?${createSearchParams({ q: query })}` }
        : SEARCH_PATH,
    )
  }

  return (
    <form
      role="search"
      onSubmit={handleSubmit}
      className={cn('flex min-w-0 items-center gap-2', className)}
    >
      {/*
        relative: 입력창 안 왼쪽에 돋보기 아이콘을 얹는 기준점이다(아이콘은 장식이라 낭독기에서 숨긴다).
        입력창을 제어 컴포넌트(useState) 대신 defaultValue로 두고 key에 현재 검색어를 넣었다.
        단순히 useState로 두면 뒤로 가기로 주소의 검색어가 바뀌어도 입력창 글자는 그대로 남는다.
        key가 바뀌면 React가 입력창을 새로 만들어 주소의 검색어로 다시 채우므로 둘이 어긋나지 않는다.
      */}
      <div className="relative min-w-0 flex-1">
        <SearchIcon
          aria-hidden="true"
          className={cn(
            'pointer-events-none absolute top-1/2 left-3 -translate-y-1/2 text-muted-foreground',
            isHero ? 'size-5' : 'size-4',
          )}
        />
        <input
          key={currentQuery}
          type="search"
          name="q"
          defaultValue={currentQuery}
          aria-label="작품 제목 검색"
          placeholder={isHero ? '작품 제목을 검색해 보세요' : '작품 제목 검색'} // hero 문구는 375px 폭(입력창 약 250px)에서 잘리지 않는 길이로 줄였다
          // hero: 높이 48px(h-12)·글자 16px. 홈의 첫 행동이라 눈에 띄고 손가락으로 누르기 쉽게 크게 둔다.
          // compact: 높이 40px(h-10)·글자 14px. 머리글 높이(--header-height)를 키우지 않는 크기다
          className={cn(
            'w-full min-w-0 rounded-lg border border-input bg-background placeholder:text-muted-foreground focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/50 focus-visible:outline-none',
            isHero ? 'h-12 pr-3 pl-10 text-base' : 'h-10 pr-2.5 pl-9 text-sm',
          )}
        />
      </div>
      {/* border-input bg-transparent: 어두운 테마에서 outline 버튼 테두리가 흐려 입력창과 같은 테두리색을 쓴다.
          hero는 이 화면의 주된 행동이라 채운 강조 버튼(primary)으로 둔다 */}
      <Button
        type="submit"
        variant={isHero ? 'default' : 'outline'}
        className={cn(isHero ? 'h-12 px-5 text-base' : 'h-10 border-input bg-transparent')}
      >
        검색
      </Button>
    </form>
  )
}
