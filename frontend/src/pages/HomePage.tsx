import { PageTitle } from '@/components/common/PageTitle'
import { HomeScreen } from '@/features/home/components/HomeScreen'

/**
 * 홈(`/`, 공개, 홈 1차안). 가치 제안·시작 안내·큰 검색창·공통 목록(인기 순위, 찜 많은 작품, 새로 볼 수 있게 된 작품, 독점)을 보인다.
 * 화면 조립은 features/home에 있고, 이 파일은 연결만 한다(pages는 얇게, frontend.md).
 */
export function HomePage() {
  return (
    <>
      <PageTitle />
      <HomeScreen />
    </>
  )
}
