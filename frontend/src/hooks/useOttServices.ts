import { useListOttServices } from '@/api/generated/product/product'

// 서비스 목록은 거의 바뀌지 않아(서비스 7곳 고정) 5분 동안은 다시 불러오지 않는다
const SERVICES_STALE_TIME_MS = 5 * 60 * 1000

/**
 * 국내 OTT 7개 서비스 목록(서비스 목록 API, 계약 listOttServices)을 불러온다.
 *
 * features/search와 features/title-detail이 함께 쓰므로 기능 폴더가 아니라 src/hooks에 뒀다
 * (기능 폴더끼리는 서로 import하지 않는다, frontend.md). 성공 응답의 CommonResponse 래퍼는 select로 벗긴다.
 * 두 화면이 같은 queryKey(생성 훅의 기본 키)를 쓰므로 TanStack Query 캐시를 공유해 요청은 한 번만 나간다.
 *
 * isEnabled가 false면 요청을 보내지 않는다. 상세 화면이 주소 오류일 때처럼 서비스 목록이 필요 없는 경우에 쓴다
 * (훅은 조건문 안에서 부를 수 없어 "호출은 항상 하되 요청만 막는" 방식이다).
 */
export function useOttServices(isEnabled = true) {
  return useListOttServices({
    query: { select: (res) => res.data, staleTime: SERVICES_STALE_TIME_MS, enabled: isEnabled },
  })
}
