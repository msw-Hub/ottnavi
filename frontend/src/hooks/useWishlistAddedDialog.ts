import { useSyncExternalStore } from 'react'

/** 찜 추가 팝업에 보일 내용 */
export interface WishlistAddedInfo {
  entryKey: string // 찜 항목 키("movie:496243:all"). 같은 작품이 다시 들어오면 새로 쌓지 않고 내용만 갱신된다
  wishlistItemId: number // 방금 담은(또는 이미 있던) 찜 항목 ID. 팝업에서 우선순위를 바꿀 때 쓴다. 현재 단계는 서버 찜 목록에서 읽는다
  name: string // 팝업 문장에 쓸 이름(영화 제목, 또는 "드라마 제목 시즌 2")
  posterUrl: string | null // 썸네일 포스터 주소. 없으면 "포스터 없음" 대체 표시
}

/*
 * 찜 추가 팝업의 열림 상태를 앱 전체에서 하나만 두는 아주 작은 저장소다.
 *
 * Context나 Zustand를 쓰지 않은 이유:
 * - 열림 상태는 "팝업 하나"의 UI 상태일 뿐이라 Provider를 하나 더 감쌀 만큼의 구조가 필요 없다(Zustand는 인증 전용, frontend.md).
 * - 찜 훅이 기존 테스트에서 Provider 없이 호출되므로, 모듈 안의 값 + 구독 방식이면 Provider가 없어도 안전하게 동작한다.
 * 값이 항상 "하나"라서 같은 작품을 연타해도 팝업이 쌓이지 않는다(내용만 바뀐다).
 */
let current: WishlistAddedInfo | null = null
const listeners = new Set<() => void>()

function emit() {
  listeners.forEach((listener) => listener())
}

function subscribe(listener: () => void) {
  listeners.add(listener)
  return () => {
    listeners.delete(listener)
  }
}

function getSnapshot() {
  return current
}

/*
 * 팝업을 닫을 때 키보드 포커스를 돌려줄 버튼(방금 누른 "찜하기" 버튼).
 *
 * 왜 따로 기억하는가(Task 090 키보드 점검): Radix Dialog는 "팝업이 열리는 순간 포커스를 가진 요소"로 포커스를 돌려준다.
 * 그런데 찜하기 버튼은 요청 중(isPending)에 disabled가 되고, 포커스를 가진 버튼이 disabled가 되면 브라우저가 포커스를 잃게 한다.
 * 그래서 팝업이 열리는 시점에는 포커스를 가진 요소가 없어 닫아도 돌아갈 곳이 없었다(Tab이 문서 맨 앞부터 다시 시작).
 * 버튼을 누르는 순간에 요소를 기억해 두면 닫을 때 그 버튼으로 돌려줄 수 있다.
 * 팝업을 띄우지 않은 요청(오류 등)에서 기억한 값이 남을 수 있어, 닫을 때 꺼내며 비우고 화면에 붙어 있는 요소일 때만 쓴다.
 */
let focusReturnTarget: HTMLElement | null = null

/** "찜하기"를 누른 버튼을 기억한다(팝업이 닫힐 때 포커스를 돌려주려는 것) */
export function rememberWishlistFocusTarget(element: HTMLElement) {
  focusReturnTarget = element
}

/** 기억해 둔 버튼을 꺼내며 비운다. 이미 화면에서 사라진 요소면 null이다 */
export function takeWishlistFocusTarget(): HTMLElement | null {
  const target = focusReturnTarget
  focusReturnTarget = null
  return target?.isConnected ? target : null
}

/** 팝업을 연다. 이미 열려 있으면 내용만 바꾼다(중복으로 쌓이지 않음) */
export function openWishlistAddedDialog(info: WishlistAddedInfo) {
  current = info
  emit()
}

/** 팝업을 닫는다 */
export function closeWishlistAddedDialog() {
  if (current === null) return
  current = null
  emit()
}

/** 팝업 컴포넌트가 지금 보일 내용을 읽는다. 닫혀 있으면 null이다 */
export function useWishlistAddedDialogInfo(): WishlistAddedInfo | null {
  return useSyncExternalStore(subscribe, getSnapshot, getSnapshot)
}

/**
 * 찜을 "추가"했을 때 선택지 팝업을 띄우는 함수를 돌려준다(해제할 때는 쓰지 않는다).
 *
 * 왜 필요한가: 찜을 눌러도 "다음에 무엇을 하면 되는지"가 없으면 사용자가 찜 목록(계산 화면으로 가는 입구)을 찾지 못한다.
 * 쇼핑몰의 "장바구니에 담았습니다 / 계속 쇼핑 / 장바구니 보기"와 같은 흐름이다. 팝업 자체는 앱 루트에 한 번 마운트된
 * components/common/WishlistAddedDialog가 그린다. 찜 훅이 기능 폴더마다 있어도(기능끼리 import 금지) 안내 동작은 같아야 해서 여기에 둔다.
 */
export function useWishlistAddedDialog() {
  return function showWishlistAddedDialog(info: WishlistAddedInfo) {
    openWishlistAddedDialog(info)
  }
}
