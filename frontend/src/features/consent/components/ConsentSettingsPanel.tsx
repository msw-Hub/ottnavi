import { useId } from 'react'
import { CheckIcon, CircleDashedIcon, XIcon, type LucideIcon } from 'lucide-react'
import { cn } from 'cn'
import { Button } from '@/components/ui/button'

// 방문 통계 동의 상태. granted = 동의함, denied = 거부함(또는 철회함), unset = 아직 고르지 않음
export type ConsentStatus = 'granted' | 'denied' | 'unset'

interface ConsentSettingsPanelProps {
  status: ConsentStatus // 지금 저장된 동의 상태. 읽고 저장하는 일은 부르는 쪽(frontend-dev)이 한다
  onAccept: () => void // [동의]를 눌렀을 때
  onReject: () => void // [거부]·[동의 철회]를 눌렀을 때
  className?: string // 놓이는 자리(처리방침 화면, 푸터에서 여는 팝업 등)에 맞춘 여백을 덧붙일 때만 쓴다
}

/*
 * 상태별 표시. 색만으로 나누지 않고 ① 글자 ② 아이콘 ③ 테두리 모양을 함께 바꾼다(제공 상태 배지와 같은 규칙).
 * - 동의함: 초록으로 꽉 채운 알약 + 체크. success 토큰(초록 0.8 위 남색 0.15, 대비 약 9:1)
 * - 거부함: 바탕 없는 실선 테두리 + X. 제공 "없음"과 같은 슬레이트 토큰
 * - 아직 선택 안 함: 옅은 바탕 + 점선 테두리 + 점선 원. 제공 "모름"과 같은 중립 토큰
 * 흑백으로 봐도 채움 / 실선 / 점선으로 구분된다.
 */
const STATUS_APPEARANCE: Record<
  ConsentStatus,
  { label: string; icon: LucideIcon; className: string }
> = {
  granted: {
    label: '동의함',
    icon: CheckIcon,
    className: 'border-solid border-success-bg bg-success-bg text-success',
  },
  denied: {
    label: '거부함',
    icon: XIcon,
    className:
      'border-solid border-status-unavailable-border bg-status-unavailable-bg text-status-unavailable',
  },
  unset: {
    label: '아직 선택 안 함',
    icon: CircleDashedIcon,
    className:
      'border-dashed border-status-unknown-border bg-status-unknown-bg text-status-unknown',
  },
}

// 두 버튼의 모양. 안내 띠(ConsentBanner)와 같이 같은 크기·같은 무게로 둬 어느 한쪽으로 유도하지 않는다(h-11 = 44px 터치 대상)
const CHOICE_BUTTON_CLASS = 'h-11 min-w-24 flex-1 text-sm font-semibold sm:flex-none'

/**
 * 처리방침 화면과 푸터 링크에서 여는 "방문 통계 설정" 카드다(표현만 한다).
 *
 * 지금 상태를 글자 + 아이콘으로 보여 주고, [동의]와 [거부]로 언제든 바꿀 수 있게 한다(사용자 결정: 언제든 철회·변경 가능).
 * 동의한 상태에서는 거부 버튼 글자를 "동의 철회"로 바꿔, 이미 준 동의를 거두는 버튼이라는 것이 읽히게 한다.
 *
 * 왜 지금 상태의 버튼을 비활성(disabled)으로 만들지 않나: 비활성 버튼은 흐려져(opacity 50%) 대비 기준을 못 맞추고,
 * 키보드 Tab에서도 빠져 "왜 못 누르지"가 된다. 상태는 위의 배지가 알려 주고, 같은 선택을 다시 눌러도 결과가 같다.
 *
 * 상태 배지는 aria-live="polite"로 둬서, 버튼을 누른 뒤 상태가 바뀌면 화면 낭독기가 새 상태를 한 번 읽는다.
 */
export function ConsentSettingsPanel({
  status,
  onAccept,
  onReject,
  className,
}: ConsentSettingsPanelProps) {
  // 제목 id. section의 이름(aria-labelledby)으로 쓴다
  const titleId = useId()
  const { label, icon: Icon, className: statusClassName } = STATUS_APPEARANCE[status]

  return (
    <section
      aria-labelledby={titleId}
      className={cn(
        'grid gap-4 rounded-xl border bg-card p-4 text-card-foreground sm:p-5',
        className,
      )}
    >
      <div className="grid gap-1.5 break-keep">
        <h2 id={titleId} className="text-base font-semibold">
          방문 통계 설정
        </h2>
        <p className="text-sm leading-6 text-muted-foreground">
          방문자 수와 이용 현황을 파악하려고 Google Analytics를 씁니다. 동의하기 전에는 불러오지
          않고, 광고에는 쓰지 않습니다. 거부해도 서비스 이용에 제한이 없으며, 거부하시면 6개월 뒤에
          한 번 다시 여쭙니다.
        </p>
      </div>

      {/* 지금 상태. "현재 상태:" 글자를 앞에 둬 배지만 떼어 읽어도 무엇의 상태인지 알 수 있게 한다 */}
      <p aria-live="polite" className="flex flex-wrap items-center gap-2 text-sm">
        <span className="text-muted-foreground">현재 상태</span>
        <span
          className={cn(
            'inline-flex items-center gap-1.25 rounded-full border-2 py-0.75 pr-2.75 pl-2 leading-5 font-bold whitespace-nowrap',
            statusClassName,
          )}
        >
          {/* 아이콘은 글자와 같은 뜻이라 낭독기에는 숨긴다. 선 굵기 3은 제공 상태 배지와 맞춘 값이다 */}
          <Icon aria-hidden="true" strokeWidth={3} className="size-4.25 shrink-0" />
          <span>{label}</span>
        </span>
      </p>

      {/* 버튼 순서는 안내 띠와 같게 거부 → 동의로 둔다. 화면마다 위치가 바뀌면 잘못 누르기 쉽다 */}
      <div className="flex gap-2">
        <Button type="button" variant="outline" className={CHOICE_BUTTON_CLASS} onClick={onReject}>
          {status === 'granted' ? '동의 철회' : '거부'}
        </Button>
        <Button type="button" variant="outline" className={CHOICE_BUTTON_CLASS} onClick={onAccept}>
          동의
        </Button>
      </div>
    </section>
  )
}
