import type { ReactNode } from 'react'
import { Link, useParams } from 'react-router'
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
    return (
      <StateLayout>
        <ErrorState
          error={detail.error}
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

  return <TitleDetailContent detail={detail.data} services={services.data} />
}

// 성공 전 상태(주소 오류·로딩·오류)가 함께 쓰는 틀. 화면 제목(h1)을 항상 남긴다
function StateLayout({ children }: { children: ReactNode }) {
  return (
    <section className="flex flex-col gap-5">
      <h1 className="text-2xl font-semibold">작품 상세</h1>
      {children}
    </section>
  )
}

// 검색 화면으로 돌아가는 링크 버튼
function BackToSearchLink() {
  return (
    <Button asChild variant="outline" className="h-10 w-fit">
      <Link to="/">작품 검색으로 돌아가기</Link>
    </Button>
  )
}
