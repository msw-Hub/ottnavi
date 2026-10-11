import type { ReactNode } from 'react'
import { Link, useSearchParams } from 'react-router'
import { EmptyState } from '@/components/common/EmptyState'
import { ErrorState } from '@/components/common/ErrorState'
import { SourceAttribution } from '@/components/common/SourceAttribution'
import { Skeleton } from '@/components/ui/skeleton'
import { ToggleGroup, ToggleGroupItem } from '@/components/ui/toggle-group'
import { ProviderMappingTable } from '@/features/admin/components/providers/ProviderMappingTable'
import type { ServiceChoice } from '@/features/admin/components/providers/ProviderMappingRow'
import { useAdminProviders } from '@/features/admin/hooks/useAdminProviders'
import {
  applyProviderFilter,
  countUnmapped,
  DEFAULT_PROVIDER_FILTER,
  parseProviderFilter,
  type ProviderFilter,
} from '@/features/admin/providerMapping'
import { useOttServices } from '@/hooks/useOttServices'

// 보기 필터를 담는 주소 검색 파라미터 이름. 예: /admin/providers?filter=unmapped (기본값 "전체"는 주소에 적지 않는다. filter=all도 같은 뜻으로 읽는다)
const FILTER_PARAM = 'filter'

// 로딩 중 자리표시. 회색 줄은 장식이라 낭독기에는 숨기고, 상태 문장을 따로 읽힌다
function ProvidersSkeleton() {
  return (
    <div role="status" className="grid gap-3">
      <span className="sr-only">제공처 목록을 불러오는 중입니다</span>
      <div aria-hidden="true" className="grid gap-3">
        {[0, 1, 2, 3].map((i) => (
          <Skeleton key={i} className="h-14 w-full" />
        ))}
      </div>
    </div>
  )
}

/**
 * SCR-15 제공처 매핑 화면의 본문이다(Task 029).
 *
 * 위에서 아래로: 제목 → 안내 → 보기 필터 → 제공처 목록(표 / 카드) → JustWatch 출처.
 * 보기 필터는 URL(?filter=)에 둔다(새로고침해도 유지, frontend.md). 기본값(미매핑 먼저)은 주소에 적지 않는다.
 * 제공처 목록 쿼리는 관리자 메뉴(AdminLayout)의 "미매핑 n건" 표시와 같은 키라 요청 하나를 함께 쓴다.
 *
 * 안내 문구에 "매핑을 바꾸면 다음 수집부터 반영된다" 같은 동작은 쓰지 않는다. 백엔드 동작이 정해지지 않았기 때문이다(Task 031 안건).
 */
export function ProvidersScreen() {
  const providers = useAdminProviders()
  const services = useOttServices()
  const [searchParams, setSearchParams] = useSearchParams()
  const filter = parseProviderFilter(searchParams.get(FILTER_PARAM))

  // 서비스 선택지. 서비스 목록을 못 불러와도 화면을 막지 않는다(그때는 select에 서비스가 없고, 이미 연결된 값만 "서비스 N"으로 보인다)
  const serviceChoices: ServiceChoice[] = (services.data ?? []).map((s) => ({
    ottServiceId: s.ottServiceId,
    name: s.name,
  }))
  for (const provider of providers.data?.providers ?? []) {
    const id = provider.ottServiceId
    if (id !== null && !serviceChoices.some((s) => s.ottServiceId === id)) {
      serviceChoices.push({ ottServiceId: id, name: `서비스 ${id}` })
    }
  }

  function changeFilter(next: ProviderFilter) {
    setSearchParams(
      (prev) => {
        const params = new URLSearchParams(prev) // 목업 확인용 파라미터(mockScenario 등)를 지우지 않으려고 이어받는다
        if (next === DEFAULT_PROVIDER_FILTER) params.delete(FILTER_PARAM)
        else params.set(FILTER_PARAM, next)
        return params
      },
      { replace: true },
    )
  }

  const all = providers.data?.providers ?? []
  const unmappedCount = countUnmapped(all)

  let content: ReactNode
  if (providers.isPending) {
    content = <ProvidersSkeleton />
  } else if (providers.isError) {
    content = <ErrorState error={providers.error} onRetry={() => providers.refetch()} />
  } else if (all.length === 0) {
    content = (
      <EmptyState
        title="아직 발견된 제공처가 없습니다"
        description="수집을 한 번 실행하면 TMDB 제공처가 여기에 나타납니다."
        action={
          <Link to="/admin/collect" className="text-primary underline underline-offset-3">
            수집 화면으로 이동
          </Link>
        }
      />
    )
  } else {
    const visible = applyProviderFilter(all, filter)
    content =
      visible.length === 0 ? (
        <EmptyState title="미매핑 제공처가 없습니다" description="모두 검토했습니다." />
      ) : (
        <ProviderMappingTable providers={visible} services={serviceChoices} />
      )
  }

  return (
    <section className="flex flex-col gap-5">
      <h1 className="text-2xl font-semibold">제공처 매핑</h1>
      <p className="text-sm text-muted-foreground">
        TMDB가 알려 준 제공처를 7개 서비스 중 하나에 연결합니다. 연결하지 않은 제공처의 작품은
        계산에 쓰지 않습니다.
      </p>

      {providers.isSuccess && all.length > 0 && (
        <ToggleGroup
          type="single"
          variant="outline"
          spacing={0}
          aria-label="보기 필터"
          value={filter}
          // 같은 항목을 다시 누르면 Radix가 빈 값을 주는데, 필터는 항상 하나가 골라져 있어야 하므로 무시한다
          onValueChange={(value) => {
            if (value) changeFilter(value as ProviderFilter)
          }}
        >
          <ToggleGroupItem value="all" className="data-[state=on]:font-bold">
            전체 ({all.length})
          </ToggleGroupItem>
          <ToggleGroupItem value="unmapped-first" className="data-[state=on]:font-bold">
            미매핑 먼저
          </ToggleGroupItem>
          <ToggleGroupItem value="unmapped" className="data-[state=on]:font-bold">
            미매핑만 ({unmappedCount})
          </ToggleGroupItem>
        </ToggleGroup>
      )}

      {content}

      <SourceAttribution />
    </section>
  )
}
