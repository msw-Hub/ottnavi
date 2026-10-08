import { useSearchParams } from 'react-router'

/**
 * SCR-01 검색 화면(`/`, 공개). 검색어는 주소의 `?q=`로 받는다.
 * 결과 목록은 Task 027(features/search)에서 채운다. 지금은 헤더 검색창 → 주소 이동이 이어지는지만 보이게 검색어를 표시한다.
 */
export function SearchPage() {
  const [searchParams] = useSearchParams()
  const query = searchParams.get('q')

  return (
    <section className="flex flex-col gap-3">
      <h1 className="text-2xl font-semibold">작품 검색</h1>
      <p className="text-muted-foreground">
        {query ? `검색어: ${query}` : '찜할 작품을 제목으로 검색해 보세요.'}
      </p>
    </section>
  )
}
