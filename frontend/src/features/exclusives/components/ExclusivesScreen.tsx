import {
  ChevronDownIcon,
  CircleHelpIcon,
  DatabaseIcon,
  FilmIcon,
  ScaleIcon,
  SearchIcon,
} from 'lucide-react'
import { Link, useSearchParams } from 'react-router'
import { DataAsOf } from '@/components/common/DataAsOf'
import { DataInsufficientTag } from '@/components/common/DataInsufficientTag'
import { EmptyState } from '@/components/common/EmptyState'
import { ErrorState } from '@/components/common/ErrorState'
import { LoadingState } from '@/components/common/LoadingState'
import { SourceAttribution } from '@/components/common/SourceAttribution'
import { Button } from '@/components/ui/button'
import { ExclusiveServiceTabs } from '@/features/exclusives/components/ExclusiveServiceTabs'
import { ExclusiveTitleCard } from '@/features/exclusives/components/ExclusiveTitleCard'
import type { ExclusiveService } from '@/features/exclusives/mockTypes'
import {
  EXCLUSIVE_SERVICES,
  EXCLUSIVES_DATA_AS_OF,
  getSampleExclusiveTitles,
} from '@/features/exclusives/sampleData'
import { withJosa } from '@/lib/josa'
import { isMockMode } from '@/lib/mockScenario'

// 고른 서비스 탭을 담는 주소 파라미터 이름(?service=NETFLIX). 새로고침·공유 때 같은 탭이 열리게 URL에 둔다(frontend.md 화면 위치는 URL)
const SERVICE_PARAM = 'service'

// 목업 모드에서 상태 화면을 확인하는 주소 파라미터 이름과 값(?exclusivesPreview=…). 1차안 검토용이다(변경 내역 화면과 같은 방식)
const EXCLUSIVES_PREVIEW_PARAM = 'exclusivesPreview'
type ExclusivesPreview = 'list' | 'empty' | 'loading' | 'error'
const EXCLUSIVES_PREVIEWS: ExclusivesPreview[] = ['list', 'empty', 'loading', 'error']

/**
 * SCR-03 서비스별 독점 작품 화면의 본문이다(Task 075 1차안). pages/ExclusivesPage는 이 컴포넌트를 연결만 한다.
 *
 * 이 화면이 하는 일: 국내 OTT 7곳 중 한 곳에서만 구독으로 볼 수 있다고 확인된 작품을 서비스별로 보여 준다.
 * "이 서비스를 구독하면 여기서만 볼 수 있는 게 뭐지?"에 답해 구독할 곳을 고르는 근거가 된다.
 *
 * 위에서 아래 순서와 이유(정보 위계):
 * 1. 제목·한 줄 설명·데이터 기준일: 독점이 "확인된 범위"라는 것과 언제 데이터인지를 목록보다 먼저 알린다(PRD FR-04, 5.6).
 * 2. 산정 기준 상자: 결과를 확정처럼 읽지 않게 핵심 기준 두 가지(모름 포함 / 영화는 쿠팡플레이 비교 제외)는 항상 보이고,
 *    나머지 세부 기준은 접어 둔다(모바일에서 목록이 너무 아래로 밀리지 않게).
 * 3. 서비스 탭 7개 → 고른 서비스의 작품 수 요약 → 작품 카드 격자. 쿠팡플레이는 목록 대신 데이터 부족 안내(PRD SCR-03).
 * 4. 맨 아래 출처(JustWatch)와 "확인된 범위" 안내.
 *
 * 데이터: 1차안은 정적 샘플(sampleData.ts)을 쓴다. API(Task 078)가 생기면 서비스별 조회 훅의 결과를 넘기면 된다.
 * 상태 화면 확인(목업 모드 전용): 주소에 ?exclusivesPreview=empty|loading|error를 붙이면 고른 탭의 목록 칸이 그 상태로 보인다.
 * 운영에서는 이 파라미터를 읽지 않는다.
 */
export function ExclusivesScreen() {
  const [searchParams, setSearchParams] = useSearchParams()

  // 1. 주소의 서비스 코드로 탭을 고른다. 없거나 모르는 코드면 첫 서비스(넷플릭스)다
  const service =
    EXCLUSIVE_SERVICES.find((entry) => entry.code === searchParams.get(SERVICE_PARAM)) ??
    EXCLUSIVE_SERVICES[0]

  // 2. 목업 모드에서만 상태 확인용 파라미터를 읽는다. 모르는 값이면 목록 있음으로 본다
  const rawPreview = isMockMode() ? searchParams.get(EXCLUSIVES_PREVIEW_PARAM) : null
  const preview: ExclusivesPreview = EXCLUSIVES_PREVIEWS.includes(rawPreview as ExclusivesPreview)
    ? (rawPreview as ExclusivesPreview)
    : 'list'

  // 3. 탭을 바꾸면 주소의 서비스 코드만 바꾼다(확인용 파라미터는 이어받는다). 뒤로 가기 기록을 쌓지 않으려고 replace로 바꾼다
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

  return (
    <section className="flex flex-col gap-5">
      <div className="grid gap-2">
        {/* 제목은 라우터 경로표(router.test)와 준비 중 화면의 이름 "독점 작품 목록"에 맞췄다(1차안 처음엔 "서비스별 독점 작품") */}
        <h1 className="text-2xl font-semibold">독점 작품 목록</h1>
        <p className="break-keep text-muted-foreground">
          국내 OTT 7곳 중 <strong className="font-semibold text-foreground">한 곳에서만</strong>{' '}
          구독으로 볼 수 있다고 확인된 작품이에요. 어디를 구독할지 고를 때 참고해 주세요.
        </p>
        <DataAsOf value={EXCLUSIVES_DATA_AS_OF} />
      </div>

      <CriteriaNote />

      <ExclusiveServiceTabs
        services={EXCLUSIVE_SERVICES}
        value={service.code}
        onValueChange={selectService}
      >
        <ServicePanel
          service={service}
          preview={preview}
          onRetry={() => {
            // 1차안: "다시 시도"는 확인용 파라미터를 지워 목록 화면으로 돌아간다. 실제 화면은 조회 훅의 refetch를 넘긴다
            const next = new URLSearchParams(searchParams)
            next.delete(EXCLUSIVES_PREVIEW_PARAM)
            setSearchParams(next)
          }}
        />
      </ExclusiveServiceTabs>

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
          <span>
            다른 서비스의 제공 여부가 <strong className="font-semibold">모름</strong>인 경우도
            포함돼요. 그곳에서도 볼 수 있을 수 있어서 작품마다 &quot;모름&quot;이 몇 곳인지 함께
            적었어요.
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
          <li>
            쿠팡플레이는 제공 정보가 부족해 쿠팡플레이의 독점 여부는 판단하지 않아요(드라마 비교에는
            포함해요).
          </li>
          <li>매일 수집이 끝난 뒤 다시 계산해요. 순서는 TMDB 인기도 순이에요.</li>
        </ol>
      </details>
    </aside>
  )
}

interface ServicePanelProps {
  service: ExclusiveService // 지금 고른 서비스
  preview: ExclusivesPreview // 1차안 상태 확인 값(목업 모드에서만 list 외의 값이 온다)
  onRetry: () => void // 오류 상태의 "다시 시도"
}

// 고른 탭 안의 내용: 데이터 부족 안내 → 로딩 → 오류 → 빈 상태 → 작품 격자 순으로 고른다
function ServicePanel({ service, preview, onRetry }: ServicePanelProps) {
  // 1. 데이터 부족 서비스(쿠팡플레이)는 목록 대신 안내를 보인다(PRD FR-04, SCR-03). 확인용 상태보다 먼저 본다
  if (service.dataQuality === 'INSUFFICIENT') {
    return <InsufficientNotice serviceName={service.name} />
  }

  // 2. 확인용 상태별 본문
  if (preview === 'loading') {
    return <LoadingState message={`${service.name} 독점 작품을 불러오고 있어요`} />
  }
  if (preview === 'error') {
    return (
      <ErrorState
        title="독점 작품을 불러오지 못했어요"
        message="잠시 후 다시 시도해 주세요."
        onRetry={onRetry}
      />
    )
  }

  const titles = preview === 'empty' ? [] : getSampleExclusiveTitles(service.ottServiceId)
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

  // 3. 작품 격자. 375px에서 두 칸(카드 약 160px)이고 넓어질수록 칸을 늘린다
  return (
    <div className="grid gap-3">
      <p className="text-sm text-muted-foreground tabular-nums">
        <strong className="font-semibold text-foreground">{service.name}</strong> 독점으로 확인된
        작품 {titles.length}편 · 인기순
      </p>
      <ul
        aria-label={`${service.name} 독점 작품`}
        className="grid grid-cols-2 gap-x-3 gap-y-5 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-6"
      >
        {titles.map((title) => (
          <li key={`${title.mediaType}-${title.tmdbId}`}>
            <ExclusiveTitleCard title={title} />
          </li>
        ))}
      </ul>
    </div>
  )
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
