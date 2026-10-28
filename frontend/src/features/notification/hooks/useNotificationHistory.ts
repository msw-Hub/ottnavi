import { useInfiniteQuery, type InfiniteData } from '@tanstack/react-query'
import type {
  CommonResponseNotificationHistoryPage,
  NotificationHistoryItem,
} from '@/api/generated/model'
import {
  getListMyNotificationsQueryKey,
  listMyNotifications,
} from '@/api/generated/notification/notification'
import type { ApiError } from '@/api/http'
import { getMockRequestHeaders } from '@/lib/mockScenario'

// 한 번에 가져오는 이력 수. 첫 화면에 부담 없는 만큼만 보이고 "더 보기"로 이어 붙인다(계약 기본값은 20, 최대 100)
const HISTORY_PAGE_SIZE = 10

// 화면이 쓰는 모양: 지금까지 불러온 모든 페이지를 한 목록으로 이어 붙인 결과
export interface NotificationHistoryData {
  items: NotificationHistoryItem[] // 지금까지 불러온 알림들(최신순, 페이지 순서대로 이어 붙임)
  totalElements: number // 알림 전체 수(마지막으로 불러온 페이지 기준)
}

/**
 * 알림 발송 이력(SCR-12, 계약 listMyNotifications, GET /me/notifications)을 최신순으로 불러온다.
 *
 * 생성 훅 대신 생성 함수를 감싸 useInfiniteQuery를 쓰는 이유: 생성 훅은 한 페이지짜리라 "더 보기"로 이어 붙이기에 맞지 않는다
 * (검색 화면의 useSearchTitles와 같은 방식). 요청 주소·타입·queryKey 모양은 생성물을 그대로 쓴다.
 * - getNextPageParam: 다음 페이지 번호(page + 1)가 전체 페이지 수 안이면 그 번호, 아니면 undefined(= 더 없음).
 * - select: 각 페이지의 CommonResponse 래퍼를 벗기고 한 목록으로 합친다.
 * - queryKey 끝의 목업 헤더 값: 목업 시나리오(?mockScenario=)·페이지 크기를 주소에서 바꾸면 새로 불러와야 해서 넣는다.
 *   운영에서는 getMockRequestHeaders가 항상 빈 객체라 키에 영향이 없다.
 */
export function useNotificationHistory() {
  const mockHeaders = getMockRequestHeaders()

  return useInfiniteQuery<
    CommonResponseNotificationHistoryPage,
    ApiError,
    NotificationHistoryData,
    readonly unknown[],
    number
  >({
    queryKey: [...getListMyNotificationsQueryKey({ size: HISTORY_PAGE_SIZE }), mockHeaders],
    initialPageParam: 0,
    queryFn: ({ pageParam, signal }) =>
      listMyNotifications({ page: pageParam, size: HISTORY_PAGE_SIZE }, undefined, signal),
    getNextPageParam: (lastPage) => {
      const { page, totalPages } = lastPage.data
      return page + 1 < totalPages ? page + 1 : undefined
    },
    select: mergePages,
  })
}

// 불러온 페이지들을 한 목록으로 합친다. 합계는 가장 최근에 불러온 페이지 값을 쓴다
function mergePages(
  data: InfiniteData<CommonResponseNotificationHistoryPage, number>,
): NotificationHistoryData {
  const pages = data.pages.map((page) => page.data)
  return {
    items: pages.flatMap((page) => page.content),
    totalElements: pages[pages.length - 1].totalElements,
  }
}
