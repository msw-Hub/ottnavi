import { useEffect, useState } from 'react'
import { useLocation, useNavigate } from 'react-router'
import { readPopularityNavState, type PopularityNavState } from '@/lib/popularityNavState'

/**
 * 인기 순위 카드에서 이 상세로 들어왔는지(라우터 state)를 읽어 돌려준다. 아니면 null이다.
 *
 * 왜 state를 지우는가: 라우터 state는 브라우저 history에 저장돼 새로고침해도 남는다. 그대로 두면 새로고침·주소 재입력 뒤에도
 * "인기에서 왔다"로 읽혀 요구("새로고침은 일반 처리")와 어긋난다. 그래서 읽은 즉시 history 항목의 state를 비운다(replace, 기록이 쌓이지 않는다).
 * 비우면 location.state가 사라지므로, 이번 방문 동안 쓸 값은 컴포넌트 안(useState)에 따로 쥔다.
 * 쥔 값은 경로(pathname)와 함께 저장해, 같은 화면 컴포넌트가 다른 작품 주소로 바뀌었을 때 이전 작품의 안내가 새어 나오지 않게 한다.
 */
export function usePopularityEntryState(): PopularityNavState | null {
  const location = useLocation()
  const navigate = useNavigate()
  const fresh = readPopularityNavState(location.state)
  const [held, setHeld] = useState<{ path: string; state: PopularityNavState } | null>(null)

  // 1. 새로 실린 state가 있으면 이 경로의 값으로 쥔다(렌더 중 상태 보정: useEffect로 하면 한 번 깜빡이며 일반 404가 먼저 그려진다)
  if (fresh && held?.path !== location.pathname) {
    setHeld({ path: location.pathname, state: fresh })
  }

  // 2. 쥔 뒤에는 history의 state를 비워 새로고침이 일반 처리가 되게 한다
  const hasFresh = fresh !== null
  useEffect(() => {
    if (hasFresh) {
      void navigate(`${location.pathname}${location.search}${location.hash}`, {
        replace: true,
        state: null,
      })
    }
  }, [hasFresh, navigate, location.pathname, location.search, location.hash])

  // 3. 지금 경로에 해당하는 값만 돌려준다
  return fresh ?? (held && held.path === location.pathname ? held.state : null)
}
