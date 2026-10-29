import { CircleAlertIcon } from 'lucide-react'
import { AdminStatusPill } from '@/features/admin/components/AdminStatusPill'
import { getGuardFailureLabel } from '@/features/admin/overviewRules'

interface GuardFailureTagsProps {
  codes: string[] // 검사에서 걸린 이유 코드들(OverviewDraft.guardFailures). 여러 개일 수 있다
  showsDescriptions?: boolean // true면 이유마다 풀어 쓴 설명 문장을 알약 아래에 보인다(검사 실패 탭)
}

/**
 * 검사 실패 이유를 알약(outline)으로 보인다. 모르는 코드는 "기타 검사 실패({코드})"로 보인다.
 * 풀어 쓴 설명은 title(마우스 올림)에도 넣지만 터치 화면에서는 보이지 않으므로, 필요하면 showsDescriptions로 글자로 펼친다.
 */
export function GuardFailureTags({ codes, showsDescriptions = false }: GuardFailureTagsProps) {
  if (codes.length === 0) return null
  const items = codes.map((code) => ({ code, ...getGuardFailureLabel(code) }))
  return (
    <div className="grid gap-1">
      <ul aria-label="검사 실패 이유" className="flex flex-wrap gap-1.5">
        {items.map(({ code, label, description }) => (
          <li key={code} title={description}>
            <AdminStatusPill icon={CircleAlertIcon} label={label} tone="outline" size="sm" />
          </li>
        ))}
      </ul>
      {showsDescriptions && (
        <ul className="grid gap-0.5 text-xs text-muted-foreground">
          {items.map(({ code, description }) => (
            <li key={code}>{description}</li>
          ))}
        </ul>
      )}
    </div>
  )
}
