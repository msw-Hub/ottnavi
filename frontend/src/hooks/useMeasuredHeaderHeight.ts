import { useEffect, type RefObject } from 'react'

// 실제로 잰 머리글 높이를 담는 CSS 변수 이름. 토스트 위치(app/providers.tsx)가 이 변수를 먼저 읽는다
export const MEASURED_HEADER_HEIGHT_VAR = '--header-height-measured'

/**
 * 머리글의 "실제" 높이를 재서 CSS 변수(--header-height-measured)에 계속 반영한다.
 *
 * 왜 필요한가:
 * styles/tokens.css의 --header-height는 "두 줄 머리글" 기준으로 손으로 잰 고정값(375px에서 99px)이다.
 * 그런데 목업의 개발용 로그인 전환이나 관리자의 "관리" 링크가 있으면 머리글이 세 줄(약 131px)이 되어
 * 고정값보다 커지고, 그러면 토스트(머리글 높이 + 12px 아래에서 시작)가 머리글을 덮는다.
 * ResizeObserver는 줄 수가 바뀌거나 글자 크기·화면 폭이 달라질 때마다 알려 주므로, 항상 실제 높이를 따라간다.
 *
 * 왜 --header-height를 직접 덮어쓰지 않고 변수를 따로 두는가(되먹임 방지):
 * 머리글 자신이 min-height로 --header-height를 쓴다. 이 변수에 측정값을 쓰면 세 줄(131px)로 커졌다가
 * 두 줄로 줄어들 때 min-height가 이전의 큰 값(131px)에 묶여 머리글이 줄어들지 못한다.
 * 그래서 머리글의 min-height는 tokens.css 값을 그대로 쓰고, 토스트만 이 별도 변수를 따라가게 했다.
 * 첫 렌더 전에는 이 변수가 없으므로 토스트는 var(--header-height)로 대신한다(providers.tsx의 fallback).
 *
 * 의존성: ref는 바뀌지 않으므로 마운트 때 한 번만 관찰을 시작하고, 정리 함수에서 관찰을 끊고 변수를 지운다.
 */
export function useMeasuredHeaderHeight(headerRef: RefObject<HTMLElement | null>) {
  useEffect(() => {
    // 1. 머리글 요소가 없으면 잴 것이 없다
    const header = headerRef.current
    if (!header) return

    // 2. 현재 높이를 CSS 변수에 쓴다(border 포함 높이)
    const root = document.documentElement
    const apply = () => {
      root.style.setProperty(MEASURED_HEADER_HEIGHT_VAR, `${header.offsetHeight}px`)
    }
    apply()

    // 3. 크기가 바뀔 때마다 다시 쓴다(줄 수 변화, 폭 변화, 로그인 상태 변화)
    // (ResizeObserver가 없는 환경에서는 처음 한 번만 잰다)
    const observer = typeof ResizeObserver === 'undefined' ? null : new ResizeObserver(apply)
    observer?.observe(header)

    // 4. 화면을 떠날 때 관찰을 끊고 변수를 지워, 토스트가 tokens.css 기본값으로 돌아가게 한다
    return () => {
      observer?.disconnect()
      root.style.removeProperty(MEASURED_HEADER_HEIGHT_VAR)
    }
  }, [headerRef])
}
