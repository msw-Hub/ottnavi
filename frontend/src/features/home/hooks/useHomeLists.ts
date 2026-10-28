import { useInfiniteQuery } from '@tanstack/react-query'
import {
  getListGenresQueryKey,
  getListGenreTitlesQueryKey,
  getListNewlyAvailableTitlesQueryKey,
  getListPopularityRankingsQueryKey,
  getListServiceExclusiveTitlesQueryKey,
  getListWishlistedTitlesQueryKey,
  getSearchTitlesQueryKey,
  listGenreTitles,
  searchTitles,
  useListGenres,
  useListNewlyAvailableTitles,
  useListPopularityRankings,
  useListServiceExclusiveTitles,
  useListWishlistedTitles,
} from '@/api/generated/catalog/catalog'
import type {
  CommonResponseTitleExplorePage,
  CommonResponseTitleSearchPage,
  PopularityPeriod,
  TitleSearchItem,
} from '@/api/generated/model'
import type { ApiError } from '@/api/http'
import { getMockRequestParams } from '@/lib/mockScenario'

/*
 * 홈·랭킹·탐색 화면이 쓰는 조회 훅 모음이다(계약 listPopularityRankings 등 홈·탐색 공개 조회 6개 중 5개).
 *
 * 공통 규칙:
 * - 성공 응답의 CommonResponse 래퍼({ success, data })는 select로 벗긴다(frontend.md).
 * - queryKey 끝에 목업 전환 값(getMockRequestParams)을 붙인다: 주소의 ?mockScenario=가 바뀌면 같은 API라도 새로 받아야 하기 때문이다.
 *   운영에서는 항상 빈 객체라 키에 영향이 없다.
 * - staleTime 60초: 서버가 이 목록들을 캐시하고(브라우저 캐시 max-age=60) 하루 단위로 바뀌는 값이라, 탭을 오갈 때마다 다시 받지 않는다.
 * - 인물별 작품(listPersonTitles)은 지금 화면에 자리가 없어 연결하지 않았다(생성 훅 useListPersonTitles가 이미 있다).
 */

// 홈 목록을 다시 받지 않고 쓰는 시간(60초)
const HOME_LIST_STALE_TIME_MS = 60 * 1000

// 홈 인기 순위 줄에 보일 개수(TOP 10). 홈 맨 위 배너가 같은 요청(오늘·전체·이 개수)을 써서 요청 한 번을 나눠 쓴다.
// 줄과 배너가 이 값을 따로 쓰면 요청 조건이 달라져 같은 목록을 두 번 받는다
export const POPULAR_RANKING_LIMIT = 10
// 장르 목록은 하루에 한 번 바뀔까 말까 해서 한 시간 동안 다시 받지 않는다(서버 캐시 24시간)
const GENRES_STALE_TIME_MS = 60 * 60 * 1000

/** 탐색 결과 한 번에 받는 작품 수. 5열 바둑판 4줄이다 */
export const EXPLORE_PAGE_SIZE = 20
// 새로 볼 수 있게 된 작품을 탐색에서 한 번에 받는 최대 개수(계약 최대 50)
const EXPLORE_NEW_LIMIT = 50

/**
 * 서비스(또는 전체)의 인기 순위를 받는다. ottServiceId가 null이면 "전체" 순위다(요청에서 생략).
 * 응답에는 순위 줄({ rank, title })과 기준 안내 문구(notice), 집계한 날 수(daysCounted), 기준일(snapshotDate)이 함께 온다.
 */
export function usePopularityRanking(
  period: PopularityPeriod,
  ottServiceId: number | null,
  limit: number,
  isEnabled = true, // false면 요청을 보내지 않는다(탐색 화면이 기본 목록을 안 쓸 때)
) {
  const params = { period, limit, ...(ottServiceId !== null ? { ottServiceId } : {}) }
  return useListPopularityRankings(params, {
    query: {
      queryKey: [...getListPopularityRankingsQueryKey(params), getMockRequestParams()],
      select: (res) => res.data,
      staleTime: HOME_LIST_STALE_TIME_MS,
      enabled: isEnabled,
    },
  })
}

/** 찜이 많은 작품을 받는다. 최소 인원(minUserCount) 미만이면 빈 목록이 온다 → 화면은 줄을 숨긴다 */
export function useWishlistedTitles(limit: number) {
  const params = { limit }
  return useListWishlistedTitles(params, {
    query: {
      queryKey: [...getListWishlistedTitlesQueryKey(params), getMockRequestParams()],
      select: (res) => res.data,
      staleTime: HOME_LIST_STALE_TIME_MS,
    },
  })
}

/**
 * 새로 볼 수 있게 된 작품을 받는다. ottServiceId가 null이면 7곳 전체다.
 * isEnabled가 false면 요청을 보내지 않는다(탐색 화면이 이 목록을 안 쓸 때. 훅은 조건문 안에서 못 부르므로 "호출은 항상, 요청만 막기" 방식이다).
 */
export function useNewlyAvailableTitles(
  ottServiceId: number | null,
  limit: number,
  isEnabled = true,
) {
  const params = { limit, ...(ottServiceId !== null ? { ottServiceId } : {}) }
  return useListNewlyAvailableTitles(params, {
    query: {
      queryKey: [...getListNewlyAvailableTitlesQueryKey(params), getMockRequestParams()],
      select: (res) => res.data,
      staleTime: HOME_LIST_STALE_TIME_MS,
      enabled: isEnabled,
    },
  })
}

/**
 * 한 서비스의 독점 작품 첫 쪽만 받는다(홈의 "서비스 독점 작품" 줄용, 계약 listServiceExclusiveTitles).
 * ottServiceId가 null이면 요청을 보내지 않는다(서비스 목록이 오기 전). 독점 화면(features/exclusives)은 "더 보기"로 이어 붙이는
 * 다른 훅을 쓴다. 기능 폴더끼리 import하지 않는 규칙 때문에 홈은 홈 전용 훅을 따로 둔다.
 */
export function useServiceExclusiveTitles(ottServiceId: number | null, size: number) {
  const params = { size }
  return useListServiceExclusiveTitles(ottServiceId ?? 0, params, {
    query: {
      queryKey: [
        ...getListServiceExclusiveTitlesQueryKey(ottServiceId ?? 0, params),
        getMockRequestParams(),
      ],
      select: (res) => res.data,
      staleTime: HOME_LIST_STALE_TIME_MS,
      enabled: ottServiceId !== null,
    },
  })
}

/** 탐색 화면의 장르 칩 목록(TMDB 장르, 이름 가나다순). 서버가 이미 정렬해서 준다 */
export function useGenres() {
  return useListGenres({
    query: {
      queryKey: [...getListGenresQueryKey(), getMockRequestParams()],
      select: (res) => res.data,
      staleTime: GENRES_STALE_TIME_MS,
    },
  })
}

// 탐색 화면이 고른 조건. 검색어가 있으면 검색, 아니면 장르, 아니면 "새로 볼 수 있게 된 작품만", 아무것도 없으면 인기 순위 전체다
export interface ExploreSource {
  query: string // 제목 검색어(빈 문자열이면 검색 안 함)
  genreId: number | null // TMDB 장르 ID
  isNewOnly: boolean // 새로 볼 수 있게 된 작품만
}

// 화면이 쓰는 탐색 결과 모양
export interface ExploreResult {
  items: TitleSearchItem[] // 지금까지 받은 작품들(작품 구분 칩은 화면이 따로 거른다)
  dataAsOf: string | null // 제공 상태 데이터 기준일(모르면 null)
  hasNextPage: boolean // "더보기"로 이어 받을 페이지가 더 있는지
  isFetchingNextPage: boolean
  fetchNextPage: () => void
  isPending: boolean // 처음 받는 중
  isError: boolean
  error: unknown
  refetch: () => void
}

// 두 페이지 응답(검색, 장르별)이 공통으로 가진 모양
type PagedResponse = CommonResponseTitleSearchPage | CommonResponseTitleExplorePage

// 페이지들을 합친 결과(select가 만든다)
interface PagedData {
  items: TitleSearchItem[]
  dataAsOf: string
}

/**
 * 탐색 결과 목록을 받는다. 어떤 API를 부를지는 고른 조건으로 정한다(우선순위: 검색어 → 장르 → 새 작품 → 인기 순위).
 *
 * 왜 한 훅에 모았나: 화면은 "작품 카드 목록 + 더보기"만 알면 되고, 어디서 받았는지는 몰라도 된다.
 * - 검색어, 장르: 페이지가 있는 API라 useInfiniteQuery로 "더보기"를 이어 붙인다. 두 API는 응답 모양(content·page·totalPages)이 같다.
 * - 새 작품, 인기 순위: 페이지 없이 한 번에 받는다(최대 50개, 20개).
 * 쓰지 않는 쿼리는 enabled: false로 요청을 막는다(조건부 훅 호출은 React 규칙 위반이라 항상 부르고 요청만 막는다).
 * 새 작품 목록은 같은 작품이 서비스마다 여러 줄로 올 수 있어 작품 하나로 합친다(첫 줄만 쓴다).
 */
export function useExploreTitles({ query, genreId, isNewOnly }: ExploreSource): ExploreResult {
  const mockParams = getMockRequestParams()
  const isSearch = query.length > 0
  const isGenre = !isSearch && genreId !== null
  const isNew = !isSearch && !isGenre && isNewOnly
  const isDefault = !isSearch && !isGenre && !isNew

  // 1. 페이지가 있는 두 API(검색, 장르별)
  const paged = useInfiniteQuery<PagedResponse, ApiError, PagedData, readonly unknown[], number>({
    queryKey: isSearch
      ? [...getSearchTitlesQueryKey({ q: query, size: EXPLORE_PAGE_SIZE }), mockParams, 'explore']
      : [
          ...getListGenreTitlesQueryKey(genreId ?? 0, { size: EXPLORE_PAGE_SIZE }),
          mockParams,
          'explore',
        ],
    initialPageParam: 0,
    queryFn: ({ pageParam, signal }) =>
      isSearch
        ? searchTitles({ q: query, page: pageParam, size: EXPLORE_PAGE_SIZE }, undefined, signal)
        : listGenreTitles(
            genreId ?? 0,
            { page: pageParam, size: EXPLORE_PAGE_SIZE },
            undefined,
            signal,
          ),
    getNextPageParam: (lastPage) => {
      const { page, totalPages } = lastPage.data
      return page + 1 < totalPages ? page + 1 : undefined
    },
    // 지금까지 받은 페이지를 한 목록으로 합친다(래퍼 벗기기 포함). 기준일은 마지막 페이지 값을 쓴다
    select: (data) => ({
      items: data.pages.flatMap((page) => page.data.content),
      dataAsOf: data.pages[data.pages.length - 1].data.dataAsOf,
    }),
    staleTime: HOME_LIST_STALE_TIME_MS,
    enabled: isSearch || isGenre,
  })

  // 2. 페이지 없는 두 API
  const newly = useNewlyAvailableTitles(null, EXPLORE_NEW_LIMIT, isNew)
  const popular = usePopularityRanking('TODAY', null, EXPLORE_PAGE_SIZE, isDefault)

  // 3. 쓰는 쪽 하나를 골라 같은 모양으로 돌려준다
  if (isNew) {
    return {
      items: dedupeTitles((newly.data?.items ?? []).map((entry) => entry.title)),
      dataAsOf: newly.data?.dataAsOf ?? null,
      hasNextPage: false,
      isFetchingNextPage: false,
      fetchNextPage: () => undefined,
      isPending: newly.isPending,
      isError: newly.isError,
      error: newly.error,
      refetch: () => void newly.refetch(),
    }
  }
  if (isDefault) {
    return {
      items: (popular.data?.items ?? []).map((entry) => entry.title),
      dataAsOf: popular.data?.snapshotDate ?? null,
      hasNextPage: false,
      isFetchingNextPage: false,
      fetchNextPage: () => undefined,
      isPending: popular.isPending,
      isError: popular.isError,
      error: popular.error,
      refetch: () => void popular.refetch(),
    }
  }
  return {
    items: paged.data?.items ?? [],
    dataAsOf: paged.data?.dataAsOf ?? null,
    hasNextPage: paged.hasNextPage,
    isFetchingNextPage: paged.isFetchingNextPage,
    fetchNextPage: () => void paged.fetchNextPage(),
    isPending: paged.isPending,
    isError: paged.isError,
    error: paged.error,
    refetch: () => void paged.refetch(),
  }
}

// 같은 작품(작품 구분 + TMDB ID)을 한 번만 남긴다(처음 나온 것을 둔다)
function dedupeTitles(titles: TitleSearchItem[]): TitleSearchItem[] {
  const seen = new Set<string>()
  return titles.filter((title) => {
    const key = `${title.mediaType}-${title.tmdbId}`
    if (seen.has(key)) return false
    seen.add(key)
    return true
  })
}
