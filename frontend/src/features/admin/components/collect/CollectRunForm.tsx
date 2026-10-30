import { useState, type FormEvent } from 'react'
import { InfoIcon, TriangleAlertIcon } from 'lucide-react'
import { DemoLockedButton } from '@/components/common/DemoLockedButton'
import { DemoReadOnlyHint } from '@/components/common/DemoReadOnlyHint'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { RadioGroup, RadioGroupItem } from '@/components/ui/radio-group'
import type { RunNotice } from '@/features/admin/components/collect/runNotice'
import { PhaseLaterTag } from '@/features/admin/components/PhaseLaterTag'
import { COLLECT_TIER_LABELS } from '@/features/admin/collectRules'
import type { CollectTier } from '@/api/generated/model'
import { formatIsoDate } from '@/lib/format'

interface CollectRunFormProps {
  today: string // 오늘 날짜('YYYY-MM-DD'). 대상 날짜의 기본값이자 상한이다
  isPending: boolean // 접수 요청 중인지(수동 실행뿐 아니라 [다시 실행]도 포함하지 않는다. 이 양식의 제출만)
  notice: RunNotice | null // 마지막 실행 요청의 결과 안내. 없으면 띠를 보이지 않는다. DATE_INVALID는 띠가 아니라 날짜 입력 아래에 보인다
  onSubmit: (targetDate: string, tier: CollectTier) => void // [수집 실행]을 눌렀다
  onViewRun: (executionId: number) => void // 안내 띠의 [이력에서 보기]. 그 행으로 포커스를 옮긴다
}

// 데모 계정에게 [수집 실행]이 막힌 이유를 적은 문장의 id(버튼이 aria-describedby로 가리킨다)
const COLLECT_RUN_LOCK_NOTE_ID = 'collect-run-demo-note'

// 범위 선택지. DAILY·WEEKLY는 예약 실행(cron)이 쓰는 범위라 보이되 수동으로는 고를 수 없다(사용자 결정 2026-10-11, 문구는 2026-10-30에 사실대로 정리)
const TIER_OPTIONS: { value: CollectTier; isEnabled: boolean }[] = [
  { value: 'ALL', isEnabled: true },
  { value: 'DAILY', isEnabled: false },
  { value: 'WEEKLY', isEnabled: false },
]

// 대상 날짜·범위 글자(예: "2026. 10. 10. · 전체(ALL)")
function scopeText(targetDate: string, tier: CollectTier): string {
  return `${formatIsoDate(targetDate) ?? targetDate} · ${COLLECT_TIER_LABELS[tier]}`
}

// 안내 띠 한 줄의 글자
function noticeText(notice: Exclude<RunNotice, { kind: 'ERROR' | 'DATE_INVALID' }>): string {
  const scope = scopeText(notice.targetDate, notice.tier)
  switch (notice.kind) {
    case 'ACCEPTED':
      return `수집을 접수했습니다. 실행 ID ${notice.executionId} · ${scope}. 진행 상태는 아래 이력에서 확인하세요.`
    case 'ALREADY_RUNNING': {
      // 수집은 전체에서 동시에 하나만 돈다(Task 045). 같은 날짜·범위가 아니라 다른 날짜가 막고 있을 수도 있어 문장을 나눈다
      const running = notice.running
      if (!running) {
        return '수집이 이미 실행 중입니다. [새로 고침]으로 이력을 확인하고, 끝난 뒤 다시 실행하세요.'
      }
      if (running.targetDate === notice.targetDate && running.tier === notice.tier) {
        return `같은 날짜·범위(${scope})의 수집이 이미 실행 중입니다. 끝난 뒤 이력에서 결과를 확인하세요.`
      }
      return `다른 날짜·범위의 수집이 실행 중입니다(실행 ${running.executionId} · ${scopeText(running.targetDate, running.tier)}). 수집은 한 번에 하나만 돌기 때문에, 끝난 뒤 다시 실행하세요.`
    }
    case 'ALREADY_COMPLETED':
      return `${scope} 수집은 이미 성공했습니다. 같은 날짜는 다시 수집하지 않습니다. 다른 날짜를 고르세요.`
    case 'RECOVERY_REQUIRED':
      return `${scope}에 중단된 실행이 있습니다. 아래 이력에서 [복구]를 먼저 실행한 뒤 다시 실행하세요.`
  }
}

/**
 * 수집 수동 실행 양식이다(SCR-16): 대상 날짜 + 범위 + [수집 실행].
 *
 * 범위 중 "전체(ALL)"만 고를 수 있다. 매일(DAILY)·주간(WEEKLY) 갱신은 예약 실행(cron) 전용이라 숨기지 않고 비활성 + "자동 실행 전용" 태그로 보인다.
 * 실행은 202로 접수만 되고 실제 수집은 서버에서 비동기로 돈다(TECH 3절). 그래서 결과는 "접수했습니다" 안내이고, 진행은 이력에서 본다.
 * 같은 날짜·범위가 실행 중(409)이거나 이미 성공(409)이면 오류(빨강)가 아니라 info 안내로 보인다(runNotice.ts 주석).
 * 다른 날짜가 실행 중이어도 409 BATCH_ALREADY_RUNNING이다(수집은 한 번에 하나만 돈다, Task 045). 이때는 막고 있는 실행을 안내한다.
 * 서버가 대상 날짜를 거절하면(400 fieldErrors.targetDate, 예: 운영은 오늘만) 그 문구를 날짜 입력 아래에 보인다.
 * 화면은 미래 날짜만 미리 막는다. 운영에서 오늘이 아닌 날짜를 막는 것은 서버 설정이라 화면이 따라 판단하지 않는다.
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
  const [errorText, setErrorText] = useState<string | null>(null) // 요청 전에 화면이 찾은 날짜 오류
  // 운영자가 날짜를 고친 뒤에는 지난 요청의 서버 날짜 오류를 숨긴다. 어느 안내를 숨겼는지 객체로 기억한다(새 요청은 새 객체라 다시 보인다)
  const [dismissedNotice, setDismissedNotice] = useState<RunNotice | null>(null)

  // 날짜 입력 아래 문구: 화면 검사 오류가 먼저, 없으면 서버가 fieldErrors.targetDate로 거절한 문구(예: 운영은 오늘만 실행)
  const serverDateError =
    notice?.kind === 'DATE_INVALID' && notice !== dismissedNotice ? notice.message : null
  const dateErrorText = errorText ?? serverDateError

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
          onChange={(event) => {
            setTargetDate(event.target.value)
            setDismissedNotice(notice)
          }}
          aria-invalid={dateErrorText ? true : undefined}
          aria-describedby={dateErrorText ? 'collect-target-date-error' : undefined}
        />
        {dateErrorText && (
          <p id="collect-target-date-error" className="text-sm text-destructive">
            {dateErrorText}
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
              {/* 이전 문구: "2단계부터"(기본 label). 지금은 예약 실행(cron)으로 이미 돌고 수동 실행만 ALL로 제한된다 */}
              {!isEnabled && <PhaseLaterTag label="자동 실행 전용" />}
            </div>
          ))}
        </RadioGroup>
      </fieldset>

      {/* 데모 계정이면 [수집 실행]이 막히고(눌러도 요청 없음, Enter 제출도 막힌다) 옆에 이유를 둔다(Task 109) */}
      <div className="flex flex-wrap items-center gap-x-3 gap-y-2">
        <DemoLockedButton
          type="submit"
          disabled={isPending}
          lockedDescriptionId={COLLECT_RUN_LOCK_NOTE_ID}
        >
          {isPending ? '접수 중…' : '수집 실행'}
        </DemoLockedButton>
        <DemoReadOnlyHint variant="inline" id={COLLECT_RUN_LOCK_NOTE_ID}>
          데모 계정은 실행할 수 없어요
        </DemoReadOnlyHint>
      </div>

      {notice && notice.kind === 'ERROR' && (
        <p role="alert" className="flex items-center gap-1.5 text-sm text-destructive">
          <TriangleAlertIcon aria-hidden="true" className="size-4 shrink-0" />
          {notice.message}
        </p>
      )}
      {notice && notice.kind !== 'ERROR' && notice.kind !== 'DATE_INVALID' && (
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
          {notice.kind === 'ALREADY_RUNNING' && notice.running && (
            <Button
              type="button"
              variant="outline"
              size="sm"
              onClick={() => onViewRun(notice.running!.executionId)}
            >
              실행 중인 이력 보기
            </Button>
          )}
        </div>
      )}
    </form>
  )
}
