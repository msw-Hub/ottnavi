import { useInfiniteQuery, type InfiniteData } from '@tanstack/react-query'
import { customInstance, type ApiError } from '@/api/http'
import { getMockPageSize, getMockScenarioParams } from '@/lib/mockScenario'
import type {
  CommonResponseTitleSearchResult,
  TitleSearchItem,
  TitleSearchResult,
} from '@/features/search/mockTypes'

// 한 번에 가져오는 작품 수의 기본값. 5열 그리드 2행(10개)을 먼저 보이고 "더보기"로 이어 붙인다(사용자 결정 2026-10-09).
// 계약 기본값은 20(api-contract.md)이지만 size를 명시해 보내므로 어긋나지 않는다(최대 100). 이전 값: 20
const DEFAULT_PAGE_SIZE = 10

// 화면이 쓰는 모양: 지금까지 불러온 모든 페이지를 한 목록으로 이어 붙인 결과
export interface SearchTitlesData {
  items: TitleSearchItem[] // 지금까지 불러온 작품들(페이지 순서대로 이어 붙임)
  totalElements: number // 검색된 작품 전체 수(마지막으로 불러온 페이지 기준)
  isExternalCollectFailed: boolean // 외부 수집 실패 여부(마지막으로 불러온 페이지 기준)
  dataAsOf: string // 데이터 기준일
}

/**
 * 제목으로 작품을 검색한다(SCR-01). 검색어는 URL의 ?q=에서 받아 이 훅에 넘기고, "더보기"는 fetchNextPage로 이어 붙인다.
 *
 * 임시 구현: 이 API(GET /public/titles)는 아직 계약에 없어 orval 생성 훅이 없다. 그래서 생성 훅이 하는 일
 * (customInstance 호출 + queryKey)을 같은 모양으로 직접 적었다. Task 031에서 계약에 operation이 생기면 이 파일은
 * 생성 훅을 감싸는 형태로 바뀐다.
 *
 * - useInfiniteQuery: "더보기"로 페이지를 이어 붙이는 화면에 맞는 TanStack Query 기능이다. 검색어(키)가 바뀌면
 *   다른 쿼리가 되어 처음 페이지부터 다시 시작한다.
 * - getNextPageParam: 다음 페이지 번호(page + 1)가 전체 페이지 수 안이면 그 번호를, 아니면 undefined(= 더 없음)를 준다.
 * - size: 기본 10. 목업 모드에서만 주소의 ?mockPageSize=로 줄일 수 있다(getMockPageSize 주석). queryKey에도 넣어
 *   size가 달라지면 새로 불러오게 한다.
 * - enabled: 검색어가 비어 있으면 요청을 보내지 않는다(검색 홈 화면에서는 안내만 보인다).
 * - select: 각 페이지의 CommonResponse 래퍼({ success, data })를 벗기고 한 목록으로 합친다(frontend.md).
 * - queryKey에 mockScenario를 넣는 이유: 목업 시나리오(예: 수집 실패)를 주소에서 바꾸면 같은 검색어라도 새로 불러와야 하기 때문이다.
 *   운영에서는 항상 빈 객체라 키에 영향이 없다(getMockScenarioParams 주석).
 */
export function useSearchTitles(query: string) {
  const scenarioParams = getMockScenarioParams()
  const size = getMockPageSize() ?? DEFAULT_PAGE_SIZE

  return useInfiniteQuery<
    CommonResponseTitleSearchResult,
    ApiError,
    SearchTitlesData,
    readonly unknown[],
    number
  >({
    queryKey: ['/public/titles', { q: query, size, ...scenarioParams }],
    initialPageParam: 0,
    queryFn: ({ pageParam, signal }) =>
      customInstance<CommonResponseTitleSearchResult>({
        url: '/public/titles',
        method: 'GET',
        params: { q: query, page: pageParam, size, ...scenarioParams },
        signal,
      }),
    getNextPageParam: (lastPage) => {
      const { page, totalPages } = lastPage.data
      return page + 1 < totalPages ? page + 1 : undefined
    },
    select: mergePages,
    enabled: query.length > 0,
  })
}

// 불러온 페이지들을 한 목록으로 합친다. 합계·수집 실패 여부·기준일은 가장 최근에 불러온 페이지 값을 쓴다
function mergePages(data: InfiniteData<CommonResponseTitleSearchResult, number>): SearchTitlesData {
  const results: TitleSearchResult[] = data.pages.map((page) => page.data)
  const last = results[results.length - 1]
  return {
    items: results.flatMap((result) => result.content),
    totalElements: last.totalElements,
    isExternalCollectFailed: last.isExternalCollectFailed,
    dataAsOf: last.dataAsOf,
  }
}
