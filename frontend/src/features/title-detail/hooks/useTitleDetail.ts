import { useGetTitleDetail } from '@/api/generated/catalog/catalog'
import type { MediaType } from '@/api/generated/model'
import { getMockRequestHeaders } from '@/lib/mockScenario'

// 상세를 조회할 작품. 주소(/titles/:mediaType/:tmdbId)에서 읽어 검증을 마친 값이다
export interface TitleDetailParams {
  mediaType: MediaType // 영화 / 드라마(tv)
  tmdbId: number // TMDB ID
}

/**
 * 작품 상세를 불러온다(SCR-02, 계약 getTitleDetail). 생성 훅(useGetTitleDetail)을 감싼다.
 *
 * - params가 null이면(주소 값이 올바르지 않음) 요청을 보내지 않는다(enabled: false). 훅은 조건문 안에서 부를 수 없어
 *   "호출은 항상 하되 요청만 막는" 방식을 썼다. 이때 생성 훅에 넘길 값이 없어 자리 값('movie', 0)을 쓰지만 요청은 나가지 않는다.
 * - select: 성공 응답의 CommonResponse 래퍼를 벗긴다(frontend.md).
 * - queryKey 끝의 목업 헤더 값: 목업 시나리오(?mockScenario=error 등)를 주소에서 바꾸면 같은 작품이라도 새로 불러오도록 넣는다.
 *   이 값은 요청에는 헤더(X-Mock-*)로 실린다. 운영에서는 빈 객체라 키에 영향이 없다.
 * - 404 TITLE_NOT_FOUND는 다시 보내도 결과가 같아 재시도하지 않는다(app/providers.tsx의 재시도 정책이 4xx를 제외한다).
 */
export function useTitleDetail(params: TitleDetailParams | null) {
  const mockHeaders = getMockRequestHeaders()

  return useGetTitleDetail(params?.mediaType ?? 'movie', params?.tmdbId ?? 0, {
    query: {
      queryKey: [`/public/titles/${params?.mediaType}/${params?.tmdbId}`, mockHeaders],
      select: (res) => res.data,
      enabled: params !== null,
    },
  })
}
