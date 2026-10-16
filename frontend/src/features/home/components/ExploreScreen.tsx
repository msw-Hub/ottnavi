import type { FormEvent, ReactNode } from 'react'
import { CheckIcon, SearchIcon } from 'lucide-react'
import { useSearchParams } from 'react-router'
import { DataAsOf } from '@/components/common/DataAsOf'
import { EmptyState } from '@/components/common/EmptyState'
import { SourceAttribution } from '@/components/common/SourceAttribution'
import { Button } from '@/components/ui/button'
import { HomeTitleCard } from '@/features/home/components/HomeTitleCard'
import type { HomeGenre, HomeTitle } from '@/features/home/mockTypes'
import {
  HOME_DATA_AS_OF,
  HOME_GENRE_LABELS,
  HOME_NEW_ARRIVALS,
  HOME_SERVICES,
  HOME_TITLES,
} from '@/features/home/sampleData'
import { cn } from '@/lib/utils'

// 작품 구분 칩. 'all'은 구분하지 않음
const TYPE_OPTIONS = [
  { value: 'all', label: '전체' },
  { value: 'movie', label: '영화' },
  { value: 'tv', label: '드라마' },
] as const

// 칩 하나의 모양. h-11(44px) 터치 크기, 고른 칩은 채움 + 체크 아이콘(색만으로 구분하지 않음)
function chipClassName(isSelected: boolean) {
  return cn(
    'inline-flex h-11 shrink-0 items-center gap-1 rounded-full border px-4 text-sm font-semibold whitespace-nowrap focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ring',
    isSelected
      ? 'border-primary bg-primary text-primary-foreground'
      : 'border-input text-muted-foreground hover:text-foreground',
  )
}

/**
 * 탐색(`/explore`) 화면 본문이다(홈 1차안, 얇은 자리). 이름 검색 입력, 작품 구분·장르 칩, "새로 볼 수 있게 된 작품만" 칩, 결과 바둑판이 있다.
 *
 * 1차안 한계: 정적 샘플 작품 18개 안에서만 거른다. 인물 검색은 PRD 9절 고도화 후보(우리 DB의 출연진 상위 10명에서 찾기, 범위 미정)라
 * 지금은 작품 제목만 찾는다는 것을 입력창 아래에 밝힌다.
 * 고른 값(?q=, ?type=, ?genre=, ?view=new)을 주소에 두는 이유: 홈의 "더보기"가 바로 이 상태로 열고, 새로고침·공유에도 유지되게 하려는 것이다.
 */
export function ExploreScreen() {
  const [searchParams, setSearchParams] = useSearchParams()
  const query = (searchParams.get('q') ?? '').trim()
  const type = searchParams.get('type') ?? 'all'
  const genre = searchParams.get('genre') as HomeGenre | null
  const isNewOnly = searchParams.get('view') === 'new'

  // 주소 파라미터 하나를 바꾸거나(값) 지운다(null). 다른 파라미터는 그대로 둔다
  function updateParam(key: string, value: string | null) {
    setSearchParams(
      (current) => {
        const next = new URLSearchParams(current)
        if (value === null) next.delete(key)
        else next.set(key, value)
        return next
      },
      { replace: true },
    )
  }

  // 이름 검색 제출: 입력값을 주소의 ?q=에 넣는다(빈 값이면 지운다)
  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const value = String(new FormData(event.currentTarget).get('q') ?? '').trim()
    updateParam('q', value || null)
  }

  const results = filterTitles(HOME_TITLES, { query, type, genre, isNewOnly })

  return (
    <section aria-labelledby="explore-title" className="flex flex-col gap-6">
      <div className="grid gap-1">
        <h1 id="explore-title" className="text-2xl font-bold">
          탐색
        </h1>
        <p className="text-muted-foreground">
          장르와 작품 구분으로 7곳에서 볼 수 있는 작품을 골라 보세요.
        </p>
      </div>

      <div className="grid gap-1.5">
        <form role="search" onSubmit={handleSubmit} className="flex max-w-2xl items-center gap-2">
          <div className="relative min-w-0 flex-1">
            <SearchIcon
              aria-hidden="true"
              className="pointer-events-none absolute top-1/2 left-3 size-5 -translate-y-1/2 text-muted-foreground"
            />
            {/* key={query}: 주소의 검색어가 바뀌면(뒤로 가기 등) 입력창을 새로 만들어 같은 글자로 맞춘다(TitleSearchForm과 같은 방식) */}
            <input
              key={query}
              type="search"
              name="q"
              defaultValue={query}
              aria-label="작품 제목"
              aria-describedby="explore-search-hint"
              placeholder="작품 제목"
              className="h-12 w-full min-w-0 rounded-lg border border-input bg-background pr-3 pl-10 text-base placeholder:text-muted-foreground focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/50 focus-visible:outline-none"
            />
          </div>
          <Button type="submit" className="h-12 px-5 text-base">
            찾기
          </Button>
        </form>
        <p id="explore-search-hint" className="text-sm text-muted-foreground">
          인물 검색은 준비 중이에요. 지금은 작품 제목으로만 찾아요.
        </p>
      </div>

      <div className="grid gap-3">
        <ChipGroup label="작품 구분">
          {TYPE_OPTIONS.map((option) => (
            <ToggleChip
              key={option.value}
              label={option.label}
              isSelected={type === option.value}
              onClick={() => updateParam('type', option.value === 'all' ? null : option.value)}
            />
          ))}
        </ChipGroup>
        <ChipGroup label="장르">
          <ToggleChip
            label="전체 장르"
            isSelected={genre === null}
            onClick={() => updateParam('genre', null)}
          />
          {(Object.keys(HOME_GENRE_LABELS) as HomeGenre[]).map((value) => (
            <ToggleChip
              key={value}
              label={HOME_GENRE_LABELS[value]}
              isSelected={genre === value}
              onClick={() => updateParam('genre', value)}
            />
          ))}
        </ChipGroup>
        <ChipGroup label="보기">
          <ToggleChip
            label="새로 볼 수 있게 된 작품만"
            isSelected={isNewOnly}
            onClick={() => updateParam('view', isNewOnly ? null : 'new')}
          />
        </ChipGroup>
      </div>

      {/* 결과 개수는 거를 때마다 바뀌어 낭독기에 알린다(role="status", 급하지 않은 알림) */}
      <p role="status" className="text-sm font-semibold tabular-nums">
        작품 {results.length}편
      </p>
      {results.length === 0 ? (
        <EmptyState
          title="조건에 맞는 작품이 없습니다"
          description="장르나 작품 구분을 바꾸거나 검색어를 지워 보세요. 아직 수집되지 않은 작품은 보이지 않을 수 있습니다."
        />
      ) : (
        <ul
          aria-label="탐색 결과"
          className="grid grid-cols-2 gap-x-3 gap-y-6 sm:grid-cols-3 sm:gap-x-4 lg:grid-cols-5"
        >
          {results.map((title) => (
            <li key={`${title.mediaType}-${title.tmdbId}`}>
              <HomeTitleCard title={title} services={HOME_SERVICES} />
            </li>
          ))}
        </ul>
      )}

      <div className="grid gap-1.5 border-t border-border pt-4">
        <SourceAttribution />
        <DataAsOf value={HOME_DATA_AS_OF} />
        <p className="text-sm text-muted-foreground">
          제공 여부는 확인된 범위이며, 실제 서비스 상황과 다를 수 있습니다.
        </p>
      </div>
    </section>
  )
}

interface TitleFilter {
  query: string // 제목에 들어 있어야 하는 글자(빈 값이면 거르지 않음)
  type: string // 'all' | 'movie' | 'tv'
  genre: HomeGenre | null // 장르(null이면 거르지 않음)
  isNewOnly: boolean // 새로 볼 수 있게 된 작품만
}

// 샘플 작품을 조건으로 거른다(1차안 전용. 실제로는 서버가 걸러 내려줄 값이다)
function filterTitles(titles: HomeTitle[], filter: TitleFilter): HomeTitle[] {
  const newIds = new Set(HOME_NEW_ARRIVALS.map((entry) => entry.title.tmdbId))
  return titles.filter(
    (title) =>
      // 1. 제목 글자
      (filter.query === '' || title.title.includes(filter.query)) &&
      // 2. 작품 구분
      (filter.type === 'all' || title.mediaType === filter.type) &&
      // 3. 장르
      (filter.genre === null || title.genres.includes(filter.genre)) &&
      // 4. 새로 볼 수 있게 된 작품
      (!filter.isNewOnly || newIds.has(title.tmdbId)),
  )
}

// 칩 묶음. 눈에 보이는 묶음 이름(왼쪽 작은 글자)과 낭독기용 이름(aria-label)을 같게 둔다.
// 칩이 많아 375px에서 넘치면 칩 줄만 옆으로 밀어 본다(줄바꿈하면 장르 칩이 3줄이 되어 결과가 화면 아래로 밀린다)
function ChipGroup({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="grid gap-1.5 sm:grid-cols-[5rem_minmax(0,1fr)] sm:items-center sm:gap-3">
      <span aria-hidden="true" className="text-sm font-semibold text-muted-foreground">
        {label}
      </span>
      {/* py-2: 칩의 포커스 링(2px + 간격 2px)과 아래 테두리가 가로 스크롤 영역에 잘리지 않게 하는 여백(py-1이면 서비스 탭에서 아래쪽이 잘려 보였다).
          sm:overflow-visible: 넓은 화면은 칩이 줄바꿈되어 옆으로 밀 일이 없으니 자르지 않는다(첫 칩 왼쪽 포커스 링이 잘리지 않게).
          min-h-15: 모바일에서 가로 스크롤 영역 높이가 내용보다 작게 잡혀 칩 아래가 잘리는 것을 막는다(ServiceTabs 주석 참고) */}
      <div className="-mx-4 min-h-15 overflow-x-auto px-4 py-2 [scrollbar-width:none] sm:mx-0 sm:overflow-visible sm:px-0">
        <div role="group" aria-label={label} className="flex w-max gap-2 sm:w-auto sm:flex-wrap">
          {children}
        </div>
      </div>
    </div>
  )
}

// 눌림 상태 칩(aria-pressed). 같은 묶음에서 하나만 고르는 칩도 이 모양을 쓴다
function ToggleChip({
  label,
  isSelected,
  onClick,
}: {
  label: string
  isSelected: boolean
  onClick: () => void
}) {
  return (
    <button
      type="button"
      aria-pressed={isSelected}
      onClick={onClick}
      className={chipClassName(isSelected)}
    >
      {isSelected && <CheckIcon aria-hidden="true" strokeWidth={3} className="size-4" />}
      {label}
    </button>
  )
}
