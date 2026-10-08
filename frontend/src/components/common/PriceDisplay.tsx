import { CreditCardIcon, GiftIcon, PencilIcon, TagIcon, type LucideIcon } from 'lucide-react'
import { PRICE_SOURCE_LABELS, type PriceSource } from '@/components/common/displayTypes'
import { formatWon } from '@/lib/format'

// 가격 출처별 아이콘. 글자(PRICE_SOURCE_LABELS)와 함께 보여 출처를 빨리 알아보게 한다(아이콘만으로 뜻을 전하지는 않는다)
const PRICE_SOURCE_ICONS: Record<PriceSource, LucideIcon> = {
  ADMIN: TagIcon, // 요금표의 가격표
  USER_OVERRIDE: PencilIcon, // 내가 고친 값
  ALREADY_PAID: CreditCardIcon, // 이미 결제함
  FREE: GiftIcon, // 무료
}

interface PriceSourceLabelProps {
  priceSource: PriceSource // 금액이 어디서 왔는지(기본 요금 / 사용자 수정 / 이미 결제 / 무료)
}

/** 가격 출처를 아이콘 + 글자 알약으로 보여 준다. PriceDisplay 안에서 쓰고, 표처럼 금액과 따로 놓을 때도 쓴다. */
export function PriceSourceLabel({ priceSource }: PriceSourceLabelProps) {
  const Icon = PRICE_SOURCE_ICONS[priceSource]
  return (
    <span className="inline-flex items-center gap-1.25 rounded-full bg-muted px-2 py-0.5 text-[0.8125rem] font-semibold whitespace-nowrap text-foreground">
      <Icon aria-hidden="true" className="size-3.5 shrink-0" />
      {PRICE_SOURCE_LABELS[priceSource]}
    </span>
  )
}

interface PriceDisplayProps {
  amount: number // 원 단위 금액(계약상 정수). 이미 결제·무료는 보통 0이다
  priceSource: PriceSource // 금액 출처. 금액 옆에 라벨로 보인다
  periodLabel?: string // 금액 뒤에 붙는 기간 표시. 기본 "월"(→ "13,500원/월"). 예: "이번 달"
}

/**
 * 금액과 그 금액의 출처(기본 요금 / 사용자 수정 / 이미 결제 / 무료)를 함께 보여 준다(요금 확인·플랜 결과 화면).
 *
 * 출처를 함께 보이는 이유: 플랜 비용이 요금표 값인지, 사용자가 고친 값인지, 이미 낸 돈이라 0원인지에 따라
 * 결과를 믿을 수 있는 정도가 다르다. 0원만 보이면 "무료"인지 "이미 결제"인지 알 수 없다.
 * 금액 글자는 반드시 lib/format.ts의 formatWon으로 만든다(표기 통일, frontend.md 숫자 표시 규칙).
 * tabular-nums: 숫자 폭을 같게 해 여러 금액을 위아래로 놓았을 때 자릿수가 맞는다.
 */
export function PriceDisplay({ amount, priceSource, periodLabel = '월' }: PriceDisplayProps) {
  return (
    <span className="inline-flex flex-wrap items-center gap-x-2 gap-y-1">
      <span className="text-2xl font-extrabold tabular-nums">
        {formatWon(amount)}
        <small className="ml-0.5 text-sm font-medium text-muted-foreground">/{periodLabel}</small>
      </span>
      <PriceSourceLabel priceSource={priceSource} />
    </span>
  )
}
