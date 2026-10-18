import { SourceAttribution } from '@/components/common/SourceAttribution'
import { HomeIntro } from '@/features/home/components/HomeIntro'
import { NewArrivalsRail, PopularRail, WishlistedRail } from '@/features/home/components/HomeRails'
import { MyPlanBanner } from '@/features/home/components/MyPlanBanner'
import { useSession } from '@/hooks/useSession'
import { useOttServices } from '@/hooks/useOttServices'

/**
 * 홈(`/`) 화면 본문이다. pages/HomePage는 이 컴포넌트를 연결만 한다(pages는 얇게, frontend.md).
 *
 * 순서와 이유(정보 위계):
 * 1. 처음 온 사람: 한 줄 가치 제안 → 3단계 안내·시작 버튼 → 목록 3줄. "이게 뭐고 무엇을 하면 되는지"가 첫 화면에 다 보인다.
 * 2. 로그인한 사람: 작은 제목 → 내 플랜 → 목록 3줄. 반복 사용자가 가장 자주 하는 일(내 플랜 확인)이 맨 위에 있다.
 * 3. 목록은 "무엇을 볼지 고르는" 순서다: 인기 순위(가장 넓은 관심) → 찜 많은 작품(이 서비스 사용자의 관심) → 새로 볼 수 있게 된 작품(변화).
 * 4. 맨 아래에 출처 묶음(공통 SourceAttribution)을 둔다. 목록마다 짧은 출처·데이터 기준일 줄도 따로 있다(RailSection).
 *
 * 데이터: 각 줄이 공개 API(인기 순위, 찜 많은 작품, 새로 볼 수 있게 된 작품)를 직접 불러온다. 서비스 목록은 세 줄이 함께 쓰므로
 * 여기서 한 번 불러 넘긴다(같은 키라 요청은 한 번만 나간다). 줄마다 로딩·오류·빈 상태를 따로 가져서 한 줄이 실패해도 나머지는 보인다.
 *
 * "서비스 독점 작품" 줄은 독점 API(Task 078)가 생길 때까지 그리지 않는다(ExclusivesRail은 그때 쓰려고 남겨 두었다).
 * 화면 맨 아래에 데이터 기준일을 따로 쓰지 않는 이유: 줄마다 자기 데이터의 기준일을 밝히고, 줄마다 기준일이 다를 수 있기 때문이다.
 */
export function HomeScreen() {
  const session = useSession()
  const servicesQuery = useOttServices()
  const isMember = session.status === 'authenticated'

  return (
    <div className="flex flex-col gap-10 sm:gap-12">
      {/* 로그인한 사용자는 내 플랜을 제목 바로 아래에 둔다(HomeIntro의 memberSlot) */}
      <HomeIntro isMember={isMember} memberSlot={<MyPlanBanner />} />

      <PopularRail servicesQuery={servicesQuery} />
      <WishlistedRail servicesQuery={servicesQuery} />
      <NewArrivalsRail servicesQuery={servicesQuery} />

      {/* 화면 전체의 출처. 문구는 검색 결과 화면과 같다 */}
      <div className="grid gap-1.5 border-t border-border pt-4">
        <SourceAttribution />
        <p className="text-sm text-muted-foreground">
          제공 여부는 확인된 범위이며, 실제 서비스 상황과 다를 수 있습니다. 순위·목록은 OTT가
          발표하는 공식 순위가 아닙니다.
        </p>
      </div>
    </div>
  )
}
