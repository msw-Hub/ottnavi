import type { ReactNode } from 'react'
import { CheckCircle2Icon, TriangleAlertIcon } from 'lucide-react'
import { cn } from 'cn'
import type { OverviewTranslateSummary } from '@/api/generated/model'
import { AdminStatusPill } from '@/features/admin/components/AdminStatusPill'
import { OverviewNoticeBand } from '@/features/admin/components/overview/OverviewNoticeBand'
import { getUsageLevel } from '@/features/admin/overviewRules'

interface OverviewSummaryPanelProps {
  summary: OverviewTranslateSummary // 번역 현황(대기 수, 오늘 사용량, 상한, 모델, 기능 켜짐 여부)
}

// dl 한 칸의 모양: 위에 작은 이름(dt), 아래에 값(dd)
function Stat({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="grid content-start gap-1">
      <dt className="text-xs text-muted-foreground">{label}</dt>
      <dd className="text-sm font-semibold break-keep">{children}</dd>
    </div>
  )
}

/**
 * "번역 현황" 상자다. 번역 대기 수, 오늘 Gemini 호출 사용량(얇은 막대 + 상한 알약), 분당 상한, 쓰는 모델을 보인다.
 *
 * LLM(Gemini)을 쓴다는 사실을 화면에 드러내려고 모델 이름과 "Gemini" 표기를 이 상자에 둔다(사용자 결정).
 * 사용량이 80% 이상이면 "곧 상한", 100%면 "오늘 상한 도달" 알약이 붙는다. 막대 색만으로 알리지 않고 글자도 함께 쓴다.
 * 번역 기능이 꺼져 있으면(서버에 Gemini 키 없음) 경고를 띄운다. 이때도 조회·반영·삭제·되돌리기는 막지 않는다.
 */
export function OverviewSummaryPanel({ summary }: OverviewSummaryPanelProps) {
  const { pendingTotal, usedToday, dailyLimit, perMinuteLimit, model, isEnabled } = summary
  const level = getUsageLevel(usedToday, dailyLimit)
  const ratio = dailyLimit > 0 ? Math.min(1, usedToday / dailyLimit) : 0

  return (
    <div className="grid gap-4">
      {!isEnabled && (
        <OverviewNoticeBand tone="attention" title="번역 기능이 꺼져 있습니다" role="note">
          서버에 Gemini 키가 설정되지 않아 새 초안을 만들 수 없습니다. 이미 만든 초안의
          확인·반영·삭제와 영어로 되돌리기는 할 수 있습니다.
        </OverviewNoticeBand>
      )}

      <dl className="grid grid-cols-2 gap-x-4 gap-y-4 md:grid-cols-4">
        <Stat label="번역 대기">
          <span className="flex flex-wrap items-center gap-2">
            <span className="tabular-nums">{pendingTotal.toLocaleString('ko-KR')}편</span>
            {pendingTotal === 0 && (
              <AdminStatusPill icon={CheckCircle2Icon} label="모두 처리" tone="success" size="sm" />
            )}
          </span>
        </Stat>
        <Stat label="오늘 사용량">
          <span className="flex flex-wrap items-center gap-2">
            <span className="tabular-nums">
              {usedToday.toLocaleString('ko-KR')} / {dailyLimit.toLocaleString('ko-KR')}회
            </span>
            {level === 'nearLimit' && (
              <AdminStatusPill
                icon={TriangleAlertIcon}
                label="곧 상한"
                tone="attention"
                size="sm"
              />
            )}
            {level === 'reached' && (
              <AdminStatusPill
                icon={TriangleAlertIcon}
                label="오늘 상한 도달"
                tone="attention"
                size="sm"
              />
            )}
          </span>
          {/* 얇은 사용량 막대. 값은 위 글자로도 읽히므로 낭독기에는 진행 막대 역할과 숫자만 알린다 */}
          <div
            role="progressbar"
            aria-label="오늘 호출 사용량"
            aria-valuemin={0}
            aria-valuemax={dailyLimit}
            aria-valuenow={Math.min(usedToday, dailyLimit)}
            className="mt-1.5 h-1.5 w-full overflow-hidden rounded-full bg-muted"
          >
            <div
              className={cn(
                'h-full rounded-full',
                level === 'normal' ? 'bg-primary' : 'bg-attention-bg',
              )}
              style={{ width: `${ratio * 100}%` }}
            />
          </div>
        </Stat>
        <Stat label="분당 상한">
          <span className="tabular-nums">{perMinuteLimit.toLocaleString('ko-KR')}회</span>
        </Stat>
        <Stat label="모델">
          <span className="break-all">{model}</span>
          <span className="block text-xs font-normal text-muted-foreground">Gemini(LLM)</span>
        </Stat>
      </dl>

      {/* 초기화 안내 문장은 일괄 번역 버튼 옆(OverviewUsageMeter)에 한 곳만 둔다(사용자 결정 Q5) */}
    </div>
  )
}
