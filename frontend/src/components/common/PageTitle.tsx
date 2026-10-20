import { useEffect } from 'react'

/** 모든 화면 제목에 붙는 서비스 이름. 탭 제목은 "{화면 이름} · OTT내비" 형식이다. */
export const SITE_NAME = 'OTT내비'

/** 홈의 탭 제목. index.html의 <title>과 같은 값이다(링크 미리보기·크롤러가 보는 값과 JS가 그린 값이 어긋나지 않게). */
export const HOME_TITLE = 'OTT내비 – 찜한 작품으로 짜는 3개월 OTT 구독 플랜'

interface PageTitleProps {
  name?: string // 화면 이름("랭킹" 등). 탭에는 "{name} · OTT내비"로 보인다. 홈이면 생략한다
}

/**
 * 브라우저 탭 제목을 바꾸는 컴포넌트다. 화면 컴포넌트(pages/)가 맨 위에 하나만 둔다. 화면에는 아무것도 그리지 않는다(null).
 *
 * React 19의 <title> 렌더링 대신 document.title을 직접 바꾸는 이유(브라우저에서 확인한 결과):
 * 1. index.html에 정적 <title>이 있다(링크 미리보기·크롤러가 JS 없이 읽는 값이라 지울 수 없다).
 *    이 상태에서 React의 <title>을 그리면 React가 새 <title>을 head에 "추가"해서 head에 <title>이 두 개 남았다.
 * 2. 두 개가 있으면 브라우저와 검색 엔진의 동작이 정해져 있지 않다(React 문서도 같은 경고).
 *    지금은 React가 만든 것이 앞에 놓여 탭 제목이 맞게 보이지만, 그 순서에 기대는 것은 불안하다.
 * 3. document.title에 값을 넣으면 브라우저가 기존 <title> 하나의 글자만 바꾸므로 항상 하나만 남는다.
 *
 * 의존성 [title]: 화면 이름·작품명·검색어가 바뀔 때만 다시 쓴다. 정리 함수는 두지 않는다: 다음 화면의 PageTitle이 바로 덮어쓰고,
 * 정리에서 원래 값으로 되돌리면 화면 이동 사이에 제목이 깜빡인다.
 *
 * 개인 정보(이름·이메일 등)는 제목에 넣지 않는다. 로그인이 필요한 화면은 고정 문구만 쓴다(탭 제목은 브라우저 기록·북마크에 남는다).
 */
export function PageTitle({ name }: PageTitleProps) {
  const title = name ? `${name} · ${SITE_NAME}` : HOME_TITLE
  useEffect(() => {
    document.title = title
  }, [title])
  return null
}
