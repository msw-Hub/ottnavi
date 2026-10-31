import type { ReactNode } from 'react'
import { Link, useParams } from 'react-router'
import { PageTitle } from '@/components/common/PageTitle'
import { PosterImage } from '@/components/common/PosterImage'
import { EmptyState } from '@/components/common/EmptyState'
import { ErrorState } from '@/components/common/ErrorState'
import { LoadingState } from '@/components/common/LoadingState'
import { Button } from '@/components/ui/button'
import { TitleDetailContent } from '@/features/title-detail/components/TitleDetailContent'
import {
  useTitleDetail,
  type TitleDetailParams,
} from '@/features/title-detail/hooks/useTitleDetail'
import { useOttServices } from '@/hooks/useOttServices'
import { usePopularityEntryState } from '@/features/title-detail/hooks/usePopularityEntryState'
import type { PopularityNavState } from '@/lib/popularityNavState'
import { getTmdbImageUrl } from '@/lib/tmdbImage'

/**
 * 주소의 문자열(:mediaType, :tmdbId)을 검증해 작품 조회용 값으로 바꾼다. 올바르지 않으면 null이다.
 *
 * 주소는 사용자가 직접 고칠 수 있는 입력이라 믿지 않고 확인한다. mediaType은 movie·tv 두 값만, tmdbId는 양의 정수만 받는다.
 * 그렇지 않으면 서버에 보내지 않고 화면에서 바로 "작품을 찾을 수 없다"고 알린다.
 */
function parseTitleParams(
  mediaType: string | undefined,
  tmdbId: string | undefined,
): TitleDetailParams | null {
  if (mediaType !== 'movie' && mediaType !== 'tv') return null
  if (!tmdbId || !/^\d+$/.test(tmdbId)) return null
  const id = Number(tmdbId)
  return Number.isSafeInteger(id) && id > 0 ? { mediaType, tmdbId: id } : null
}

/**
 * SCR-02 작품 상세 화면의 본문이다. pages/TitleDetailPage는 이 컴포넌트를 연결만 한다(pages는 얇게, frontend.md).
 *
 * 화면이 가질 수 있는 상태: 주소 오류 / 로딩 / 오류 / 성공. 성공이 아닐 때도 h1 "작품 상세"를 먼저 보여
 * 화면 제목이 사라지지 않게 한다(성공하면 h1은 작품 제목이 된다).
 */
export function TitleDetailScreen() {
  const { mediaType, tmdbId } = useParams()
  const params = parseTitleParams(mediaType, tmdbId)
  const detail = useTitleDetail(params)
  // 인기 순위 카드에서 들어왔는지(라우터 state). 아니거나 새로고침한 뒤면 null
  const popularityState = usePopularityEntryState()
  // 서비스 이름·"데이터 부족" 여부는 서비스 목록 API에 있어 상세와 함께 받는다. 둘 다 준비되었을 때만 본문을 그린다.
  // 주소가 잘못되었으면 서비스 목록도 필요 없어 요청을 막는다
  const services = useOttServices(params !== null)

  if (params === null) {
    return (
      <StateLayout>
        <EmptyState
          title="작품을 찾을 수 없습니다"
          description="주소가 올바르지 않습니다. 작품 검색에서 다시 찾아 주세요."
          action={<BackToSearchLink />}
        />
      </StateLayout>
    )
  }

  if (detail.isPending || services.isPending) {
    return (
      <StateLayout>
        <LoadingState message="작품 정보를 불러오고 있습니다" />
      </StateLayout>
    )
  }

  if (detail.isError) {
    // 없는 작품(404)은 다시 시도해도 같아서 "다시 시도" 대신 검색으로 돌아가는 링크를 둔다
    const isNotFound = detail.error.status === 404
    // 인기 순위 카드에서 와서 404면 "곧 반영될 작품"이라 일반 404와 다른 안내를 보인다(state가 없으면 이 분기는 건너뛴다)
    if (isNotFound && popularityState) {
      return (
        <StateLayout>
          <PopularityPendingNotice state={popularityState} />
          <BackToSearchLink />
        </StateLayout>
      )
    }
    return (
      <StateLayout>
        <ErrorState
          error={detail.error}
          // 서버는 DB에 저장된 작품만 돌려준다(단건 수집은 이월, Task 050·051). 그래서 404는 "아직 수집되지 않은 작품"일 수 있다고 덧붙인다
          message={
            isNotFound
              ? '작품을 찾을 수 없습니다. 아직 수집되지 않은 작품일 수 있습니다.'
              : undefined
          }
          onRetry={isNotFound ? undefined : () => detail.refetch()}
        />
        {isNotFound && <BackToSearchLink />}
      </StateLayout>
    )
  }

  if (services.isError) {
    return (
      <StateLayout>
        <ErrorState error={services.error} onRetry={() => services.refetch()} />
      </StateLayout>
    )
  }

  // 성공했을 때만 작품명을 탭 제목에 쓴다. 이 분기와 StateLayout 분기는 동시에 그려지지 않아 <title>은 항상 하나다
  return (
    <>
      <PageTitle name={detail.data.title} />
      <TitleDetailContent detail={detail.data} services={services.data} />
    </>
  )
}

// 성공 전 상태(주소 오류·로딩·오류)가 함께 쓰는 틀. 화면 제목(h1)을 항상 남긴다
function StateLayout({ children }: { children: ReactNode }) {
  return (
    <section className="flex flex-col gap-5">
      <PageTitle name="작품 상세" />
      <h1 className="text-2xl font-semibold">작품 상세</h1>
      {children}
    </section>
  )
}

/**
 * 인기 순위에는 있지만 우리 DB에 상세가 아직 없는 작품의 안내다. 서버가 인기 수집 뒤 상위 작품의 상세를 채우므로 다음 정보 수집 때 반영된다
 * (한 번에 채우는 작품 수에 상한이 있어 하루를 넘길 수 있으므로 문구에서 기한을 약속하지 않는다).
 * 오류가 아니라 "기다리면 되는 상태"라 경고색 ErrorState 대신 EmptyState 모양을 쓰고, 낭독기가 바로 읽도록 role="status"로 감싼다.
 * 카드에서 받은 제목·포스터를 함께 보여 어떤 작품을 기다리는지 알게 한다(새 API 호출 없음).
 */
function PopularityPendingNotice({ state }: { state: PopularityNavState }) {
  return (
    <div role="status" className="grid gap-3">
      <div className="flex items-center gap-3">
        <PosterImage
          title={state.title}
          posterUrl={getTmdbImageUrl(state.posterPath, 'w185')}
          className="w-20 shrink-0"
        />
        <p className="min-w-0 text-lg font-semibold break-keep">{state.title}</p>
      </div>
      <EmptyState
        title="상세 정보를 모으는 중이에요"
        description="새로 인기를 얻은 작품은 다음 정보 수집 때 함께 반영돼요. 조금 뒤에 다시 확인해 주세요."
      />
    </div>
  )
}

// 검색 화면으로 돌아가는 링크 버튼. 검색 화면 주소가 `/`에서 `/search`로 바뀌어(홈 1차안) 목적지를 맞췄다(이전: to="/")
function BackToSearchLink() {
  return (
    <Button asChild variant="outline" className="h-11 w-fit">
      <Link to="/search">작품 검색으로 돌아가기</Link>
    </Button>
  )
}
