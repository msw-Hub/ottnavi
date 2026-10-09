import { SearchScreen } from '@/features/search/components/SearchScreen'

/**
 * SCR-01 검색 화면(`/`, 공개). 검색어는 주소의 `?q=`로 받는다.
 * 데이터 조회와 화면 조립은 features/search에 있고, 이 파일은 연결만 한다(pages는 얇게, frontend.md).
 */
export function SearchPage() {
  return <SearchScreen />
}
