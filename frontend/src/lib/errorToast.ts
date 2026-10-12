import { toast, type ExternalToast } from 'sonner'

/*
 * 오류 코드 중 "서비스가 잘못됐다"가 아니라 "사용자가 알아야 할 제한·안내"인 코드.
 * 이 코드는 빨간 오류 토스트 대신 노란 경고 토스트로 알린다(Task 037, 디자인 결정 "세트 1").
 * 경고로 바꿀 코드가 생기면 여기에만 추가한다. 호출하는 쪽에 코드 목록을 흩뿌리지 않기 위해 한곳에 모았다.
 *  - WISHLIST_LIMIT_EXCEEDED: 찜 20개 초과(409). 사용자가 찜을 정리하면 해결되는 일이라 오류가 아니라 경고다
 */
const WARNING_ERROR_CODES: ReadonlySet<string> = new Set(['WISHLIST_LIMIT_EXCEEDED'])

/**
 * API 오류를 토스트로 알린다. 경고 대상 코드면 경고 토스트, 그 밖의 오류는 오류 토스트다.
 *
 * @param errorCode ApiError.errorCode (서버가 준 코드. 없으면 null)
 * @param userMessage 화면에 보일 문장 (ApiError.userMessage)
 * @param options 버튼(action) 같은 sonner 추가 옵션. 없으면 생략해 sonner 호출 인자가 문장 하나만 되게 한다
 */
export function toastApiError(
  errorCode: string | null | undefined,
  userMessage: string,
  options?: ExternalToast,
) {
  const show = errorCode != null && WARNING_ERROR_CODES.has(errorCode) ? toast.warning : toast.error
  if (options) show(userMessage, options)
  else show(userMessage)
}
