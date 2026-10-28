import { SourceAttribution } from '@/components/common/SourceAttribution'
import { HomeIntro } from '@/features/home/components/HomeIntro'
// 이전: import { HomeIntro, StartSteps } from '@/features/home/components/HomeIntro' (3단계 안내 상자를 없애 StartSteps는 쓰지 않는다)
import {
  ExclusivesRail,
  NewArrivalsRail,
  PopularRail,
  WishlistedRail,
} from '@/features/home/components/HomeRails'
import { MyPlanBanner } from '@/features/home/components/MyPlanBanner'
import { POPULAR_RANKING_LIMIT, usePopularityRanking } from '@/features/home/hooks/useHomeLists'
import { useSession } from '@/hooks/useSession'
import { useOttServices } from '@/hooks/useOttServices'

/**
 * 홈(`/`) 화면 본문이다. pages/HomePage는 이 컴포넌트를 연결만 한다(pages는 얇게, frontend.md).
 *
 * 순서와 이유(정보 위계):
 * 1. 처음 온 사람: 포스터 배너(한 줄 가치 제안·시작 버튼) → 인기 순위(큰 포스터) → 나머지 목록. 첫 화면에서 서비스 소개 한 문장과 큰 포스터가 함께 보인다.
 *    이전(2026-10-28 전): 인기 순위 줄 바로 아래에 "이렇게 써요" 3단계 안내 상자가 있었다. 그 전에는 3단계 상자가 첫 화면을 차지해 모바일에서 인기 순위가 안 보였다.
 * 2. 로그인한 사람: 작은 제목 → 내 플랜 → 목록 3줄. 반복 사용자가 가장 자주 하는 일(내 플랜 확인)이 맨 위에 있다.
 * 3. 목록은 "무엇을 볼지 고르는" 순서다: 인기 순위(가장 넓은 관심) → 찜 많은 작품(이 서비스 사용자의 관심) → 새로 볼 수 있게 된 작품(변화) → 서비스 독점 작품.
 * 4. 맨 아래에 출처 묶음(공통 SourceAttribution)을 한 번만 둔다. 데이터 기준일은 목록마다 제목 아래에 있다(RailSection).
 *    이전: 목록마다 짧은 출처·데이터 기준일 줄이 따로 있었다(H-1-2에서 맨 아래 한 곳으로 모음).
 *
 * 데이터: 각 줄이 공개 API(인기 순위, 찜 많은 작품, 새로 볼 수 있게 된 작품, 서비스별 독점 목록)를 직접 불러온다. 서비스 목록은 네 줄이 함께 쓰므로
 * 여기서 한 번 불러 넘긴다(같은 키라 요청은 한 번만 나간다). 줄마다 로딩·오류·빈 상태를 따로 가져서 한 줄이 실패해도 나머지는 보인다.
 *
 * 맨 아래 "서비스 독점 작품" 줄(Task 079)은 고른 서비스의 독점 목록 첫 쪽을 독점 API에서 받는다. 독점은 "어디를 구독할지" 고르는
 * 근거라 가장 나중에 둔다.
 * 화면 맨 아래에 데이터 기준일을 따로 쓰지 않는 이유: 줄마다 자기 데이터의 기준일을 밝히고, 줄마다 기준일이 다를 수 있기 때문이다.
 */
export function HomeScreen() {
  const session = useSession()
  const servicesQuery = useOttServices()
  const isMember = session.status === 'authenticated'
  // 배너 배경에 깔 포스터. 인기 순위 줄이 처음 보여 주는 요청(오늘·전체·같은 개수)과 같아서 요청은 한 번만 나간다.
  // 로그인한 사용자는 배너가 없어 요청하지 않는다(isEnabled=false). 받는 중·실패일 때는 빈 목록이고, 배너는 포스터 자리에 토큰 색 칸을 그린다.
  const bannerRanking = usePopularityRanking('TODAY', null, POPULAR_RANKING_LIMIT, !isMember)
  const heroPosterPaths = (bannerRanking.data?.items ?? []).map((item) => item.title.posterPath)

  return (
    // 줄(구간) 사이 40px(640px 미만) / 48px(이상). 값은 그대로 두고 공통 구간 간격 토큰 이름으로 바꿨다(styles/tokens.css R1)
    // 이전: <div className="flex flex-col gap-10 sm:gap-12">
    <div className="flex flex-col gap-section">
      {/* 로그인한 사용자는 내 플랜을 제목 바로 아래에 둔다(HomeIntro의 memberSlot) */}
      {/* B안: 소개와 인기 순위 사이만 간격을 좁혀(gap-6 · 640px 이상 gap-8) 첫 화면에 포스터가 더 들어오게 한다. 다른 줄 사이 간격은 그대로다 */}
      {/* 소개가 둥근 배너 패널(시안 A)이 된 뒤로는 패널의 테두리와 둥근 모서리가 경계를 맡아, 패널과 인기 순위 줄 사이를 24px(gap-6)로 둔다.
          경계가 형태로 보여 큰 여백이 필요 없고, 첫 화면에 인기 순위 포스터가 더 들어온다.
          이전(전체 폭 띠): 소개가 아래 구분선이 있는 띠가 된 뒤로는 선이 경계를 맡아 간격을 24px로 고정한다(띠 안 아래 여백 24px + 선 + 24px).
          이전(B안): gap-6 sm:gap-8 */}
      <div className="flex flex-col gap-6">
        <HomeIntro
          isMember={isMember}
          memberSlot={<MyPlanBanner />}
          heroPosterPaths={heroPosterPaths}
        />
        <PopularRail servicesQuery={servicesQuery} />
      </div>
      {/* 이전:
      <HomeIntro isMember={isMember} memberSlot={<MyPlanBanner />} />
      <PopularRail servicesQuery={servicesQuery} /> */}
      {/* 2026-10-28 사용자 결정: 비로그인 홈의 "이렇게 써요" 3단계 안내 상자를 없앤다(배너와 시작 버튼이 이미 서비스 설명과 첫 행동을 맡아 중복이다).
          이 상자 안에 있던 "로그인하지 않아도 검색창에서 작품을 찾을 수 있어요" 안내도 함께 사라진다(StartSteps 컴포넌트는 HomeIntro.tsx에 남겨 둠).
      이전(B안: 인기 순위 줄 바로 아래, 비로그인에게만): {!isMember && <StartSteps />} */}
      <WishlistedRail servicesQuery={servicesQuery} />
      <NewArrivalsRail servicesQuery={servicesQuery} />
      <ExclusivesRail servicesQuery={servicesQuery} />

      {/* 화면 전체의 출처. 문구는 검색 결과 화면과 같다 */}
      <div className="grid gap-1.5 border-t border-border pt-4">
        <SourceAttribution />
        <p className="text-sm text-muted-foreground">
          제공 여부는 확인된 범위이며, 실제 서비스 상황과 다를 수 있습니다. 순위·목록은 OTT가
          발표하는 공식 순위가 아닙니다.
        </p>
        {/* H-1-2: 줄마다 붙던 출처 한 줄을 여기로 모았다. 기준일은 목록마다 달라 한 날짜로 합치지 않고, 어디서 보는지만 알린다 */}
        <p className="text-sm text-muted-foreground">
          데이터 기준일은 목록마다 제목 아래에 적어 두었습니다.
        </p>
      </div>
    </div>
  )
}
