/**
 * "인기 순위 카드에서 작품 상세로 들어왔다"는 사실을 라우터 이동 state로 넘기는 값과 그 검사 함수다.
 *
 * 왜 필요한가: 인기 순위에는 우리 DB에 아직 없는 작품도 나온다. 그런 카드를 누르면 상세 API가 404를 돌려주는데,
 * 검색·주소 직접 입력으로 만난 404("없는 작품")와 달리 이 경우는 "곧 반영될 작품"이라 다른 안내가 필요하다.
 * 그래서 카드(features/home)가 Link의 state에 이 값을 싣고, 상세 화면(features/title-detail)이 읽는다.
 *
 * 왜 lib에 두는가: 두 기능 폴더가 서로 import하면 안 되므로(frontend.md) 둘이 함께 쓰는 약속은 공용 위치에 둔다.
 * 왜 주소 파라미터가 아니라 state인가: 공유 가능한 주소에 "인기에서 왔다"는 일시적 맥락을 남기지 않기 위해서다.
 * 주의: state는 브라우저 history에 저장돼 새로고침해도 남는다. 그래서 상세 화면(usePopularityEntryState)이 읽은 즉시 비운다.
 */

// 라우터 state로 넘기는 모양. title·posterPath는 카드에 이미 있는 값만 쓴다(새 API 호출 없음)
export interface PopularityNavState {
  fromPopularity: true // 인기 순위 카드에서 왔다는 표시
  title: string // 카드의 작품 제목
  posterPath: string | null // 카드의 포스터 상대 경로(없으면 null)
}

// 인기 순위 카드가 Link에 실을 state를 만든다
export function createPopularityNavState(
  title: string,
  posterPath: string | null,
): PopularityNavState {
  return { fromPopularity: true, title, posterPath }
}

// location.state는 타입이 없는 값(unknown)이라 믿지 않고 모양을 확인한다. 맞지 않으면 null이라 기존 일반 처리가 된다
export function readPopularityNavState(state: unknown): PopularityNavState | null {
  if (typeof state !== 'object' || state === null) return null
  const candidate = state as Record<string, unknown>
  if (candidate.fromPopularity !== true || typeof candidate.title !== 'string') return null
  const posterPath = typeof candidate.posterPath === 'string' ? candidate.posterPath : null
  return { fromPopularity: true, title: candidate.title, posterPath }
}
