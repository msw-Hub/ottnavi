import { PageTitle } from '@/components/common/PageTitle'
import { ExploreScreen } from '@/features/home/components/ExploreScreen'

/** 탐색(`/explore`, 공개, 홈 1차안). 이름 검색·장르 칩·결과 바둑판. 고른 값은 ?q=, ?type=, ?genre=, ?view=로 받는다. */
export function ExplorePage() {
  return (
    <>
      <PageTitle name="탐색" />
      <ExploreScreen />
    </>
  )
}
