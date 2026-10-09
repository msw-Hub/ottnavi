import { CheckIcon, CircleQuestionMarkIcon, XIcon, type LucideIcon } from 'lucide-react'
import { DataInsufficientTag } from '@/components/common/DataInsufficientTag'
import { AVAILABILITY_LABELS, type AvailabilityStatus } from '@/components/common/displayTypes'
import type { ServiceStatusEntry } from '@/components/common/ServiceStatusRow'
import { cn } from '@/lib/utils'

// 상태별 모양. ProviderStatusBadge와 같은 규칙(아이콘·테두리 모양·색 + 글자)을 줄 안에 들어가는 작은 크기로 옮겼다
const CHIP_APPEARANCE: Record<AvailabilityStatus, { icon: LucideIcon; className: string }> = {
  // 있음: 초록으로 꽉 채운 칩 + 체크
  AVAILABLE: {
    icon: CheckIcon,
    className:
      'border-solid border-status-available-border bg-status-available-bg text-status-available',
  },
  // 없음: 바탕 없는 실선 테두리 + X
  NOT_AVAILABLE: {
    icon: XIcon,
    className:
      'border-solid border-status-unavailable-border bg-status-unavailable-bg text-status-unavailable',
  },
  // 모름: 바탕 없는 점선 테두리 + 물음표
  UNKNOWN: {
    icon: CircleQuestionMarkIcon,
    className:
      'border-dashed border-status-unknown-border bg-status-unknown-bg text-status-unknown',
  },
}

// 묶음 순서(가장 알고 싶은 "볼 수 있음"이 위)와 제목. 괄호 안은 칩에 적힌 짧은 말과 같아 두 표기가 서로 이어진다
const GROUP_ORDER: AvailabilityStatus[] = ['AVAILABLE', 'NOT_AVAILABLE', 'UNKNOWN']
const GROUP_TITLES: Record<AvailabilityStatus, string> = {
  AVAILABLE: '볼 수 있음(있음)',
  NOT_AVAILABLE: '볼 수 없음(없음)',
  UNKNOWN: '확인 안 됨(모름)',
}

interface ServiceStatusChipsProps {
  entries: ServiceStatusEntry[] // 서비스별 제공 상태(보통 7개). 받은 순서대로 그린다
  label: string // 이 목록이 어떤 작품의 것인지(화면 낭독기용 이름, 예: "오징어 게임 서비스별 제공 상태")
}

/**
 * 찜 목록 카드 안에서 서비스 7곳의 제공 상태를 상태별 묶음(볼 수 있음 / 볼 수 없음 / 확인 안 됨)으로 나눠 작은 칩으로 보여 준다(SCR-08).
 *
 * 검색 결과 카드의 AvailableServiceBadges는 "있음"만 보여 주지만, 찜 목록은 "어느 서비스로 갈아타야 볼 수 있는지"를 가늠해야 하므로
 * 없음·모름까지 모두 보인다. 묶음 제목이 상태를 글자로 먼저 알리고, 칩은 색만으로 구분하지 않도록 서비스 이름 + 아이콘 + 글자(있음/없음/모름)와
 * 테두리 모양(꽉 채움 / 실선 / 점선)을 함께 쓴다.
 * 데이터 부족(쿠팡플레이) 서비스는 이름 옆에 "데이터 부족"을 붙여 "모름"이 정보 부족 때문일 수 있음을 알린다.
 */
export function ServiceStatusChips({ entries, label }: ServiceStatusChipsProps) {
  return (
    // 상태별 묶음: 좁은 폭(sm 미만)에서는 세로로 쌓고, 넓은 폭에서는 카드 전체 폭을 써서 가로 3칸으로 나란히 둔다.
    // 빈 묶음은 그려지지 않아 칸이 남을 수 있으나 왼쪽부터 채워져 어색하지 않다.
    // 묶음 제목이 상태를 글자로 먼저 알려 주고, 칩은 그 안에서 다시 상태를 아이콘·글자·테두리로 알린다
    <div role="group" aria-label={label} className="grid items-start gap-3 sm:grid-cols-3">
      {GROUP_ORDER.map((status) => {
        const groupEntries = entries.filter((entry) => entry.status === status)
        // 빈 묶음은 생략한다(예: 모름인 서비스가 없으면 "확인 안 됨" 제목도 보이지 않는다)
        if (groupEntries.length === 0) return null
        return (
          <div key={status} className="grid gap-1.5">
            <h4 className="text-sm font-semibold text-foreground">
              {GROUP_TITLES[status]}
              <span className="ml-1 font-normal text-muted-foreground tabular-nums">
                {groupEntries.length}곳
              </span>
            </h4>
            <ul
              aria-label={`${label} - ${GROUP_TITLES[status]}`}
              className="flex flex-wrap gap-1.5"
            >
              {groupEntries.map(({ service }) => {
                const { icon: Icon, className } = CHIP_APPEARANCE[status]
                return (
                  <li
                    key={service.ottServiceId}
                    className={cn(
                      'inline-flex items-center gap-1 rounded-full border-2 py-px pr-2 pl-1.5 text-xs leading-5 font-semibold whitespace-nowrap',
                      className,
                    )}
                  >
                    <Icon aria-hidden="true" strokeWidth={3} className="size-3 shrink-0" />
                    {service.name}
                    <span className="font-bold">{AVAILABILITY_LABELS[status]}</span>
                    {service.dataQuality === 'INSUFFICIENT' && <DataInsufficientTag />}
                  </li>
                )
              })}
            </ul>
          </div>
        )
      })}
    </div>
  )
}
