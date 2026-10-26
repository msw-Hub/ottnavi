import type { FormEvent, ReactNode } from 'react'
import { CheckIcon, SearchIcon } from 'lucide-react'
import { useSearchParams } from 'react-router'
import { DataAsOf } from '@/components/common/DataAsOf'
import { EmptyState } from '@/components/common/EmptyState'
import { ErrorState } from '@/components/common/ErrorState'
import { SourceAttribution } from '@/components/common/SourceAttribution'
import { Button } from '@/components/ui/button'
import { HomeTitleCard } from '@/features/home/components/HomeTitleCard'
import { RailSkeleton } from '@/features/home/components/RailSection'
import { useExploreTitles, useGenres } from '@/features/home/hooks/useHomeLists'
import { useOttServices } from '@/hooks/useOttServices'
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

// 주소의 ?genre= 값(TMDB 장르 ID)을 숫자로 바꾼다. 숫자가 아니거나 1 미만이면 null(고르지 않음)
function parseGenreId(raw: string | null): number | null {
  if (raw === null || !/^[1-9]\d{0,8}$/.test(raw)) return null
  return Number(raw)
}

/**
 * 탐색(`/explore`) 화면 본문이다. 제목 검색 입력, 작품 구분·장르 칩, "새로 볼 수 있게 된 작품만" 칩, 결과 바둑판이 있다.
 *
 * 결과를 받는 방법(useExploreTitles): 검색어가 있으면 작품 검색 API, 없고 장르를 골랐으면 장르별 작품 API(TMDB 장르),
 * 없고 "새로 볼 수 있게 된 작품만"이면 새 작품 API, 아무것도 안 골랐으면 오늘의 인기 순위(전체)를 보인다.
 * 서로 다른 API라 한 번에 한 조건만 적용된다: 검색을 하면 장르·새 작품 선택이 풀리고, 장르나 새 작품을 고르면 검색어가 지워진다
 * (눌러 놓은 칩이 결과에 반영되지 않는 어긋남을 막으려는 것이다). 작품 구분(영화/드라마) 칩만 받은 결과를 화면에서 거른다.
 * 인물 검색은 PRD 9절 고도화 후보라 입력창 아래에 준비 중이라고 밝힌다(출연자별 작품 API는 계약에 있고 아직 화면에 연결하지 않았다).
 * 고른 값(?q=, ?type=, ?genre=, ?view=new)을 주소에 두는 이유: 홈의 "더보기"가 바로 이 상태로 열고, 새로고침·공유에도 유지되게 하려는 것이다.
 */
export function ExploreScreen() {
  const [searchParams, setSearchParams] = useSearchParams()
  const query = (searchParams.get('q') ?? '').trim()
  const type = searchParams.get('type') ?? 'all'
  const genreId = parseGenreId(searchParams.get('genre'))
  const isNewOnly = searchParams.get('view') === 'new'

  const servicesQuery = useOttServices()
  const genresQuery = useGenres()
  const explore = useExploreTitles({ query, genreId, isNewOnly })

  // 주소 파라미터를 바꾼다. updates의 값이 null이면 지운다. 다른 파라미터(작품 구분, 목업 확인용 ?mockScenario 등)는 그대로 둔다
  function updateParams(updates: Record<string, string | null>) {
    setSearchParams(
      (current) => {
        const next = new URLSearchParams(current)
        for (const [key, value] of Object.entries(updates)) {
          if (value === null) next.delete(key)
          else next.set(key, value)
        }
        return next
      },
      { replace: true },
    )
  }

  // 이름 검색 제출: 입력값을 주소의 ?q=에 넣는다(빈 값이면 지운다). 검색은 장르·새 작품 조건과 함께 쓸 수 없어 둘을 푼다
  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const value = String(new FormData(event.currentTarget).get('q') ?? '').trim()
    // 빈 값이면 검색어만 지운다(고른 장르·새 작품 조건이 안내 없이 풀리지 않게)
    if (!value) {
      updateParams({ q: null })
      return
    }
    updateParams({ q: value, genre: null, view: null })
  }

  // 작품 구분은 받은 결과에서 화면이 거른다
  const results = explore.items.filter((title) => type === 'all' || title.mediaType === type)
  const services = servicesQuery.data ?? []

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
              onClick={() => updateParams({ type: option.value === 'all' ? null : option.value })}
            />
          ))}
        </ChipGroup>
        <ChipGroup label="장르">
          <ToggleChip
            label="전체 장르"
            isSelected={genreId === null}
            onClick={() => updateParams({ genre: null })}
          />
          {/* 장르 칩은 TMDB 장르 목록 API(이름 가나다순)에서 온다. 받는 중·실패일 때는 "전체 장르"만 남고 아래 줄에 안내가 붙는다 */}
          {(genresQuery.data ?? []).map((genre) => (
            <ToggleChip
              key={genre.genreId}
              label={genre.name}
              isSelected={genreId === genre.genreId}
              onClick={() => updateParams({ genre: String(genre.genreId), q: null, view: null })}
            />
          ))}
        </ChipGroup>
        {genresQuery.isError && (
          <p role="status" className="text-sm text-muted-foreground">
            장르 목록을 불러오지 못했어요. 잠시 후 새로고침해 주세요.
          </p>
        )}
        <ChipGroup label="보기">
          <ToggleChip
            label="새로 볼 수 있게 된 작품만"
            isSelected={isNewOnly}
            onClick={() =>
              updateParams(isNewOnly ? { view: null } : { view: 'new', q: null, genre: null })
            }
          />
        </ChipGroup>
      </div>

      {explore.isPending ? (
        <RailSkeleton heading="탐색 결과" />
      ) : explore.isError ? (
        <ErrorState
          title="작품을 불러오지 못했습니다"
          error={explore.error}
          onRetry={explore.refetch}
        />
      ) : (
        <>
          {/* 결과 개수는 거를 때마다 바뀌어 낭독기에 알린다(role="status", 급하지 않은 알림). 더 받을 페이지가 있으면 "이상"으로 쓴다 */}
          <p role="status" className="text-sm font-semibold tabular-nums">
            작품 {results.length}편{explore.hasNextPage ? ' 이상' : ''}
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
                  <HomeTitleCard title={title} services={services} />
                </li>
              ))}
            </ul>
          )}
          {explore.hasNextPage && (
            <div className="flex justify-center">
              <Button
                type="button"
                variant="outline"
                size="lg"
                className="h-11 px-6"
                disabled={explore.isFetchingNextPage}
                onClick={explore.fetchNextPage}
              >
                {explore.isFetchingNextPage ? '불러오는 중…' : '더보기'}
              </Button>
            </div>
          )}
        </>
      )}

      <div className="grid gap-1.5 border-t border-border pt-4">
        <SourceAttribution />
        <DataAsOf value={explore.dataAsOf} />
        <p className="text-sm text-muted-foreground">
          제공 여부는 확인된 범위이며, 실제 서비스 상황과 다를 수 있습니다.
        </p>
      </div>
    </section>
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
