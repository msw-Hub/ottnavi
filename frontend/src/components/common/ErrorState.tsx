import { RefreshCwIcon, TriangleAlertIcon } from 'lucide-react'
import { isApiError } from '@/api/http'
import { Button } from '@/components/ui/button'
import { DEFAULT_ERROR_MESSAGE } from '@/lib/errorMessages'

interface ErrorStateProps {
  error?: unknown // 실패 원인. TanStack Query의 error를 그대로 넘긴다(ApiError면 안내 문장을 거기서 꺼낸다)
  message?: string // 안내 문장을 직접 정할 때. 주면 error보다 우선한다
  title?: string // 제목. 기본 "불러오지 못했습니다"
  onRetry?: () => void // "다시 시도" 버튼을 눌렀을 때 할 일(보통 refetch). 없으면 버튼을 숨긴다
}

/**
 * 데이터를 불러오지 못했을 때 보여 주는 상태 화면이다(로딩·오류·빈 상태 중 오류, frontend.md).
 *
 * 안내 문장을 고르는 순서:
 * 1. message prop을 줬으면 그것을 쓴다(화면마다 특별한 안내가 필요할 때).
 * 2. error가 ApiError면 userMessage를 쓴다. 공통 인터셉터(api/http.ts)가 errorCode로 문서(error-codes.md)의
 *    화면 메시지를 이미 찾아 둔 값이라, 이 컴포넌트가 다시 표를 찾지 않는다.
 * 3. 그 밖의 오류(코드에서 난 예외 등)는 기본 메시지를 쓴다. 개발자용 오류 내용(error.message)은 화면에 보이지 않는다.
 *
 * role="alert": 오류는 바로 알아야 하는 정보라 화면 낭독기가 즉시 읽어 준다.
 * 오류 글자·아이콘은 어두운 바탕용 밝은 빨강(--destructive)이다. 색만으로 오류임을 알리지 않도록 경고 아이콘과 제목 글자도 함께 둔다.
 */
export function ErrorState({
  error,
  message,
  title = '불러오지 못했습니다',
  onRetry,
}: ErrorStateProps) {
  const description = message ?? (isApiError(error) ? error.userMessage : DEFAULT_ERROR_MESSAGE)

  return (
    <div
      role="alert"
      className="grid content-start justify-items-center gap-2 rounded-lg border border-border bg-background px-4 py-5 text-center"
    >
      <TriangleAlertIcon aria-hidden="true" className="size-8 text-destructive" />
      <p className="font-bold text-destructive">{title}</p>
      <p className="text-sm text-muted-foreground">{description}</p>
      {onRetry && (
        // size="lg"(36px)에 h-10을 덧씌워 40px로 만든다: 손가락으로 누르기 쉽게(Task 026 검수). components/ui는 고치지 않는다
        <Button type="button" size="lg" onClick={onRetry} className="mt-1 h-10">
          <RefreshCwIcon aria-hidden="true" />
          다시 시도
        </Button>
      )}
    </div>
  )
}
