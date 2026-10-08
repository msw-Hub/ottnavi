/*
 * TMDB 이미지 URL을 만드는 함수다.
 *
 * 백엔드는 posterPath·logoPath 같은 이미지 값을 TMDB 상대 경로('/abc.jpg') 그대로 내려주고,
 * URL 조립은 프론트가 한다(api-contract.md 데이터 형식). 화면마다 문자열을 이어 붙이면 기본 URL·크기 표기가 흩어지므로
 * 이 함수 하나로 모은다.
 *
 * 외부(TMDB) 이미지를 실제로 불러올지, 목업에서는 막을지는 Task 027에서 정한다. 여기서는 URL만 만든다.
 */

// TMDB 이미지 기본 URL.
// 출처: TMDB API GET /3/configuration 응답의 images.secure_base_url (developer.themoviedb.org, Configuration Details 문서 예시).
// TMDB는 이 값을 설정 API로 받아 쓰라고 안내하지만, 값이 거의 바뀌지 않아 요청 한 번을 아끼려고 상수로 둔다.
// 바뀌었다는 안내가 있으면 이 상수만 고치면 된다. https(secure_base_url)를 쓰는 이유는 http 이미지가
// https 페이지에서 혼합 콘텐츠로 차단되기 때문이다.
export const TMDB_IMAGE_BASE_URL = 'https://image.tmdb.org/t/p/'

/*
 * 이미지 종류별로 TMDB가 제공하는 크기다(같은 configuration 응답의 *_sizes 값).
 * 목록에 없는 크기(예: 'w200')로 요청하면 TMDB가 이미지를 주지 않으므로, 타입으로 막아 오타를 컴파일 단계에서 잡는다.
 * - w숫자: 가로 픽셀, h632: 세로 픽셀, original: 원본(용량이 커서 목록 화면에는 쓰지 않는다)
 */
export type TmdbPosterSize = 'w92' | 'w154' | 'w185' | 'w342' | 'w500' | 'w780' | 'original' // poster_sizes
export type TmdbLogoSize = 'w45' | 'w92' | 'w154' | 'w185' | 'w300' | 'w500' | 'original' // logo_sizes
export type TmdbProfileSize = 'w45' | 'w185' | 'h632' | 'original' // profile_sizes (출연진 사진)
export type TmdbBackdropSize = 'w300' | 'w780' | 'w1280' | 'original' // backdrop_sizes (배경 이미지)
export type TmdbStillSize = 'w92' | 'w185' | 'w300' | 'original' // still_sizes (회차 장면)

export type TmdbImageSize =
  TmdbPosterSize | TmdbLogoSize | TmdbProfileSize | TmdbBackdropSize | TmdbStillSize

/**
 * TMDB 상대 경로와 크기로 이미지 URL을 만든다. 경로가 없거나 올바르지 않으면 null을 돌려준다.
 *
 * null을 돌려주는 경우와 이유:
 * - null·undefined·빈 문자열: TMDB에 이미지가 없는 작품이 실제로 있다. 호출부(PosterCard 등)가 null을 보고 대체 이미지를 그린다.
 * - '/'로 시작하지 않거나 '//'로 시작하는 값: TMDB 상대 경로 형식이 아니다. 특히 'https://...'나 '//다른주소/...' 같은 값을
 *   그대로 이어 붙이면 의도하지 않은 외부 주소로 요청이 나갈 수 있어 받지 않는다.
 *
 * 예: getTmdbImageUrl('/abc.jpg', 'w342') → 'https://image.tmdb.org/t/p/w342/abc.jpg'
 */
export function getTmdbImageUrl(
  path: string | null | undefined,
  size: TmdbImageSize,
): string | null {
  if (path == null) return null
  const trimmed = path.trim()
  if (!trimmed.startsWith('/') || trimmed.startsWith('//')) return null
  // 기본 URL이 '/'로 끝나고 경로가 '/'로 시작하므로 크기 뒤에 경로를 그대로 붙이면 '/w342/abc.jpg'가 된다
  return `${TMDB_IMAGE_BASE_URL}${size}${trimmed}`
}
