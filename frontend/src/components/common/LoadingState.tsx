import { LoaderCircleIcon } from 'lucide-react'

interface LoadingStateProps {
  message?: string // 무엇을 불러오는지 알리는 문장. 기본 "불러오는 중입니다"
}

/**
 * 데이터를 불러오는 동안 보여 주는 상태 화면이다(로딩·오류·빈 상태 중 로딩, frontend.md).
 *
 * - role="status": 화면 낭독기가 하던 말을 끊지 않고 이 문장을 읽어 준다(급한 알림인 role="alert"와 다르다).
 * - 회전 아이콘은 motion-safe일 때만 돈다. 사용자가 운영체제에서 "동작 줄이기"를 켜면 멈춘 아이콘만 보인다
 *   (prefers-reduced-motion, 어지럼증을 느끼는 사용자 배려).
 * - 아래 회색 줄(skeleton)은 곧 내용이 채워질 자리를 보여 준다. 장식이라 낭독기에는 숨긴다.
 */
export function LoadingState({ message = '불러오는 중입니다' }: LoadingStateProps) {
  return (
    <div
      role="status"
      className="grid content-start justify-items-center gap-2 rounded-lg border border-border bg-background px-4 py-5 text-center"
    >
      <LoaderCircleIcon
        aria-hidden="true"
        className="size-8 text-muted-foreground motion-safe:animate-spin"
      />
      <p className="font-bold">{message}</p>
      <div aria-hidden="true" className="mt-1 grid w-full gap-2">
        <span className="block h-3 rounded-full bg-muted" />
        <span className="block h-3 w-4/5 rounded-full bg-muted" />
        <span className="block h-3 w-3/5 rounded-full bg-muted" />
      </div>
    </div>
  )
}
