import { useSearchParams } from 'react-router'
import { PageTitle } from '@/components/common/PageTitle'
import { SearchScreen } from '@/features/search/components/SearchScreen'

/**
 * SCR-01 검색 화면(`/search`, 공개. 홈 1차안 전에는 `/`였다). 검색어는 주소의 `?q=`로 받는다.
 * 데이터 조회와 화면 조립은 features/search에 있고, 이 파일은 연결만 한다(pages는 얇게, frontend.md).
 */
export function SearchPage() {
  // 검색어는 SearchScreen과 같은 방식(?q=, 앞뒤 공백 제거)으로 읽는다. 제목만 정하는 용도라 화면 쪽 코드는 건드리지 않는다
  const [searchParams] = useSearchParams()
  const query = (searchParams.get('q') ?? '').trim()
  // 검색어는 사용자가 입력한 값이지만 <title>은 텍스트로만 처리돼 HTML로 해석되지 않는다
  const name = query ? `"${query}" 검색 결과` : '작품 검색'
  return (
    <>
      <PageTitle name={name} />
      <SearchScreen />
    </>
  )
}
