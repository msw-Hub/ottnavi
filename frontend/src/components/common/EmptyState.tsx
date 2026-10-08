import type { ReactNode } from 'react'
import { InboxIcon } from 'lucide-react'

interface EmptyStateProps {
  title: string // 무엇이 비었는지(예: "검색 결과가 없습니다")
  description?: string // 다음에 할 일 안내(예: "제목을 다르게 입력하거나 원제로 검색해 보세요.")
  action?: ReactNode // 다음 행동 버튼·링크(예: "찜하러 가기"). 화면마다 달라 호출부가 만들어 넘긴다
}

/**
 * 불러오기는 성공했지만 보여 줄 내용이 없을 때의 상태 화면이다(로딩·오류·빈 상태 중 빈 상태, frontend.md).
 *
 * 빈 목록을 그냥 비워 두지 않는 이유: 사용자가 "아직 불러오는 중인지, 오류인지, 정말 없는지" 구분할 수 없다.
 * 오류가 아니므로 role="alert"를 쓰지 않는다(낭독기가 하던 말을 끊고 읽을 만큼 급한 정보가 아니다).
 */
export function EmptyState({ title, description, action }: EmptyStateProps) {
  return (
    <div className="grid content-start justify-items-center gap-2 rounded-lg border border-border bg-background px-4 py-5 text-center">
      <InboxIcon aria-hidden="true" className="size-8 text-muted-foreground" />
      <p className="font-bold">{title}</p>
      {description && <p className="text-sm text-muted-foreground">{description}</p>}
      {action}
    </div>
  )
}
