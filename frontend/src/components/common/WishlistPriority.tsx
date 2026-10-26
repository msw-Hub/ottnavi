import { useId } from 'react'
import { BookmarkIcon, ClockIcon, StarIcon, type LucideIcon } from 'lucide-react'
import {
  WISHLIST_PRIORITY_DESCRIPTIONS,
  WISHLIST_PRIORITY_LABELS,
  WISHLIST_PRIORITY_OPTIONS,
  type WishlistPriority,
} from '@/components/common/displayTypes'
import { cn } from '@/lib/utils'

/*
 * 찜 우선순위(꼭 볼 작품 / 보고 싶음 / 여유될 때)를 화면에 보이는 공통 조각들이다.
 * 찜 버튼의 설정 상자(상세·검색), 찜 목록 카드, 찜 목록 요약, 찜 추가 팝업이 같은 모양을 쓰도록 한곳에 모았다.
 *
 * 왜 단계마다 모양을 다르게 하나: 이전에는 세 단계가 같은 모양의 글자로만 보여 "보고 싶음"과 "꼭 볼 작품"의 차이를 느끼기 어려웠다.
 * 계산은 꼭 볼 작품을 가장 먼저 챙기므로(PRD 5.4·11절 35번) 무게가 큰 단계일수록 눈에 띄게 한다.
 * 색만으로 구분하지 않는다(frontend.md 화면 표시 규칙): 아이콘(별 / 북마크 / 시계) + 글자 + 모양(꽉 채움 / 실선 / 점선)을 함께 쓴다.
 * 흑백으로 봐도 별·북마크·시계와 채움·실선·점선으로 구분된다. 색 값은 styles/tokens.css의 --priority-* 토큰만 쓴다.
 */

// 단계별 아이콘. 별(가장 중요) → 북마크(담아 둠) → 시계(시간 날 때)
const PRIORITY_ICONS: Record<WishlistPriority, LucideIcon> = {
  MUST: StarIcon,
  WANT: BookmarkIcon,
  MAYBE: ClockIcon,
}

// 단계별 색·테두리 모양(선택된 칸·배지 공통). 테두리 굵기는 쓰는 곳에서 정한다
const PRIORITY_TONE: Record<WishlistPriority, string> = {
  MUST: 'border-solid border-priority-must-border bg-priority-must-bg text-priority-must-fg',
  WANT: 'border-solid border-priority-want-border bg-priority-want-bg text-priority-want-fg',
  MAYBE: 'border-dashed border-priority-maybe-border bg-priority-maybe-bg text-priority-maybe-fg',
}

interface PriorityIconProps {
  priority: WishlistPriority // 어느 단계의 아이콘인지
  className?: string // 크기 등 덧붙일 클래스
}

/** 우선순위 아이콘 하나. 장식이라 낭독기에서는 숨기고, 단계 이름은 옆 글자가 알린다. 꼭 볼 작품의 별만 속을 채워 무게를 더한다 */
export function PriorityIcon({ priority, className }: PriorityIconProps) {
  const Icon = PRIORITY_ICONS[priority]
  return (
    <Icon
      aria-hidden="true"
      className={cn('size-4 shrink-0', priority === 'MUST' && 'fill-current', className)}
    />
  )
}

interface PriorityBadgeProps {
  priority: WishlistPriority // 보일 단계
  count?: number // 주면 "꼭 볼 작품 3개"처럼 개수를 붙인다(찜 목록 요약용)
  className?: string
}

/** 우선순위 알약 배지(읽기 전용). 아이콘 + 단계 이름(+ 개수)을 단계별 모양으로 보인다 */
export function PriorityBadge({ priority, count, className }: PriorityBadgeProps) {
  return (
    <span
      className={cn(
        'inline-flex items-center gap-1 rounded-full border-2 px-2.5 py-0.5 text-sm font-bold whitespace-nowrap tabular-nums',
        PRIORITY_TONE[priority],
        className,
      )}
    >
      <PriorityIcon priority={priority} className="size-3.5" />
      {WISHLIST_PRIORITY_LABELS[priority]}
      {count !== undefined && <span className="font-extrabold">{count}개</span>}
    </span>
  )
}

interface PriorityChoiceProps {
  value: WishlistPriority // 지금 고른 단계
  onChange: (priority: WishlistPriority) => void // 다른 단계를 골랐을 때
  legend: string // 묶음 이름(낭독기가 읽는 그룹 이름). 예: "우선순위", "기생충 우선순위"
  disabled?: boolean // 요청 중이면 고르지 못하게 막는다
}

/**
 * 우선순위 라디오 카드 3장(세로). 단계 이름 아래에 그 단계가 계산에서 어떤 대접을 받는지 한 줄 설명을 붙인다.
 * 찜 버튼을 누르면 열리는 설정 상자(상세·검색 화면)에 쓴다.
 *
 * - 네이티브 radio를 그대로 보여 준다: 화살표·Tab 키 조작과 낭독기 지원을 브라우저에 맡기고, 동그라미 채움으로도 선택이 보인다(색만으로 알리지 않음).
 * - 카드 전체가 label이라 어디를 눌러도 고를 수 있다. 터치 높이는 44px 이상이다.
 * - 라디오의 이름은 단계 이름만(aria-labelledby)으로 하고 설명은 aria-describedby로 따로 읽힌다.
 *   label이 설명까지 감싸고 있어서 그냥 두면 이름이 "꼭 볼 작품 계산이 가장 먼저…"처럼 길어지기 때문이다.
 */
export function PriorityRadioCards({ value, onChange, legend, disabled }: PriorityChoiceProps) {
  const baseId = useId()
  return (
    <fieldset className="grid gap-1.5" disabled={disabled}>
      <legend className="px-1 pb-1 text-xs text-muted-foreground">{legend}</legend>
      {WISHLIST_PRIORITY_OPTIONS.map((option) => {
        const isChecked = value === option.value
        const nameId = `${baseId}-${option.value}-name`
        const descriptionId = `${baseId}-${option.value}-description`
        return (
          <label
            key={option.value}
            className={cn(
              'grid min-h-11 cursor-pointer gap-0.5 rounded-lg border-2 px-2.5 py-2 transition-colors motion-reduce:transition-none has-focus-visible:outline-2 has-focus-visible:outline-offset-2 has-focus-visible:outline-ring has-disabled:cursor-not-allowed has-disabled:opacity-60',
              isChecked ? PRIORITY_TONE[option.value] : 'border-border bg-card hover:bg-muted',
            )}
          >
            <span className="flex items-center gap-2 text-sm font-bold">
              <input
                type="radio"
                name={`${baseId}-priority`}
                value={option.value}
                checked={isChecked}
                onChange={() => onChange(option.value)}
                aria-labelledby={nameId}
                aria-describedby={descriptionId}
                className="size-4 shrink-0 accent-current"
              />
              <PriorityIcon priority={option.value} />
              <span id={nameId}>{option.label}</span>
            </span>
            {/* 설명: 선택된 카드는 단계 글자색, 나머지는 흐린 글자. 왼쪽 여백(pl-6)으로 단계 이름과 줄을 맞춘다 */}
            <span
              id={descriptionId}
              className={cn(
                'pl-6 text-xs leading-snug break-keep',
                !isChecked && 'text-muted-foreground',
              )}
            >
              {WISHLIST_PRIORITY_DESCRIPTIONS[option.value]}
            </span>
          </label>
        )
      })}
    </fieldset>
  )
}

/**
 * 우선순위 3칸 세그먼트(가로). 찜 목록 카드처럼 폭이 좁은 자리(375px에서 약 220px)에 쓴다.
 * 설명 문장은 넣지 않고(카드 높이가 커진다) 목록 위 우선순위 요약이 한 번만 설명한다.
 *
 * 선택된 칸은 단계 색으로 채우고 테두리를 두껍게(2px), 글자를 굵게 한다. 선택 안 된 칸은 1px 테두리·흐린 글자다.
 * 색을 못 보아도 채움·굵기 차이와 아이콘·글자로 어느 칸이 골라졌는지 알 수 있다.
 * 라디오 동그라미는 숨기고(sr-only) 칸 전체가 label이다. 키보드 포커스는 칸 바깥 윤곽선으로 보인다.
 */
export function PrioritySegmented({ value, onChange, legend, disabled }: PriorityChoiceProps) {
  const baseId = useId()
  return (
    <fieldset className="min-w-0" disabled={disabled}>
      <legend className="sr-only">{legend}</legend>
      <div className="grid grid-cols-3 gap-1">
        {WISHLIST_PRIORITY_OPTIONS.map((option) => {
          const isChecked = value === option.value
          return (
            <label
              key={option.value}
              className={cn(
                'flex min-h-11 cursor-pointer flex-col items-center justify-center gap-0.5 rounded-md px-1 py-1 text-center text-xs leading-tight break-keep transition-colors motion-reduce:transition-none has-focus-visible:outline-2 has-focus-visible:outline-offset-2 has-focus-visible:outline-ring has-disabled:cursor-not-allowed has-disabled:opacity-60',
                isChecked
                  ? cn('border-2 font-bold', PRIORITY_TONE[option.value])
                  : 'border border-border text-muted-foreground hover:bg-muted',
              )}
            >
              <input
                type="radio"
                name={`${baseId}-priority`}
                value={option.value}
                checked={isChecked}
                onChange={() => onChange(option.value)}
                className="sr-only"
              />
              <PriorityIcon priority={option.value} />
              {option.label}
            </label>
          )
        })}
      </div>
    </fieldset>
  )
}

/**
 * 세 단계의 뜻을 한눈에 보는 안내 목록(읽기 전용). 찜 추가 팝업처럼 아직 고를 수 없는 자리에서 단계의 차이를 알린다.
 * highlight를 주면 그 단계 줄에 "지금" 표시를 붙인다(예: 기본값으로 담긴 "보고 싶음").
 */
export function PriorityGuideList({ highlight }: { highlight?: WishlistPriority }) {
  return (
    <ul className="grid gap-1.5" aria-label="우선순위 안내">
      {WISHLIST_PRIORITY_OPTIONS.map((option) => (
        <li key={option.value} className="flex items-start gap-2 text-sm">
          {/* 배지 폭을 고정(6.5rem)해 세 줄의 설명이 같은 세로선에서 시작하게 한다(폭이 다르면 설명이 들쭉날쭉해 읽기 어렵다) */}
          <PriorityBadge
            priority={option.value}
            className="w-[6.5rem] shrink-0 justify-center text-xs"
          />
          <span className="pt-0.5 text-xs leading-snug break-keep text-muted-foreground">
            {WISHLIST_PRIORITY_DESCRIPTIONS[option.value]}
            {highlight === option.value && (
              <strong className="ml-1 font-bold text-foreground">(지금 이 단계)</strong>
            )}
          </span>
        </li>
      ))}
    </ul>
  )
}
