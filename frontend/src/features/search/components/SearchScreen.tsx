import { InfoIcon } from 'lucide-react'
import { useSearchParams } from 'react-router'
import type { OttService } from '@/api/generated/model'
import { DataAsOf } from '@/components/common/DataAsOf'
import { DataInsufficientTag } from '@/components/common/DataInsufficientTag'
import { EmptyState } from '@/components/common/EmptyState'
import { ErrorState } from '@/components/common/ErrorState'
import { LoadingState } from '@/components/common/LoadingState'
import { SourceAttribution } from '@/components/common/SourceAttribution'
import { Button } from '@/components/ui/button'
import { SearchResultCard } from '@/features/search/components/SearchResultCard'
import { useSearchTitles, type SearchTitlesData } from '@/features/search/hooks/useSearchTitles'
import { useOttServices } from '@/hooks/useOttServices'

/**
 * SCR-01 검색 화면의 본문이다. pages/SearchPage는 이 컴포넌트를 연결만 한다(pages는 얇게, frontend.md).
 *
 * 검색어는 컴포넌트 상태가 아니라 주소의 ?q=에서 읽는다. 헤더 검색창(app/RootLayout)이 제출하면 주소만 바뀌고,
 * 이 화면은 주소가 바뀐 것을 보고 결과를 다시 보인다. 그래서 새로고침·뒤로 가기·주소 공유에도 같은 결과가 나온다.
 * h1은 검색어가 있든 없든 항상 "작품 검색"으로 둔다(화면 제목이 흔들리지 않게).
 */
export function SearchScreen() {
  const [searchParams] = useSearchParams()
  const query = (searchParams.get('q') ?? '').trim()

  return (
    <section className="flex flex-col gap-5">
      <h1 className="text-2xl font-semibold">작품 검색</h1>
      {query ? <SearchResults query={query} /> : <SearchIntro />}
    </section>
  )
}

// 검색어가 없을 때 보이는 안내. 이 서비스가 보여 주는 정보의 범위("확인된 범위")도 처음부터 알린다
function SearchIntro() {
  return (
    <div className="grid gap-2 text-muted-foreground">
      <p>찜할 작품을 제목으로 검색해 보세요. 화면 위쪽 검색창에 제목을 입력하면 됩니다.</p>
      <p className="text-sm">
        제공 여부는 TMDB(JustWatch) 데이터로 확인된 범위이며, 실제 서비스 상황과 다를 수 있습니다.
      </p>
    </div>
  )
}

interface SearchResultsProps {
  query: string // 검색어(공백을 뺀 값, 비어 있지 않다)
}

/**
 * 검색어가 있을 때 결과를 불러와 로딩·오류·빈 상태·목록 중 하나를 그린다.
 *
 * 검색 화면 전체가 아니라 이 컴포넌트에서 훅을 부르는 이유: 검색어가 없을 때는 검색과 서비스 목록 요청이
 * 나가지 않게 하기 위함이다(훅은 조건문 안에서 부를 수 없어, 훅을 부르는 부분을 조건부로 그리는 컴포넌트로 나눴다).
 */
function SearchResults({ query }: SearchResultsProps) {
  const search = useSearchTitles(query)
  const services = useOttServices()

  if (search.isPending || services.isPending) {
    return <LoadingState message="작품을 검색하고 있습니다" />
  }
  // 첫 불러오기가 실패했을 때만 화면 전체를 오류로 바꾼다. "더보기" 실패는 이미 불러온 목록을 지우지 않고 목록 아래에서 알린다
  if (search.isError && !search.data) {
    return <ErrorState error={search.error} onRetry={() => search.refetch()} />
  }
  if (services.isError) {
    return <ErrorState error={services.error} onRetry={() => services.refetch()} />
  }

  return (
    <SearchResultList
      query={query}
      result={search.data!} // 위에서 pending·첫 오류를 걸러 내서 여기서는 항상 데이터가 있다
      services={services.data}
      hasNextPage={search.hasNextPage}
      isFetchingNextPage={search.isFetchingNextPage}
      loadMoreError={search.isFetchNextPageError ? search.error : null}
      onLoadMore={() => search.fetchNextPage()}
    />
  )
}

interface SearchResultListProps {
  query: string // 검색어(결과 제목에 보인다)
  result: SearchTitlesData // 지금까지 불러온 검색 결과(작품 목록·전체 건수·수집 실패 여부·기준일)
  services: OttService[] // 서비스 목록 API의 7개 서비스
  hasNextPage: boolean // 더 불러올 페이지가 있는지
  isFetchingNextPage: boolean // "더보기"로 다음 페이지를 불러오는 중인지
  loadMoreError: unknown // "더보기"가 실패한 원인. 실패하지 않았으면 null
  onLoadMore: () => void // "더보기"(또는 실패 후 다시 시도)를 눌렀을 때 다음 페이지를 불러온다
}

// 결과 목록과 출처·기준일. 결과가 없으면 빈 상태 안내를 보인다
function SearchResultList({
  query,
  result,
  services,
  hasNextPage,
  isFetchingNextPage,
  loadMoreError,
  onLoadMore,
}: SearchResultListProps) {
  // 데이터 부족(INSUFFICIENT) 서비스의 이름. 서비스 목록 API가 알려 주는 값을 그대로 쓴다
  const insufficientNames = services
    .filter((service) => service.dataQuality === 'INSUFFICIENT')
    .map((service) => service.name)

  return (
    <div className="flex flex-col gap-4">
      {/* 외부 수집 실패: 오류로 막지 않고 저장된 결과만 보여 주면서 한계를 알린다(PRD SCR-01). 급한 알림은 아니라 role="status" */}
      {result.isExternalCollectFailed && (
        <p
          role="status"
          className="flex items-start gap-2 rounded-lg border border-border bg-muted px-4 py-3 text-sm"
        >
          <InfoIcon aria-hidden="true" className="mt-0.5 size-4 shrink-0" />
          <span>
            외부 데이터 수집에 실패해 저장된 결과만 보여 드립니다. 최근에 추가된 작품이나 바뀐 제공
            정보는 반영되지 않았을 수 있습니다.
          </span>
        </p>
      )}

      {result.items.length === 0 ? (
        <EmptyState
          title="검색 결과가 없습니다"
          description={`"${query}"에 해당하는 작품을 찾지 못했습니다. 제목을 다르게 입력하거나 원제로 검색해 보세요.`}
        />
      ) : (
        <>
          <h2 className="text-lg font-semibold">
            &ldquo;{query}&rdquo; 검색 결과 {result.totalElements}건
          </h2>
          {/* 쿠팡플레이처럼 데이터가 부족한 서비스의 안내는 카드마다 반복하지 않고 결과 위에 한 번만 둔다 */}
          {insufficientNames.length > 0 && (
            <p className="flex flex-wrap items-center gap-x-1.5 gap-y-1 text-sm text-muted-foreground">
              <DataInsufficientTag />
              <span>
                {insufficientNames.join(', ')}는 데이터가 부족해 제공 여부를 확인하지 못할 수
                있습니다.
              </span>
            </p>
          )}
          <ul
            className="grid grid-cols-2 gap-x-3 gap-y-5 sm:grid-cols-3 sm:gap-x-4 lg:grid-cols-5"
            aria-label="검색 결과"
          >
            {result.items.map((item) => (
              // 같은 제목의 영화·드라마가 있어 제목이 아니라 mediaType + tmdbId가 작품의 고유 식별자다
              <li key={`${item.mediaType}-${item.tmdbId}`}>
                <SearchResultCard item={item} services={services} />
              </li>
            ))}
          </ul>
          {/* 더보기: 다음 페이지를 아래에 이어 붙인다. 마지막 페이지까지 불러오면 버튼을 숨긴다. 실패하면 오류 안내와 다시 시도로 바꾼다 */}
          {loadMoreError ? (
            <ErrorState error={loadMoreError} onRetry={onLoadMore} />
          ) : (
            hasNextPage && (
              <Button
                type="button"
                variant="outline"
                size="lg"
                className="h-10 w-full"
                disabled={isFetchingNextPage}
                aria-label={isFetchingNextPage ? '검색 결과 불러오는 중' : '검색 결과 더보기'}
                onClick={onLoadMore}
              >
                {isFetchingNextPage ? '불러오는 중' : '더보기'}
              </Button>
            )
          )}
        </>
      )}

      {/* 제공처 정보가 보이는 화면이라 출처와 기준일을 둔다(빈 결과에는 제공처가 없어 생략) */}
      {result.items.length > 0 && (
        <div className="grid gap-1.5 border-t border-border pt-4">
          <SourceAttribution />
          <DataAsOf value={result.dataAsOf} />
          <p className="text-sm text-muted-foreground">
            제공 여부는 확인된 범위이며, 실제 서비스 상황과 다를 수 있습니다.
          </p>
        </div>
      )}
    </div>
  )
}
