import { useQuery } from '@tanstack/react-query'
import { customInstance, type ApiError } from '@/api/http'
import type {
  CommonResponseTitleDetail,
  MediaType,
  TitleDetail,
} from '@/features/title-detail/mockTypes'
import { getMockScenarioParams } from '@/lib/mockScenario'

// 상세를 조회할 작품. 주소(/titles/:mediaType/:tmdbId)에서 읽어 검증을 마친 값이다
export interface TitleDetailParams {
  mediaType: MediaType // 영화 / 드라마(tv)
  tmdbId: number // TMDB ID
}

/**
 * 작품 상세를 불러온다(SCR-02).
 *
 * 임시 구현: 이 API(GET /public/titles/{mediaType}/{tmdbId})는 아직 계약에 없어 orval 생성 훅이 없다.
 * 생성 훅이 하는 일(customInstance 호출 + queryKey)을 같은 모양으로 직접 적었고, Task 031에서 생성 훅을 감싸는 형태로 바뀐다.
 *
 * - params가 null이면(주소 값이 올바르지 않음) 요청을 보내지 않는다(enabled: false). 훅은 조건문 안에서 부를 수 없어
 *   "호출은 항상 하되 요청만 막는" 방식을 썼다.
 * - select: 성공 응답의 CommonResponse 래퍼를 벗긴다(frontend.md).
 * - 404 TITLE_NOT_FOUND는 다시 보내도 결과가 같아 재시도하지 않는다(app/providers.tsx의 재시도 정책이 4xx를 제외한다).
 */
export function useTitleDetail(params: TitleDetailParams | null) {
  const scenarioParams = getMockScenarioParams()

  return useQuery<CommonResponseTitleDetail, ApiError, TitleDetail>({
    queryKey: ['/public/titles', params?.mediaType, params?.tmdbId, scenarioParams],
    queryFn: ({ signal }) =>
      customInstance<CommonResponseTitleDetail>({
        // enabled가 false일 때는 이 함수가 실행되지 않는다. params!는 그 경우를 타입에서 걸러 내려는 것이다
        url: `/public/titles/${params!.mediaType}/${params!.tmdbId}`,
        method: 'GET',
        params: scenarioParams,
        signal,
      }),
    select: (res) => res.data,
    enabled: params !== null,
  })
}
