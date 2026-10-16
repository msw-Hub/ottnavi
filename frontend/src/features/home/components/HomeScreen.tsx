import { useSearchParams } from 'react-router'
import { DataAsOf } from '@/components/common/DataAsOf'
import { SourceAttribution } from '@/components/common/SourceAttribution'
import { HomeIntro } from '@/features/home/components/HomeIntro'
import {
  ExclusivesRail,
  NewArrivalsRail,
  PopularRail,
  WishlistedRail,
} from '@/features/home/components/HomeRails'
import { MyPlanBanner } from '@/features/home/components/MyPlanBanner'
import type { RailStatus } from '@/features/home/components/RailSection'
import {
  getSampleExclusives,
  getSampleNewArrivals,
  getSamplePopularRanking,
  HOME_DATA_AS_OF,
  HOME_MOST_WISHLISTED,
  HOME_PLAN_SAMPLE,
  HOME_SERVICES,
} from '@/features/home/sampleData'
import { useSession } from '@/hooks/useSession'
import { isMockMode } from '@/lib/mockScenario'

// 인기 순위 줄에 보일 개수(TOP 10)
const POPULAR_RANKING_LIMIT = 10

// 목업 모드에서 상태 화면을 확인하는 주소 파라미터 값(?homePreview=…). 1차안 검토용이다
const PREVIEW_RAIL_STATUSES: RailStatus[] = ['loading', 'error', 'empty']

/**
 * 홈(`/`) 화면 본문이다(홈 1차안). pages/HomePage는 이 컴포넌트를 연결만 한다(pages는 얇게, frontend.md).
 *
 * 순서와 이유(정보 위계):
 * 1. 처음 온 사람: 한 줄 가치 제안 → 3단계 안내·시작 버튼 → 큰 검색창 → 목록 4줄. "이게 뭐고 무엇을 하면 되는지"가 첫 화면에 다 보인다.
 * 2. 로그인한 사람: 작은 제목 → 내 플랜 → 큰 검색창 → 목록 4줄. 반복 사용자가 가장 자주 하는 일(내 플랜 확인, 작품 검색)이 맨 위에 있다.
 * 3. 목록 4줄은 "무엇을 볼지 고르는" 순서다: 인기 순위(가장 넓은 관심) → 찜 많은 작품(이 서비스 사용자의 관심)
 *    → 새로 볼 수 있게 된 작품(변화) → 서비스 독점(어느 서비스를 구독할지 판단 근거).
 * 4. 맨 아래에 출처·기준일 전체 묶음(공통 SourceAttribution·DataAsOf)을 둔다. 목록마다 짧은 출처 줄도 따로 있다(RailSection).
 *
 * 데이터: 1차안은 정적 샘플(sampleData.ts)을 props로 넘긴다. API가 생기면 이 파일에서 샘플 대신 조회 훅 결과를 넘기면 된다.
 * 상태 화면 확인(목업 모드 전용): 주소에 ?homePreview=loading|error|empty를 붙이면 목록 4줄이 그 상태로, ?homePreview=no-plan이면
 * 내 플랜이 "플랜 없음"으로 보인다. 운영에서는 이 파라미터를 읽지 않는다.
 */
export function HomeScreen() {
  const session = useSession()
  const [searchParams] = useSearchParams()
  const isMember = session.status === 'authenticated'

  // 1. 목업 모드에서만 상태 화면 확인용 파라미터를 읽는다
  const preview = isMockMode() ? searchParams.get('homePreview') : null
  const railStatus: RailStatus = PREVIEW_RAIL_STATUSES.includes(preview as RailStatus)
    ? (preview as RailStatus)
    : 'ready'
  // 2. 내 플랜 샘플: 기본은 "플랜 있음", 확인용 파라미터면 "플랜 없음"
  const planSummary = preview === 'no-plan' ? null : HOME_PLAN_SAMPLE
  // 3. 목록 4줄이 함께 쓰는 값
  const common = { services: HOME_SERVICES, dataAsOf: HOME_DATA_AS_OF, status: railStatus }

  return (
    <div className="flex flex-col gap-10 sm:gap-12">
      {/* 로그인한 사용자는 내 플랜을 제목 바로 아래(검색창 위)에 둔다(HomeIntro의 memberSlot) */}
      <HomeIntro isMember={isMember} memberSlot={<MyPlanBanner summary={planSummary} />} />

      <PopularRail
        {...common}
        getRanking={(serviceId, period) =>
          getSamplePopularRanking(serviceId, POPULAR_RANKING_LIMIT, period)
        }
      />
      <WishlistedRail {...common} items={HOME_MOST_WISHLISTED} />
      <NewArrivalsRail {...common} getNewArrivals={getSampleNewArrivals} />
      <ExclusivesRail {...common} getExclusives={getSampleExclusives} />

      {/* 화면 전체의 출처·기준일. 문구는 검색 결과 화면과 같다 */}
      <div className="grid gap-1.5 border-t border-border pt-4">
        <SourceAttribution />
        <DataAsOf value={HOME_DATA_AS_OF} />
        <p className="text-sm text-muted-foreground">
          제공 여부는 확인된 범위이며, 실제 서비스 상황과 다를 수 있습니다. 순위·목록은 OTT가
          발표하는 공식 순위가 아닙니다.
        </p>
      </div>
    </div>
  )
}
