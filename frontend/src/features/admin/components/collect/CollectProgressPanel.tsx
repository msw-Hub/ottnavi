import {
  CircleCheckIcon,
  CircleIcon,
  CirclePauseIcon,
  CircleXIcon,
  LoaderCircleIcon,
  type LucideIcon,
} from 'lucide-react'
import { cn } from 'cn'
import type { CollectProgress, CollectStepProgress, CollectStepStatus } from '@/api/generated/model'
import {
  CANDIDATE_PERCENT_NOTE,
  COLLECT_STEP_LABELS,
  COLLECT_STEP_STATUS_LABELS,
  describeStepProgress,
  formatHaltedStepLine,
  formatProgressHeadline,
} from '@/features/admin/collectRules'

// 단계 상태별 아이콘과 글자 모양. 다섯 상태가 아이콘 모양과 글자에서 모두 달라 색 없이도 구분된다(색만으로 나누지 않는다)
const STEP_STATUS_APPEARANCE: Record<
  CollectStepStatus,
  { icon: LucideIcon; className: string; iconClassName?: string }
> = {
  PENDING: { icon: CircleIcon, className: 'text-muted-foreground' }, // 빈 원 + 회색
  // 회전하는 원 + info 색 굵은 글자. 움직임 줄이기 설정이면 회전을 멈춘다(모양과 글자로 여전히 읽힌다)
  RUNNING: {
    icon: LoaderCircleIcon,
    className: 'font-semibold text-info',
    iconClassName: 'animate-spin motion-reduce:animate-none',
  },
  COMPLETED: { icon: CircleCheckIcon, className: 'text-foreground' }, // 체크 원
  FAILED: { icon: CircleXIcon, className: 'font-semibold text-destructive' }, // X 원 + 빨강
  // 일시정지 원 + 회색 점선 테두리(실행 상태 배지의 "중단됨"과 같은 모양 언어)
  STOPPED: {
    icon: CirclePauseIcon,
    className:
      'rounded-md border border-dashed border-muted-foreground/60 px-1 text-muted-foreground',
  },
}

// 단계 상태 한 칸(아이콘 + 글자)
function StepStatusLabel({ status }: { status: CollectStepStatus }) {
  const { icon: Icon, className, iconClassName } = STEP_STATUS_APPEARANCE[status]
  return (
    <span className={cn('inline-flex w-fit items-center gap-1.5', className)}>
      <Icon aria-hidden="true" className={cn('size-4 shrink-0', iconClassName)} />
      {COLLECT_STEP_STATUS_LABELS[status]}
    </span>
  )
}

// 단계 한 줄 오른쪽의 진행 표시(막대·퍼센트·건수 또는 글자). 무엇을 보일지는 collectRules.describeStepProgress가 정한다
function StepProgressValue({ stepProgress }: { stepProgress: CollectStepProgress }) {
  const display = describeStepProgress(stepProgress)
  if (display.kind === 'NONE') {
    // 대기: 값이 없다는 표시. 낭독기는 "—"를 읽지 않거나 이상하게 읽어 뜻을 글자로 대신 준다
    return (
      <span className="text-muted-foreground">
        <span aria-hidden="true">—</span>
        <span className="sr-only">아직 시작 전</span>
      </span>
    )
  }
  if (display.kind === 'TEXT') {
    return <span className="text-muted-foreground tabular-nums">{display.text}</span>
  }
  const label = COLLECT_STEP_LABELS[stepProgress.step]
  return (
    <span className="flex flex-wrap items-center gap-x-2 gap-y-1 tabular-nums">
      {display.detailText && <span>{display.detailText}</span>}
      {/* 진행 막대: 최대 12rem, 높이 6px. 채움 너비만 퍼센트로 정한다(색은 토큰 bg-muted 바탕, bg-primary 채움).
          후보 단계는 근사값이라 막대에 마우스를 올리면 title로 알린다(화면 아래 각주에도 같은 글자가 있다) */}
      <span
        role="progressbar"
        aria-label={`${label} 진행률`}
        aria-valuenow={display.percent}
        aria-valuemin={0}
        aria-valuemax={100}
        aria-valuetext={display.percentText}
        title={display.isApproximate ? CANDIDATE_PERCENT_NOTE : undefined}
        className="block h-1.5 w-full max-w-48 min-w-24 flex-1 overflow-hidden rounded-full bg-muted"
      >
        <span
          className="block h-full rounded-full bg-primary"
          style={{ width: `${display.percent}%` }}
        />
      </span>
      <span className="font-semibold">{display.percentText}</span>
    </span>
  )
}

interface CollectProgressPanelProps {
  progress: CollectProgress // 실행 중인 실행의 단계별 진행(서버 값 그대로)
}

/**
 * 실행 중인 수집의 단계별 진행 띠다(SCR-16 실행 이력, 표의 서브 행·카드 안에 들어간다). 보여 주기만 하고 상태를 갖지 않는다.
 *
 * 첫 줄: 회전 아이콘 + "2/5 단계 · 지금: 영화 상세"(진행 중인 단계가 없으면 "다음 단계 준비 중") + 오른쪽 "15초마다 자동 갱신".
 * 둘째 줄부터: 단계 목록(실행 순서대로 ol) 한 줄에 [상태 아이콘 + 상태 글자] [단계 이름] [진행 표시].
 * 후보 단계 퍼센트가 하나라도 보이면 맨 아래에 "근사값" 각주를 단다.
 *
 * aria-live를 주지 않는 이유: 15초마다 값이 바뀌는데 그때마다 낭독기가 단계 목록 전체를 읽으면 다른 일을 할 수 없다.
 * 운영자가 필요할 때 이 띠로 와서 읽으면 된다.
 * 색은 info 토큰(옅은 테두리 border-info/40 + bg-info-bg)으로, 실패 사유 띠(destructive)와 같은 모양에 색만 다르다.
 */
export function CollectProgressPanel({ progress }: CollectProgressPanelProps) {
  // 근사값 각주를 달지: 막대로 보이는(진행 중이고 퍼센트가 있는) 후보 단계가 있을 때만
  const hasApproximate = progress.steps.some((stepProgress) => {
    const display = describeStepProgress(stepProgress)
    return display.kind === 'BAR' && display.isApproximate
  })

  return (
    <div className="grid gap-2 rounded-lg border border-info/40 bg-info-bg px-3 py-2 text-sm whitespace-normal">
      <div className="flex flex-wrap items-center justify-between gap-x-3 gap-y-1">
        <p className="flex items-center gap-2 font-semibold text-info">
          <LoaderCircleIcon
            aria-hidden="true"
            className="size-4 shrink-0 animate-spin motion-reduce:animate-none"
          />
          {formatProgressHeadline(progress)}
        </p>
        <p className="text-xs text-muted-foreground">15초마다 자동 갱신</p>
      </div>
      {/* 넓은 화면은 세 칸(상태 · 단계 이름 · 진행)을 열로 맞추고, 좁은 화면은 상태·이름 아래로 진행을 내린다 */}
      <ol aria-label="단계별 진행" className="grid gap-1.5">
        {progress.steps.map((stepProgress) => (
          <li
            key={stepProgress.step}
            className="grid grid-cols-[5.5rem_1fr] items-center gap-x-3 gap-y-1 sm:grid-cols-[5.5rem_8rem_1fr]"
          >
            <StepStatusLabel status={stepProgress.status} />
            <span>{COLLECT_STEP_LABELS[stepProgress.step]}</span>
            {/* 좁은 화면에서 대기 단계의 "—"는 줄 하나를 따로 차지해 목록만 길어진다. 상태 글자 "대기"가 이미 같은 뜻이라 좁은 화면에서만 숨긴다 */}
            <span
              className={cn(
                'col-span-2 sm:col-span-1',
                stepProgress.status === 'PENDING' && 'max-sm:hidden',
              )}
            >
              <StepProgressValue stepProgress={stepProgress} />
            </span>
          </li>
        ))}
      </ol>
      {hasApproximate && (
        <p className="text-xs text-muted-foreground">
          <span aria-hidden="true">* </span>
          {CANDIDATE_PERCENT_NOTE}
        </p>
      )}
    </div>
  )
}

interface CollectHaltedStepProps {
  progress: CollectProgress // 실패·중단된 실행의 단계별 진행
  isStopped: boolean // 중단됨이면 true(일시정지 아이콘), 실패면 false(X 아이콘)
}

/**
 * 실패·중단된 실행이 어느 단계에서 멈췄는지 한 줄로 알린다: "2/5 단계에서 멈춤 · 영화 상세".
 * 실패 행에서는 실패 사유 띠 바로 앞에 놓인다. 끝난 실행의 단계 목록 펼침은 만들지 않는다(이 한 줄이면 다시 실행할지 판단하기에 충분하다).
 * 아이콘은 실행 상태 배지와 같은 모양(실패 X, 중단됨 일시정지)이고 글자가 함께 있어 색에 기대지 않는다.
 */
export function CollectHaltedStep({ progress, isStopped }: CollectHaltedStepProps) {
  const Icon = isStopped ? CirclePauseIcon : CircleXIcon
  return (
    <p className="flex items-center gap-2 text-sm font-semibold whitespace-normal">
      <Icon
        aria-hidden="true"
        className={cn('size-4 shrink-0', isStopped ? 'text-muted-foreground' : 'text-destructive')}
      />
      {formatHaltedStepLine(progress)}
    </p>
  )
}
