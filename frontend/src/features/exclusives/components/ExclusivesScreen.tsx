import {
  ChevronDownIcon,
  CircleHelpIcon,
  DatabaseIcon,
  FilmIcon,
  ScaleIcon,
  SearchIcon,
} from 'lucide-react'
import type { ReactNode } from 'react'
import { Link, useSearchParams } from 'react-router'
import type { OttService } from '@/api/generated/model'
import { DataAsOf } from '@/components/common/DataAsOf'
import { DataInsufficientTag } from '@/components/common/DataInsufficientTag'
import { EmptyState } from '@/components/common/EmptyState'
import { ErrorState } from '@/components/common/ErrorState'
import { LoadingState } from '@/components/common/LoadingState'
import { SourceAttribution } from '@/components/common/SourceAttribution'
import { Button } from '@/components/ui/button'
import { ExclusiveServiceTabs } from '@/features/exclusives/components/ExclusiveServiceTabs'
import { ExclusiveTitleCard } from '@/features/exclusives/components/ExclusiveTitleCard'
import {
  ExclusiveTitleRail,
  ExclusiveTitleRailItem,
  ExclusiveTitleRailSkeleton,
} from '@/features/exclusives/components/ExclusiveTitleRail'
// import { EXCLUSIVE_PAGE_SIZE, useExclusiveTitles } from '@/features/exclusives/hooks/useExclusiveTitles' // "24 / 30편" 표시를 되살릴 때 EXCLUSIVE_PAGE_SIZE까지 푼다
import { useExclusiveTitles } from '@/features/exclusives/hooks/useExclusiveTitles'
import { useOttServices } from '@/hooks/useOttServices'
import { withJosa } from '@/lib/josa'

// 고른 서비스 탭을 담는 주소 파라미터 이름(?service=NETFLIX). 새로고침·공유 때 같은 탭이 열리게 URL에 둔다(frontend.md 화면 위치는 URL)
const SERVICE_PARAM = 'service'

/**
 * SCR-03 서비스별 독점 작품 화면의 본문이다. pages/ExclusivesPage는 이 컴포넌트를 연결만 한다.
 *
 * 이 화면이 하는 일: 국내 OTT 7곳 중 한 곳에서만 구독으로 볼 수 있다고 확인된 작품을 서비스별로 보여 준다.
 * "이 서비스를 구독하면 여기서만 볼 수 있는 게 뭐지?"에 답해 구독할 곳을 고르는 근거가 된다.
 *
 * 위에서 아래 순서와 이유(정보 위계):
 * 1. 제목·한 줄 설명·데이터 기준일: 독점이 "확인된 범위"라는 것과 언제 데이터인지를 목록보다 먼저 알린다(PRD FR-04, 5.6).
 * 2. 산정 기준 상자: 결과를 확정처럼 읽지 않게 핵심 기준 두 가지(모름 포함 / 영화는 쿠팡플레이 비교 제외)는 항상 보이고,
 *    나머지 세부 기준은 접어 둔다(모바일에서 목록이 너무 아래로 밀리지 않게).
 * 3. 서비스 탭 7개 → 고른 서비스의 작품 수 요약 → 옆으로 넘기는 작품 가로 줄.
 *    데이터 부족 서비스(쿠팡플레이)는 요약 줄 위에 "드라마만 확인했다"는 작은 안내를 붙인다(목록은 그대로 보인다).
 * 4. 맨 아래 출처(JustWatch)와 "확인된 범위" 안내.
 *
 * 데이터: 서비스 탭은 서비스 목록 API, 작품은 서비스별 독점 목록 API(listServiceExclusiveTitles)에서 받는다.
 * 한 번에 24편씩 받고, 줄의 오른쪽 끝 가까이 넘기면 다음 쪽을 스스로 불러와 줄 뒤에 이어 붙인다. 탭을 바꾸면 그 서비스의 첫 쪽·첫 위치부터 다시 시작한다.
 * 이전: 아래로 펼쳐지는 격자 + 맨 아래 "더보기" 버튼이었다. 다른 OTT 앱처럼 옆으로 넘기는 편이 낫다는 사용자 의견으로 바꿨다.
 * 서버가 isJudged=false로 답하면(예전 응답) 목록 대신 데이터 부족 안내를 보이는 분기도 남겨 두었다.
 */
export function ExclusivesScreen() {
  const [searchParams, setSearchParams] = useSearchParams()
  const servicesQuery = useOttServices()
  const services = servicesQuery.data ?? []

  // 1. 주소의 서비스 코드로 탭을 고른다. 없거나 모르는 코드면 첫 서비스(넷플릭스)다. 서비스 목록이 오기 전에는 null이다
  const service =
    services.find((entry) => entry.code === searchParams.get(SERVICE_PARAM)) ?? services[0] ?? null

  // 2. 고른 서비스의 독점 목록. 서비스가 정해지기 전에는 요청을 보내지 않는다
  const exclusives = useExclusiveTitles(service?.ottServiceId ?? null)

  // 3. 탭을 바꾸면 주소의 서비스 코드만 바꾼다(다른 파라미터는 이어받는다). 뒤로 가기 기록을 쌓지 않으려고 replace로 바꾼다
  function selectService(code: string) {
    setSearchParams(
      (prev) => {
        const next = new URLSearchParams(prev)
        next.set(SERVICE_PARAM, code)
        return next
      },
      { replace: true },
    )
  }

  // 4. 탭 영역. 서비스 목록이 없으면 탭 자체를 못 그리므로 화면이 로딩·오류·빈 상태를 맡고, 탭이 뜬 뒤의 상태는 ServicePanel이 맡는다
  //    (그래야 탭을 바꿔 새로 받는 동안에도 탭이 사라지지 않는다)
  let tabsContent: ReactNode
  if (servicesQuery.isPending) {
    tabsContent = <LoadingState message="서비스 목록을 불러오고 있어요" />
  } else if (servicesQuery.isError) {
    tabsContent = (
      <ErrorState
        title="서비스 목록을 불러오지 못했어요"
        error={servicesQuery.error}
        onRetry={() => void servicesQuery.refetch()}
      />
    )
  } else if (!service) {
    tabsContent = <EmptyState title="보여 줄 서비스가 없어요" />
  } else {
    tabsContent = (
      <ExclusiveServiceTabs services={services} value={service.code} onValueChange={selectService}>
        <ServicePanel service={service} exclusives={exclusives} />
      </ExclusiveServiceTabs>
    )
  }

  return (
    <section className="flex flex-col gap-5">
      <div className="grid gap-2">
        {/* 제목은 라우터 경로표(router.test)와 준비 중 화면의 이름 "독점 작품 목록"에 맞췄다(1차안 처음엔 "서비스별 독점 작품") */}
        <h1 className="text-2xl font-semibold">독점 작품 목록</h1>
        <p className="break-keep text-muted-foreground">
          국내 OTT 7곳 중 <strong className="font-semibold text-foreground">한 곳에서만</strong>{' '}
          구독으로 볼 수 있다고 확인된 작품이에요. 어디를 구독할지 고를 때 참고해 주세요.
        </p>
        {/* 기준일은 서버가 독점을 계산한 날이다. 목록을 받기 전에는 "기준일 정보 없음"으로 보인다 */}
        <DataAsOf value={exclusives.data?.dataAsOf ?? null} />
      </div>

      <CriteriaNote />

      {tabsContent}

      <div className="grid gap-1.5 border-t border-border pt-4">
        <SourceAttribution />
        <p className="text-sm break-keep text-muted-foreground">
          독점 여부는 확인된 범위이며, 실제 서비스 화면과 다를 수 있습니다. 데이터는 매일 다시
          수집해 계산합니다.
        </p>
      </div>
    </section>
  )
}

/**
 * 산정 기준 상자다. 독점을 "확정"으로 읽지 않게 하는 핵심 장치라 목록 위에 둔다(PRD FR-04, 5.6 표현 수위).
 *
 * 항상 보이는 두 줄과 접힌 세부 기준으로 나눈 이유:
 * - "모름도 포함"과 "영화는 쿠팡플레이 비교 제외(구조적 공백)"는 결과 해석을 바꾸는 기준이라 접으면 안 된다(PRD FR-04 "화면에 명시").
 * - 대여·구매 제외, 시즌 단위 판단 같은 세부는 궁금한 사람만 펼쳐 본다. 모바일에서 목록이 화면 두 장 아래로 밀리는 것을 막는다.
 * 펼치기는 <details>/<summary>를 쓴다: 키보드(Enter/Space)와 낭독기의 펼침 상태 알림을 브라우저가 기본으로 해 준다.
 * 상자는 경고가 아니라 안내라 info 토큰(옅은 파랑 바탕 + 밝은 파랑 글자, 대비 약 9:1)을 쓴다(변경 내역 화면의 안내 띠와 같은 색).
 */
function CriteriaNote() {
  return (
    <aside
      aria-labelledby="exclusives-criteria-heading"
      className="grid gap-2.5 rounded-xl border border-info bg-info-bg px-4 py-3 text-info"
    >
      <h2
        id="exclusives-criteria-heading"
        className="flex items-center gap-2 text-base font-semibold"
      >
        <ScaleIcon aria-hidden="true" className="size-5 shrink-0" />
        확인된 범위의 독점이에요
      </h2>
      <ul className="grid gap-2 text-sm break-keep">
        <li className="flex items-start gap-2">
          {/* 아이콘은 줄마다 뜻을 보조한다. 같은 뜻의 글자가 있어 낭독기에는 숨긴다 */}
          <CircleHelpIcon aria-hidden="true" className="mt-0.5 size-4 shrink-0" />
          {/* 이전 뒷문장: "그곳에서도 볼 수 있을 수 있어서 작품마다 "모름"이 몇 곳인지 함께 적었어요."
              카드의 "다른 N곳: 없음·모름" 줄을 빼면서(2차안) 문장도 줄였다 */}
          <span>
            다른 서비스의 제공 여부가 <strong className="font-semibold">모름</strong>인 경우도
            포함돼요. 그곳에서도 볼 수 있을 수 있어요.
          </span>
        </li>
        <li className="flex items-start gap-2">
          <FilmIcon aria-hidden="true" className="mt-0.5 size-4 shrink-0" />
          <span>
            <strong className="font-semibold">영화는 쿠팡플레이와 비교하지 않았어요.</strong> TMDB가
            쿠팡플레이의 영화 제공 정보를 주지 않아(구조적 공백) 쿠팡플레이에도 있을 수 있어요.
          </span>
        </li>
      </ul>
      <details className="group text-sm">
        <summary className="inline-flex min-h-11 cursor-pointer list-none items-center gap-1 rounded-md font-semibold underline underline-offset-3 focus-visible:ring-3 focus-visible:ring-ring/50 focus-visible:outline-none [&::-webkit-details-marker]:hidden">
          산정 기준 자세히
          <ChevronDownIcon
            aria-hidden="true"
            className="size-4 transition-transform group-open:rotate-180 motion-reduce:transition-none"
          />
        </summary>
        <ol className="mt-1 grid list-decimal gap-1.5 pl-5 break-keep">
          <li>
            정액 구독으로 &quot;있음&quot;이 확인된 곳이 7곳 중 이 서비스 한 곳뿐인 작품이에요.
          </li>
          <li>대여·구매로만 볼 수 있는 곳은 세지 않아요.</li>
          <li>
            드라마는 시즌마다 따로 판단해요. 일부 시즌만 독점일 수 있어 카드에 시즌을 적었어요.
          </li>
          {/* 이전: "쿠팡플레이는 제공 정보가 부족해 쿠팡플레이의 독점 여부는 판단하지 않아요(드라마 비교에는 포함해요)."
              서버가 쿠팡플레이도 드라마 독점은 판정하게 바뀌어 문구를 맞췄다 */}
          <li>쿠팡플레이는 영화 제공 정보를 받지 못해, 쿠팡플레이 독점은 드라마만 확인해요.</li>
          {/* 정렬 규칙 변경(독점: 공개일이 최근 6개월 안인 작품이 먼저, 같은 묶음 안에서는 인기도순)에 맞춘 문구.
              이전: "매일 수집이 끝난 뒤 다시 계산해요. 순서는 TMDB 인기도 순이에요." */}
          <li>
            매일 수집이 끝난 뒤 다시 계산해요. 순서는 최근 6개월 안에 공개된 작품이 먼저이고, 같은
            묶음 안에서는 TMDB 인기도가 높은 순이에요.
          </li>
        </ol>
      </details>
    </aside>
  )
}

interface ServicePanelProps {
  service: OttService // 지금 고른 서비스
  exclusives: ReturnType<typeof useExclusiveTitles> // 고른 서비스의 독점 목록 조회 결과(지금까지 받은 쪽들, 로딩·오류 상태 포함)
}

/**
 * 고른 탭 안의 내용이다. 데이터 부족 서비스(쿠팡플레이)면 맨 위에 작은 안내를 붙이고, 그 아래는 상태에 따라 하나를 고른다.
 *
 * 쿠팡플레이 안내를 목록 "대신"이 아니라 목록 "위"에 둔 이유: 서버가 쿠팡플레이도 드라마 독점은 판정하게 바뀌어(영화만 판정 불가)
 * 목록은 그대로 보이고, "영화는 빠졌다"는 범위만 알려 주면 된다. 서비스 목록의 dataQuality로 고르므로 로딩·빈 상태에서도 같은 안내가 보인다.
 */
function ServicePanel({ service, exclusives }: ServicePanelProps) {
  return (
    // grid-cols-1(= minmax(0, 1fr)): 칸의 최소 폭을 0으로 둬, 안의 가로 줄이 카드 전체 폭만큼 화면을 넓히지 않게 한다(홈 RailSection과 같은 이유)
    <div className="grid grid-cols-1 gap-3">
      {service.dataQuality === 'INSUFFICIENT' && (
        <p className="flex flex-wrap items-center gap-x-1.5 gap-y-1 text-sm break-keep text-muted-foreground">
          {/* "데이터 부족" 태그는 아이콘 + 글자라 색만으로 구분하지 않는다(홈·랭킹의 데이터 부족 안내와 같은 모양) */}
          <DataInsufficientTag />
          <span>
            {withJosa(service.name, '은/는')} 영화 제공 정보를 받지 못해 드라마만 확인했어요.
          </span>
        </p>
      )}
      <ServicePanelBody service={service} exclusives={exclusives} />
    </div>
  )
}

// 탭 안의 본문: 로딩 → 오류 → (구버전 응답) 판정 안 함 안내 → 빈 상태 → 작품 가로 줄 순으로 고른다
function ServicePanelBody({ service, exclusives }: ServicePanelProps) {
  // 1. 처음 받는 중이거나 처음 받기에 실패한 경우. (다음 쪽을 받다 실패한 경우는 받아 둔 목록을 지우지 않으려고 아래에서 따로 다룬다)
  if (exclusives.isPending) {
    // 이전: 가운데 동그라미 로딩(LoadingState). 목록이 가로 한 줄이 되어 자리 모양을 맞춘 회색 카드 줄로 바꿨다
    // return <LoadingState message={`${service.name} 독점 작품을 불러오고 있어요`} />
    return <ExclusiveTitleRailSkeleton message={`${service.name} 독점 작품을 불러오고 있어요`} />
  }
  if (!exclusives.data) {
    // 받는 중도 아닌데 받은 것이 없으면 처음 받기에 실패한 것이다
    return (
      <ErrorState
        title="독점 작품을 불러오지 못했어요"
        error={exclusives.error}
        onRetry={() => void exclusives.refetch()}
      />
    )
  }
  const { items: titles, totalElements, isJudged } = exclusives.data

  // 2. 서버가 독점을 판정하지 않았다고 답한 경우(isJudged=false)만 목록 대신 큰 안내를 보인다.
  //    지금 서버는 쿠팡플레이도 드라마는 판정해 이 길로 오지 않는다. 예전 응답이 와도 화면이 깨지지 않게 남겨 둔 분기다
  if (!isJudged) {
    return <InsufficientNotice serviceName={service.name} />
  }

  // 3. 판정은 했지만 독점 작품이 없는 경우
  if (titles.length === 0) {
    return (
      <EmptyState
        title={`${service.name} 독점으로 확인된 작품이 아직 없어요`}
        description="다른 서비스에도 있다고 확인됐거나, 아직 제공 정보가 모이지 않았을 수 있어요. 매일 다시 계산해요."
        action={
          <Button asChild variant="outline" className="mt-1 h-11">
            <Link to="/search">
              <SearchIcon aria-hidden="true" />
              작품 검색하기
            </Link>
          </Button>
        }
      />
    )
  }

  // 4. 작품 가로 줄. 요약 줄의 편수는 전체 수다.
  //    이전: 오른쪽에 지금까지 불러온 수 "24 / 30편"(여러 쪽짜리일 때만)과 낭독기용 "전체 N편 중 M편을 불러왔어요"를 보였다.
  //    사용자 결정(2026-10-19)으로 뺐다. 다시 넣으려면 아래 주석과 위 import의 EXCLUSIVE_PAGE_SIZE를 푼다
  // const isMultiPage = totalElements > EXCLUSIVE_PAGE_SIZE
  return (
    <div className="grid grid-cols-1 gap-1">
      <div className="flex flex-wrap items-baseline justify-between gap-x-3 gap-y-1">
        <p className="text-sm text-muted-foreground tabular-nums">
          <strong className="font-semibold text-foreground">{service.name}</strong> 독점으로 확인된
          작품 {totalElements}편 · 최근 공개작 먼저
          {/* 이전: "· 인기순". 최근 6개월 안 공개작이 앞에 오도록 정렬이 바뀌어 순서의 뜻을 맞췄다 */}
        </p>
        {/* {isMultiPage && (
          <p className="text-xs text-muted-foreground tabular-nums">
            <span aria-hidden="true">
              {titles.length} / {totalElements}편
            </span>
            <span className="sr-only">
              전체 {totalElements}편 중 {titles.length}편을 불러왔어요
            </span>
          </p>
        )} */}
      </div>
      <ExclusiveTitleRail
        label={`${service.name} 독점 작품`}
        itemCount={titles.length}
        hasMore={exclusives.hasNextPage}
        isLoadingMore={exclusives.isFetchingNextPage}
        isLoadMoreError={exclusives.isFetchNextPageError}
        onLoadMore={() => void exclusives.fetchNextPage()}
      >
        {/* 순위 = 지금까지 불러온 목록에서의 위치(1부터). 서버가 정한 순서(최근 6개월 안 공개작 먼저 → 같은 묶음 안 인기도순)대로 주므로
            다음 쪽이 뒤에 붙어도 25, 26…으로 이어진다. 요약 줄의 "최근 공개작 먼저"가 이 숫자의 뜻을 말한다.
            이전 설명: 서버가 인기도 내림차순(동률은 작품 ID)으로 주고, 요약 줄의 "인기순"이 숫자의 뜻을 말했다 */}
        {titles.map((title, index) => (
          <ExclusiveTitleRailItem key={`${title.mediaType}-${title.tmdbId}`}>
            <ExclusiveTitleCard title={title} rank={index + 1} />
          </ExclusiveTitleRailItem>
        ))}
      </ExclusiveTitleRail>
    </div>
  )

  // 이전: 아래로 이어 붙이는 격자 + "더보기" 버튼. 다른 OTT 앱처럼 옆으로 넘겨 보는 한 줄로 바꿨다(사용자 의견, 1차안)
  // 격자: <ul className="grid grid-cols-2 gap-x-3 gap-y-5 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-6"> 안에 카드를 <li>로 늘어놓았다
  // 더보기: hasNextPage일 때 <Button variant="outline" size="lg" className="h-11 px-6">, 받는 중에는 disabled + "불러오는 중…",
  //         다음 쪽 실패 때는 버튼 위에 <p role="alert" className="text-center text-sm text-destructive">다음 작품을 불러오지 못했어요. 다시 눌러 주세요.</p>
}

/**
 * 데이터 부족 서비스(쿠팡플레이) 탭의 안내다. 빈 상태(EmptyState)와 모양을 달리한 이유:
 * "독점 작품이 없다"가 아니라 "판단하지 않는다"는 뜻이라, 빈 상자 아이콘 대신 데이터 아이콘과 "데이터 부족" 태그를 쓴다.
 * 다음 행동(작품 검색)을 함께 둬 막다른 화면이 되지 않게 한다.
 */
function InsufficientNotice({ serviceName }: { serviceName: string }) {
  return (
    <div className="grid content-start justify-items-center gap-2 rounded-lg border border-dashed border-input bg-background px-4 py-6 text-center">
      <DatabaseIcon aria-hidden="true" className="size-8 text-muted-foreground" />
      <DataInsufficientTag />
      <p className="font-bold break-keep">
        {withJosa(serviceName, '은/는')} 독점 여부를 판단하지 않아요
      </p>
      <p className="max-w-prose text-sm break-keep text-muted-foreground">
        TMDB(JustWatch)에 {serviceName}의 제공 정보가 적어, 다른 서비스에 없는 작품인지 확인할 수
        없기 때문이에요. 작품마다의 제공 상태는 작품 상세 화면에서 볼 수 있어요.
      </p>
      <Button asChild variant="outline" className="mt-1 h-11">
        <Link to="/search">
          <SearchIcon aria-hidden="true" />
          작품 검색하기
        </Link>
      </Button>
    </div>
  )
}
