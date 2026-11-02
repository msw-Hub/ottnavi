// 작품 제목 검색어 규칙. 검색 화면(features/search)과 탐색 화면(features/home)이 함께 쓴다(기능 폴더끼리는 서로 import하지 않아 lib에 둔다).

// 검색어 최소 글자 수(앞뒤 공백 제외). 계약(openapi.yaml 검색어 q의 minLength)·백엔드 검증(TitleSearchService)과 같은 값이다.
// 1글자 검색은 결과가 지나치게 많고 검색 비용도 커서 서버가 400으로 거절하므로, 화면에서 먼저 안내하고 요청을 보내지 않는다.
export const MIN_SEARCH_QUERY_LENGTH = 2

// 사용자에게 보여 주는 안내 문구(서버가 거절할 때의 문구와 같게 맞춘다)
export const SEARCH_QUERY_TOO_SHORT_MESSAGE = '검색어는 2글자 이상 입력해 주세요.'

/**
 * 앞뒤 공백을 뺀 검색어가 비어 있지 않은데 최소 글자 수보다 짧은지 알려 준다.
 * 예: "" → false(검색어 없음), "가" → true, "가나" → false, "😀" → true(이모지 하나는 1글자).
 *
 * 글자 수를 `query.length`로 세지 않는 이유: 자바스크립트의 length는 UTF-16 단위라 이모지 같은 문자를 2로 센다.
 * `Array.from`은 문자 하나를 하나로 세어 백엔드(코드 포인트 기준)와 같은 결과가 나온다.
 */
export function isSearchQueryTooShort(query: string): boolean {
  const trimmed = query.trim()
  if (trimmed === '') {
    return false
  }
  return Array.from(trimmed).length < MIN_SEARCH_QUERY_LENGTH
}
