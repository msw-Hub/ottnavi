import { useState, type FormEvent } from 'react'
import { InfoIcon, TriangleAlertIcon } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { RadioGroup, RadioGroupItem } from '@/components/ui/radio-group'
import type { RunNotice } from '@/features/admin/components/collect/runNotice'
import { PhaseLaterTag } from '@/features/admin/components/PhaseLaterTag'
import { COLLECT_TIER_LABELS } from '@/features/admin/collectRules'
import type { CollectTier } from '@/features/admin/mockTypes'
import { formatIsoDate } from '@/lib/format'

interface CollectRunFormProps {
  today: string // 오늘 날짜('YYYY-MM-DD'). 대상 날짜의 기본값이자 상한이다
  isPending: boolean // 접수 요청 중인지(수동 실행뿐 아니라 [다시 실행]도 포함하지 않는다. 이 양식의 제출만)
  notice: RunNotice | null // 마지막 실행 요청의 결과 안내. 없으면 띠를 보이지 않는다
  onSubmit: (targetDate: string, tier: CollectTier) => void // [수집 실행]을 눌렀다
  onViewRun: (executionId: number) => void // 안내 띠의 [이력에서 보기]. 그 행으로 포커스를 옮긴다
}

// 범위 선택지. DAILY·WEEKLY는 보이되 고를 수 없다(2단계, 사용자 결정 2026-10-11)
const TIER_OPTIONS: { value: CollectTier; isEnabled: boolean }[] = [
  { value: 'ALL', isEnabled: true },
  { value: 'DAILY', isEnabled: false },
  { value: 'WEEKLY', isEnabled: false },
]

// 안내 띠 한 줄의 글자
function noticeText(notice: Exclude<RunNotice, { kind: 'ERROR' }>): string {
  const scope = `${formatIsoDate(notice.targetDate) ?? notice.targetDate} · ${COLLECT_TIER_LABELS[notice.tier]}`
  switch (notice.kind) {
    case 'ACCEPTED':
      return `수집을 접수했습니다. 실행 ID ${notice.executionId} · ${scope}. 진행 상태는 아래 이력에서 확인하세요.`
    case 'ALREADY_RUNNING':
      return `같은 날짜·범위(${scope})의 수집이 이미 실행 중입니다. 끝난 뒤 이력에서 결과를 확인하세요.`
    case 'ALREADY_COMPLETED':
      return `${scope} 수집은 이미 성공했습니다. 같은 날짜는 다시 수집하지 않습니다. 다른 날짜를 고르세요.`
    case 'RECOVERY_REQUIRED':
      return `${scope}에 중단된 실행이 있습니다. 아래 이력에서 [복구]를 먼저 실행한 뒤 다시 실행하세요.`
  }
}

/**
 * 수집 수동 실행 양식이다(SCR-16): 대상 날짜 + 범위 + [수집 실행].
 *
 * 범위 중 "전체(ALL)"만 고를 수 있다. 매일(DAILY)·주간(WEEKLY) 갱신은 2단계(O3)에서 열리므로 숨기지 않고 비활성 + "2단계부터" 태그로 보인다.
 * 실행은 202로 접수만 되고 실제 수집은 서버에서 비동기로 돈다(TECH 3절). 그래서 결과는 "접수했습니다" 안내이고, 진행은 이력에서 본다.
 * 같은 날짜·범위가 실행 중(409)이거나 이미 성공(409)이면 오류(빨강)가 아니라 info 안내로 보인다(runNotice.ts 주석).
 *
 * 폼: Enter로 제출된다(<form>의 기본 동작). 제출 중에는 aria-busy와 버튼 비활성으로 중복 접수를 막는다.
 */
export function CollectRunForm({
  today,
  isPending,
  notice,
  onSubmit,
  onViewRun,
}: CollectRunFormProps) {
  const [targetDate, setTargetDate] = useState(today)
  const [errorText, setErrorText] = useState<string | null>(null)

  function handleSubmit(event: FormEvent) {
    event.preventDefault()
    if (!/^\d{4}-\d{2}-\d{2}$/.test(targetDate)) {
      setErrorText('대상 날짜를 입력해 주세요.')
      return
    }
    if (targetDate > today) {
      setErrorText('대상 날짜는 오늘 또는 그 이전이어야 해요.')
      return
    }
    setErrorText(null)
    onSubmit(targetDate, 'ALL')
  }

  return (
    <form
      onSubmit={handleSubmit}
      noValidate
      aria-busy={isPending}
      aria-labelledby="collect-run-heading"
      className="grid gap-4 rounded-xl border border-border bg-card p-4"
    >
      <h2 id="collect-run-heading" className="text-lg font-semibold">
        수동 실행
      </h2>

      <div className="grid gap-1.5 sm:max-w-xs">
        <Label htmlFor="collect-target-date">대상 날짜</Label>
        <Input
          id="collect-target-date"
          type="date"
          max={today}
          required
          value={targetDate}
          onChange={(event) => setTargetDate(event.target.value)}
          aria-invalid={errorText ? true : undefined}
          aria-describedby={errorText ? 'collect-target-date-error' : undefined}
        />
        {errorText && (
          <p id="collect-target-date-error" className="text-sm text-destructive">
            {errorText}
          </p>
        )}
      </div>

      <fieldset className="grid gap-2">
        <legend className="mb-1 text-sm font-medium">범위</legend>
        <RadioGroup value="ALL" aria-label="수집 범위" className="gap-2">
          {TIER_OPTIONS.map(({ value, isEnabled }) => (
            <div key={value} className="flex flex-wrap items-center gap-2">
              <RadioGroupItem id={`collect-tier-${value}`} value={value} disabled={!isEnabled} />
              <Label
                htmlFor={`collect-tier-${value}`}
                className={isEnabled ? undefined : 'text-muted-foreground'}
              >
                {COLLECT_TIER_LABELS[value]}
              </Label>
              {!isEnabled && <PhaseLaterTag />}
            </div>
          ))}
        </RadioGroup>
      </fieldset>

      <div>
        <Button type="submit" disabled={isPending}>
          {isPending ? '접수 중…' : '수집 실행'}
        </Button>
      </div>

      {notice && notice.kind === 'ERROR' && (
        <p role="alert" className="flex items-center gap-1.5 text-sm text-destructive">
          <TriangleAlertIcon aria-hidden="true" className="size-4 shrink-0" />
          {notice.message}
        </p>
      )}
      {notice && notice.kind !== 'ERROR' && (
        <div
          role="status"
          className="flex flex-wrap items-center gap-x-3 gap-y-2 rounded-lg border border-info bg-info-bg px-3 py-2.5 text-sm text-info"
        >
          <InfoIcon aria-hidden="true" className="size-4 shrink-0" />
          <p className="min-w-0 flex-1">{noticeText(notice)}</p>
          {notice.kind === 'ACCEPTED' && (
            <Button
              type="button"
              variant="outline"
              size="sm"
              onClick={() => onViewRun(notice.executionId)}
            >
              이력에서 보기
            </Button>
          )}
          {notice.kind === 'ALREADY_RUNNING' && notice.runningExecutionId !== null && (
            <Button
              type="button"
              variant="outline"
              size="sm"
              onClick={() => onViewRun(notice.runningExecutionId!)}
            >
              실행 중인 이력 보기
            </Button>
          )}
        </div>
      )}
    </form>
  )
}
