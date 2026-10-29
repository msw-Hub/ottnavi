import { useState, type FormEvent } from 'react'
import { toast } from 'sonner'
import type { BulkDraftRun, OverviewTranslateSummary } from '@/api/generated/model'
import { isApiError } from '@/api/http'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { LlmWriteButton } from '@/features/admin/components/overview/LlmWriteButton'
import { OverviewBulkProgress } from '@/features/admin/components/overview/OverviewBulkProgress'
import { OverviewNoticeBand } from '@/features/admin/components/overview/OverviewNoticeBand'
import { OverviewUsageMeter } from '@/features/admin/components/overview/OverviewUsageMeter'
import {
  BULK_COUNT_DEFAULT,
  BULK_COUNT_MAX,
  BULK_COUNT_MIN,
  getRemainingCalls,
} from '@/features/admin/overviewRules'
import {
  useInvalidateOverviewQueries,
  useStartBulkOverviewDrafts,
} from '@/features/admin/hooks/useAdminOverviewTranslate'
import { getErrorMessage } from '@/lib/errorMessages'

// 이 패널의 안내·이유 문장 id(입력 도움말과 막힌 이유를 낭독기에 잇는다)
const COUNT_HELP_ID = 'llm-bulk-count-help'
const COUNT_ERROR_ID = 'llm-bulk-count-error'
export const BULK_BLOCKED_REASON_ID = 'llm-bulk-blocked-reason'

interface OverviewBulkPanelProps {
  summary: OverviewTranslateSummary | null // 번역 현황(남은 호출 수 경고에 쓴다). 아직 못 불러왔으면 null
  bulkRun: BulkDraftRun | null // 최근 일괄 번역 기록
  lastCheckedAt: number // 진행 상황을 마지막으로 받은 시각(밀리초)
  announcement: string // 상태가 바뀐 순간 한 번 낭독기에 알릴 문장
  blockedReason: string | null // 시작할 수 없는 이유(키 없음, 이미 실행 중, 단건 생성 중). 있으면 버튼이 비활성이다
  isProgressUnavailable?: boolean // 진행 상황을 불러오지 못했는지. true면 "실행한 적 없음"으로 오해하지 않게 진행 영역을 그리지 않는다
}

/**
 * "일괄 번역" 상자다: 찜 많은 순 상위 몇 편을 번역할지 정해 시작하고, 진행 상황을 본다.
 *
 * 일괄 번역은 서버에서 비동기로 돈다(접수 뒤 창을 닫아도 계속). 진행 상황은 화면 상태에 저장하지 않고
 * 초안 목록 응답의 bulk를 읽어 보인다(OverviewTranslateScreen의 폴링). 그래서 새로고침해도 실행 중이면 이어서 보인다.
 * 남은 호출보다 큰 수를 넣어도 막지 않고 경고만 한다: 서버가 도중에 DAILY_LIMIT로 멈추면 그때까지 만든 초안은 남기 때문이다.
 */
export function OverviewBulkPanel({
  summary,
  bulkRun,
  lastCheckedAt,
  announcement,
  blockedReason,
  isProgressUnavailable = false,
}: OverviewBulkPanelProps) {
  const start = useStartBulkOverviewDrafts()
  const invalidate = useInvalidateOverviewQueries()
  const [countText, setCountText] = useState(String(BULK_COUNT_DEFAULT))
  const [errorText, setErrorText] = useState<string | null>(null) // 입력값 오류
  const [alreadyRunning, setAlreadyRunning] = useState(false) // 서버가 "이미 실행 중"(409)이라고 답했는지
  const [failureText, setFailureText] = useState<string | null>(null) // 그 밖의 시작 실패 문장

  const count = Number(countText)
  const isValidCount =
    countText.trim() !== '' &&
    Number.isInteger(count) &&
    count >= BULK_COUNT_MIN &&
    count <= BULK_COUNT_MAX
  const remaining = summary ? getRemainingCalls(summary.usedToday, summary.dailyLimit) : null
  const showsRemainingWarning = isValidCount && remaining !== null && count > remaining

  function handleSubmit(event: FormEvent) {
    event.preventDefault()
    // 1. 연타 방지: 접수 중이면 두 번째 요청을 보내지 않는다
    if (start.isPending) return
    // 2. 입력값 검사(1~100의 정수)
    if (!isValidCount) {
      setErrorText(`${BULK_COUNT_MIN}부터 ${BULK_COUNT_MAX} 사이의 숫자를 입력해 주세요.`)
      return
    }
    setErrorText(null)
    setAlreadyRunning(false)
    setFailureText(null)
    // 3. 시작 요청. 성공하면 접수 토스트, 이미 실행 중(409)이면 안내 띠, 그 밖은 오류 문장
    start.mutate(
      { count },
      {
        onSuccess: () =>
          toast.success(
            '일괄 번역을 시작했습니다. 진행 상황은 이 화면에서 5초마다 새로 확인합니다.',
          ),
        onError: (error) => {
          const code = isApiError(error) ? error.errorCode : null
          if (code === 'LLM_BULK_RUNNING') {
            // 오류 문장 대신 진행 패널을 다시 불러와 실행 중인 일괄을 바로 보여 준다
            setAlreadyRunning(true)
            void invalidate()
            return
          }
          setFailureText(isApiError(error) ? error.userMessage : getErrorMessage(null))
        },
      },
    )
  }

  return (
    <div className="grid gap-4">
      <form onSubmit={handleSubmit} noValidate aria-busy={start.isPending} className="grid gap-3">
        <div className="grid gap-1.5">
          <Label htmlFor="llm-bulk-count">찜 많은 순 상위 몇 편을 번역할까요</Label>
          <div className="flex flex-wrap items-center gap-2">
            <Input
              id="llm-bulk-count"
              type="number"
              inputMode="numeric"
              min={BULK_COUNT_MIN}
              max={BULK_COUNT_MAX}
              value={countText}
              onChange={(event) => setCountText(event.target.value)}
              aria-invalid={errorText ? true : undefined}
              aria-describedby={errorText ? `${COUNT_HELP_ID} ${COUNT_ERROR_ID}` : COUNT_HELP_ID}
              className="w-24"
            />
            <span className="text-sm">
              편 ({BULK_COUNT_MIN}~{BULK_COUNT_MAX})
            </span>
          </div>
          <p id={COUNT_HELP_ID} className="text-xs text-muted-foreground">
            이미 초안이 있는 작품은 건너뛰고 다음 작품으로 채웁니다.
          </p>
          {errorText && (
            <p id={COUNT_ERROR_ID} className="text-sm text-destructive">
              {errorText}
            </p>
          )}
          {showsRemainingWarning && (
            <p className="text-sm text-attention-fg">
              <span className="rounded bg-attention-bg px-1.5 py-0.5">
                오늘 남은 호출은 {remaining}회라 도중에 멈출 수 있습니다.
              </span>
            </p>
          )}
        </div>

        <div className="flex flex-wrap items-center gap-x-3 gap-y-2">
          <LlmWriteButton
            type="submit"
            blockedReason={blockedReason}
            blockedReasonId={BULK_BLOCKED_REASON_ID}
            isBusy={start.isPending}
          >
            {start.isPending ? '접수 중…' : '일괄 번역 시작'}
          </LlmWriteButton>
          {blockedReason && (
            <p id={BULK_BLOCKED_REASON_ID} className="text-sm text-muted-foreground">
              {blockedReason}
            </p>
          )}
          {/* 실행 직전에 남은 횟수를 눈앞에서 확인하게 한다(현황 상자의 막대와 같은 값). 현황을 못 불러왔으면 숨긴다 */}
          {summary && (
            <OverviewUsageMeter usedToday={summary.usedToday} dailyLimit={summary.dailyLimit} />
          )}
        </div>

        <p className="text-xs text-muted-foreground">
          한 편에 약 15~30초가 걸려 100편이면 30~60분입니다(구글 서버 상황에 따라 더 걸릴 수
          있습니다). 일괄 번역은 한 번에 하나만 실행됩니다. 창을 닫아도 서버에서 계속 진행됩니다.
        </p>

        {alreadyRunning && (
          <OverviewNoticeBand tone="info" role="status">
            이미 실행 중인 일괄 번역을 보여 드립니다.
          </OverviewNoticeBand>
        )}
        {failureText && (
          <OverviewNoticeBand tone="danger" role="alert">
            {failureText}
          </OverviewNoticeBand>
        )}
      </form>

      {!isProgressUnavailable && (
        <OverviewBulkProgress
          bulkRun={bulkRun}
          dailyLimit={summary?.dailyLimit ?? null}
          lastCheckedAt={lastCheckedAt}
          announcement={announcement}
        />
      )}
    </div>
  )
}
