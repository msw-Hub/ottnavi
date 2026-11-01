import { useCallback, useSyncExternalStore } from 'react'
import type { ConsentStatus } from '@/features/consent/components/ConsentSettingsPanel'
import {
  CONSENT_STORAGE_KEY,
  parseStoredConsent,
  resolveConsentStatus,
  serializeConsent,
  type StoredConsent,
} from '@/features/consent/consentPolicy'

/*
 * 방문 통계 동의 상태 저장소다. localStorage에 선택을 저장하고, 화면 여러 곳(띠·푸터·처리방침 카드)이 같은 값을 보게 한다.
 *
 * 왜 React 상태(useState)가 아니라 모듈 저장소 + useSyncExternalStore인가: 띠와 설정 카드가 서로 다른 컴포넌트 트리에 있어도
 * 한쪽에서 누르면 다른 쪽이 바로 바뀌어야 하고, 다른 탭에서 바꾼 선택도 반영해야 한다(storage 이벤트).
 * 외부 저장소를 React에 연결하는 표준 방법이 useSyncExternalStore다.
 *
 * 저장소를 못 쓰는 경우(시크릿 모드의 일부 브라우저, 저장 공간 차단 등): 모든 접근을 try/catch로 감싼다.
 * 쓰기가 실패하면 이번 세션 동안만 메모리에 두고, 이 경우 GA는 켜지 않는다(analyticsEnabled가 false).
 * 이유: 선택을 저장하지 못하면 다음 방문에서 동의 기록을 증명할 수 없으므로, 동의했더라도 통계 전송은 하지 않는 쪽이 안전하다.
 */

// 저장소 쓰기에 실패했을 때 이번 세션에서만 쓰는 선택값. null이면 저장소가 정상이다
let memoryOnly: StoredConsent | null = null

// 상태가 바뀌면 알려 줄 구독자(useSyncExternalStore가 등록한다)
const listeners = new Set<() => void>()

function notify(): void {
  listeners.forEach((listener) => listener())
}

/** 저장된 선택을 읽어 지금 상태로 바꿔 돌려준다. 저장소 접근이 막혀 있으면 메모리 값, 그것도 없으면 'unset'이다. */
export function readConsentStatus(now: Date = new Date()): ConsentStatus {
  // 1. 저장소에 쓰지 못해 메모리에만 둔 선택이 있으면 그것을 쓴다
  if (memoryOnly !== null) return resolveConsentStatus(JSON.stringify(memoryOnly), now)
  // 2. 저장소에서 읽는다. 접근 자체가 예외를 던질 수 있어(차단된 저장소) try/catch로 감싼다
  try {
    return resolveConsentStatus(window.localStorage.getItem(CONSENT_STORAGE_KEY), now)
  } catch {
    return 'unset'
  }
}

/** 선택이 브라우저 저장소에 남아 다음 방문에도 유지되는지. false면 이번 세션 메모리에서만 유지 중이다. */
export function isConsentPersisted(): boolean {
  return memoryOnly === null
}

/** 선택을 저장하고 구독자에게 알린다. 저장에 실패하면 메모리에만 둔다. */
export function writeConsent(status: 'granted' | 'denied', now: Date = new Date()): void {
  // 1. 저장할 문자열을 만든다
  const raw = serializeConsent(status, now)
  try {
    // 2. 저장소에 쓴다. 성공하면 메모리 대체값은 비운다
    window.localStorage.setItem(CONSENT_STORAGE_KEY, raw)
    memoryOnly = null
  } catch {
    // 3. 저장소를 못 쓰면 이번 세션 메모리에만 둔다(GA는 켜지 않는다)
    memoryOnly = parseStoredConsent(raw)
  }
  // 4. 화면에 알린다
  notify()
}

/** 테스트용: 메모리 대체값과 구독 상태를 비운다. */
export function resetConsentStoreForTest(): void {
  memoryOnly = null
  notify()
}

function subscribe(listener: () => void): () => void {
  listeners.add(listener)
  // 다른 탭에서 선택을 바꿨을 때도 반영한다(storage 이벤트는 "다른" 탭에서만 오므로 이 탭의 변경과 겹치지 않는다)
  window.addEventListener('storage', listener)
  return () => {
    listeners.delete(listener)
    window.removeEventListener('storage', listener)
  }
}

interface UseConsentResult {
  status: ConsentStatus // 화면에 보일 동의 상태
  analyticsEnabled: boolean // GA를 켜도 되는가: 동의했고 그 선택이 저장소에 남아 있을 때만 true
  accept: () => void
  reject: () => void
}

/** 방문 통계 동의 상태를 읽고 바꾸는 훅. 반환하는 status는 문자열이라 값이 같으면 다시 그리지 않는다. */
export function useConsent(): UseConsentResult {
  // getSnapshot이 문자열(원시값)을 돌려줘서 매 호출 결과가 같으면 같은 값으로 취급된다(무한 렌더 방지)
  const status = useSyncExternalStore(subscribe, () => readConsentStatus())
  const accept = useCallback(() => writeConsent('granted'), [])
  const reject = useCallback(() => writeConsent('denied'), [])
  return { status, analyticsEnabled: status === 'granted' && isConsentPersisted(), accept, reject }
}
