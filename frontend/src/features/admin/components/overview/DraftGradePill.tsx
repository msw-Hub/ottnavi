import { CheckCircle2Icon, TriangleAlertIcon, XCircleIcon } from 'lucide-react'
import type { OverviewDraftStatus } from '@/api/generated/model'
import { AdminStatusPill } from '@/features/admin/components/AdminStatusPill'
import { getWarningLabel } from '@/features/admin/overviewRules'

interface DraftGradePillProps {
  grade: OverviewDraftStatus // PASSED 성공 / WARNING 주의 / FAILED 실패 (overviewRules의 getDraftGrade로 계산한 값)
}

/**
 * 등급 배지다. 색만으로 구분하지 않도록 세 등급의 모양이 모두 다르다.
 * - 성공: 초록으로 꽉 채움 + 체크
 * - 주의: 노랑으로 꽉 채움 + 세모 느낌표(관리자 화면의 "미매핑"·"요금 확인일 오래됨"과 같은 주의 모양)
 * - 실패: 바탕 없음 + 빨간 실선 + X
 * 흑백으로 보면 성공과 주의가 둘 다 "채움"이지만 아이콘(체크/세모)과 글자가 달라 구분된다.
 */
export function DraftGradePill({ grade }: DraftGradePillProps) {
  if (grade === 'PASSED') {
    return <AdminStatusPill icon={CheckCircle2Icon} label="성공" tone="success" size="sm" />
  }
  if (grade === 'WARNING') {
    return <AdminStatusPill icon={TriangleAlertIcon} label="주의" tone="attention" size="sm" />
  }
  return <AdminStatusPill icon={XCircleIcon} label="실패" tone="danger" size="sm" />
}

interface DraftWarningTagsProps {
  codes: string[] // 주의 이유 코드들(OverviewDraft.warnings)
  showsDescriptions?: boolean // true면 라벨 아래에 설명 문장을 함께 보인다(주의 탭·수정 칸)
}

/**
 * 주의 이유 라벨 묶음이다. 실패 이유(GuardFailureTags)와 같은 배치(알약 한 줄 + 아래 설명 목록)이고,
 * 아이콘만 다르다(실패 이유는 동그라미 느낌표, 주의 이유는 세모 느낌표). 알약은 테두리만 있어 등급 배지보다 덜 눈에 띈다.
 * 라벨 표(WARNING_LABELS)는 토스트 문장과 같이 쓰려고 overviewRules.ts에 있다.
 */
export function DraftWarningTags({ codes, showsDescriptions = false }: DraftWarningTagsProps) {
  if (codes.length === 0) return null
  const items = codes.map((code) => ({ code, ...getWarningLabel(code) }))
  return (
    <div className="grid gap-1">
      <ul aria-label="주의 이유" className="flex flex-wrap gap-1.5">
        {items.map(({ code, label, description }) => (
          <li key={code} title={description}>
            <AdminStatusPill icon={TriangleAlertIcon} label={label} tone="outline" size="sm" />
          </li>
        ))}
      </ul>
      {showsDescriptions && (
        <ul className="grid gap-0.5 text-xs break-keep text-muted-foreground">
          {items.map(({ code, description }) => (
            <li key={code}>{description}</li>
          ))}
        </ul>
      )}
    </div>
  )
}
