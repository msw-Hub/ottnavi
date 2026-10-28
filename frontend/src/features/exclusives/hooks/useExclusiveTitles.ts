import { useInfiniteQuery } from '@tanstack/react-query'
import {
  getListServiceExclusiveTitlesQueryKey,
  listServiceExclusiveTitles,
} from '@/api/generated/catalog/catalog'
import type { CommonResponseExclusiveTitlePage, ExclusiveTitle } from '@/api/generated/model'
import type { ApiError } from '@/api/http'
import { getMockRequestParams } from '@/lib/mockScenario'

/*
 * 독점 목록 화면(SCR-03)의 조회 훅이다(계약 listServiceExclusiveTitles).
 *
 * "더 보기"를 이어 붙이는 목록이라 useInfiniteQuery를 쓴다. 생성 훅(useListServiceExclusiveTitles)은 한 쪽(page)만 받는 훅이라,
 * 다음 쪽을 아래에 이어 붙이려면 쪽마다 따로 훅을 부르는 어색한 구조가 된다. 그래서 생성된 호출 함수(listServiceExclusiveTitles)와
 * 키 만들기(getListServiceExclusiveTitlesQueryKey)만 가져다 무한 조회 훅을 직접 만든다(탐색 화면의 useExploreTitles와 같은 방식).
 */

// 한 번에 받는 작품 수. 가로 줄에서 넓은 화면 기준 네 화면쯤이다(처음엔 6열 격자 4줄로 정한 값. 홈 줄은 훨씬 적게 받아 따로 쓴다)
export const EXCLUSIVE_PAGE_SIZE = 24
// 독점 목록은 하루 단위로 바뀌는 값이라(서버도 24시간 캐시) 탭을 오갈 때마다 다시 받지 않는다
const EXCLUSIVE_STALE_TIME_MS = 60 * 1000

// 지금까지 받은 쪽들을 한 목록으로 합친 모양(select가 만든다)
export interface ExclusiveTitleList {
  items: ExclusiveTitle[] // 지금까지 받은 작품(서버 순서: 최근 6개월 안 공개작 먼저, 같은 묶음 안은 인기도순)
  totalElements: number // 이 서비스 독점으로 확인된 작품 전체 수
  isJudged: boolean // 독점을 판정했는지. false면 데이터 부족 서비스라 화면이 목록 대신 안내를 보인다
  dataAsOf: string // 독점을 계산한 데이터의 기준일(마지막으로 받은 쪽의 값)
}

/**
 * 한 서비스의 독점 작품을 쪽 단위로 받는다. ottServiceId가 null이면 요청을 보내지 않는다(서비스 목록이 오기 전).
 *
 * - 성공 응답의 CommonResponse 래퍼({ success, data })는 select에서 벗긴다(frontend.md).
 * - 서비스마다 queryKey가 달라 탭을 바꾸면 캐시도 새 목록(처음 쪽부터)이 된다. 키 끝의 'infinite'는 같은 주소를 쓰는 한 쪽짜리 조회와
 *   캐시 모양이 섞이지 않게 하는 표시, getMockRequestParams()는 목업 전환 값(운영에서는 빈 객체)이다.
 */
export function useExclusiveTitles(ottServiceId: number | null) {
  const params = { size: EXCLUSIVE_PAGE_SIZE }
  return useInfiniteQuery<
    CommonResponseExclusiveTitlePage,
    ApiError,
    ExclusiveTitleList,
    readonly unknown[],
    number
  >({
    queryKey: [
      ...getListServiceExclusiveTitlesQueryKey(ottServiceId ?? 0, params),
      getMockRequestParams(),
      'infinite',
    ],
    initialPageParam: 0,
    queryFn: ({ pageParam, signal }) =>
      listServiceExclusiveTitles(
        ottServiceId ?? 0,
        { page: pageParam, size: EXCLUSIVE_PAGE_SIZE },
        undefined,
        signal,
      ),
    // 마지막으로 받은 쪽 번호가 전체 쪽 수보다 작으면 다음 쪽이 있다
    getNextPageParam: (lastPage) => {
      const { page, totalPages } = lastPage.data
      return page + 1 < totalPages ? page + 1 : undefined
    },
    select: (data) => {
      const last = data.pages[data.pages.length - 1].data
      return {
        items: data.pages.flatMap((page) => page.data.content),
        totalElements: last.totalElements,
        isJudged: last.isJudged,
        dataAsOf: last.dataAsOf,
      }
    },
    staleTime: EXCLUSIVE_STALE_TIME_MS,
    // gcTime 0: 탭을 떠나면 그 서비스의 받아 둔 쪽들을 바로 버린다. 기본값(5분)이면 "더보기"로 3쪽까지 받아 둔 서비스로 돌아왔을 때
    // 이어 붙인 긴 목록이 그대로 보이는데, 이 화면은 탭을 바꾸면 항상 첫 쪽부터 다시 시작하기로 했다(24편이라 다시 받아도 가볍다)
    gcTime: 0,
    enabled: ottServiceId !== null,
  })
}
