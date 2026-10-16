import { CheckIcon } from 'lucide-react'
import { POPULARITY_PERIOD_OPTIONS } from '@/features/home/homeDisplay'
import type { HomePopularityPeriod } from '@/features/home/mockTypes'
import { cn } from '@/lib/utils'

interface PeriodToggleProps {
  value: HomePopularityPeriod // 지금 고른 기간
  onChange: (period: HomePopularityPeriod) => void // 기간을 바꿨을 때
}

/**
 * 인기 순위의 기간(오늘 / 최근 7일)을 고르는 두 칸짜리 전환 버튼이다(홈 1차안 피드백).
 *
 * 서비스 탭(알약 모양, 하늘색 채움)과 다르게 보이게 한 이유: 같은 줄에 "기간"과 "서비스" 두 가지 고르기가 있어,
 * 모양이 같으면 한 묶음으로 읽힌다. 그래서 회색 홈(bg-muted) 안에 두 칸이 붙은 "구간 전환" 모양으로 둔다.
 * 고른 칸은 바탕이 떠오르는 것(bg-background + 그림자)에 더해 체크 아이콘·굵은 글자가 붙는다(색만으로 구분하지 않음).
 * 눌림 상태는 aria-pressed로 낭독기에 알린다(랭킹 화면의 "순위 기준" 버튼과 같은 방식).
 * 높이: 홈 p-1(4px) x 2 + 칸 h-9(36px) = 44px로 손가락 터치 크기 기준에 맞춘다.
 */
export function PeriodToggle({ value, onChange }: PeriodToggleProps) {
  return (
    <div
      role="group"
      aria-label="순위 기간"
      className="inline-flex w-fit items-center rounded-lg border border-border bg-muted p-1"
    >
      {POPULARITY_PERIOD_OPTIONS.map((option) => {
        const isSelected = option.value === value
        return (
          <button
            key={option.value}
            type="button"
            aria-pressed={isSelected}
            onClick={() => onChange(option.value)}
            className={cn(
              'inline-flex h-9 items-center gap-1 rounded-md px-3 text-sm whitespace-nowrap focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ring',
              isSelected
                ? 'bg-background font-bold text-foreground shadow-sm'
                : 'font-medium text-muted-foreground hover:text-foreground',
            )}
          >
            {isSelected && <CheckIcon aria-hidden="true" strokeWidth={3} className="size-3.5" />}
            {option.label}
          </button>
        )
      })}
    </div>
  )
}
