/*
 * 서비스별 독점 목록(SCR-03, Task 075) 1차안이 쓰는 임시 타입이다(frontend.md "목업 단계의 임시 타입 예외").
 *
 * 왜 임시인가: 독점 목록 API는 아직 계약(docs/api/openapi.yaml)에 없다(계약 추가는 Task 078). 1차안은 화면 모양을 먼저 정해
 * 계약 초안의 근거를 만드는 단계라, 화면이 실제로 쓰는 값의 모양만 여기 적고 정적 샘플(sampleData.ts)로 채운다.
 * 계약이 정해지면 이 파일을 지우고 생성 타입으로 바꾼다(그때 찾기 쉽게 이 파일 하나에만 둔다).
 * 이미 계약에 있는 값(작품 구분 MediaType, 서비스 OttService)은 처음부터 생성 타입을 쓴다.
 */
import type { MediaType, OttService } from '@/api/generated/model'

// 임시: 계약 반영 후 생성 타입으로 교체
// 탭에 쓰는 서비스 정보. 서비스 목록 API(listOttServices)의 필드 일부만 쓴다(로고는 1차안에서 쓰지 않는다)
export type ExclusiveService = Pick<OttService, 'ottServiceId' | 'code' | 'name' | 'dataQuality'>

// 임시: 계약 반영 후 생성 타입으로 교체
// 독점 목록의 작품 한 개. "확인된 범위에서 7곳 중 이 서비스에서만 구독으로 볼 수 있는 작품"이다(PRD FR-04)
export interface ExclusiveTitle {
  mediaType: MediaType // 영화(movie) / 드라마(tv). 상세 주소와 카드의 "영화/드라마" 글자에 쓴다
  tmdbId: number // 상세 주소에 쓰는 TMDB ID
  title: string // 한국어 제목
  releaseYear: number | null // 개봉·첫 방영 연도(모르면 null)
  posterUrl: string | null // 완성된 포스터 주소. 1차안은 외부 요청 없이 data URI만 쓴다. 없으면 null → "포스터 없음"
  // 드라마에서 이 서비스 독점으로 확인된 시즌 번호(오름차순). 영화는 null이다.
  // 독점 판정은 시즌(시청 단위) 단위라 같은 드라마라도 일부 시즌만 독점일 수 있다
  exclusiveSeasonNumbers: number[] | null
  comparedServiceCount: number // 비교한 다른 서비스 수. 보통 6곳이고, 영화는 쿠팡플레이(구조적 공백)를 빼 5곳이다
  unknownElsewhereCount: number // 비교한 다른 서비스 중 제공 여부가 "모름"인 곳의 수. 0보다 크면 다른 곳에도 있을 수 있다
}

// 임시: 계약 반영 후 생성 타입으로 교체
// 한 서비스의 독점 목록 응답(페이지 형식: api-contract.md의 고정 5개 필드 + 부가 필드)
export interface ExclusiveTitlePage {
  content: ExclusiveTitle[] // 이 페이지의 작품(인기순)
  page: number // 0부터
  size: number
  totalElements: number // 이 서비스 독점으로 확인된 작품 전체 수
  totalPages: number
  dataAsOf: string // 독점을 계산한 데이터의 기준일('YYYY-MM-DD'). 수집이 끝난 뒤 다시 계산한 날이다
}
