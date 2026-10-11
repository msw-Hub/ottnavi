import { TriangleAlertIcon } from 'lucide-react'
import { AdminStatusPill } from '@/features/admin/components/AdminStatusPill'
import { formatIsoDate } from '@/lib/format'

interface StaleVerifiedTagProps {
  lastVerifiedDate: string | null // 월 구독요금의 마지막 확인일('YYYY-MM-DD'). 없거나 잘못된 값이면 "확인일 없음"으로 보인다
  className?: string // 놓이는 자리에 맞춘 여백 등을 덧붙일 때만 쓴다
}

/**
 * OTT 구독상품의 월 구독요금을 오래 확인하지 않았을 때 붙이는 주의 태그다(SCR-14, 사용자 결정 2026-10-11).
 * 예: "요금 확인일 오래됨 (2026-06-30)". OTT 구독상품 목록과 월 구독요금 이력 양쪽에 붙인다.
 *
 * 이 태그는 "보여 주기"만 한다. 언제 붙일지(마지막 확인일이 오늘로부터 90일을 넘었는지)는 화면 조립 쪽(frontend-dev)이 계산해
 * 붙일 때만 이 컴포넌트를 그린다. 날짜 계산을 여기 넣지 않은 이유: 오늘 날짜 기준 계산은 테스트에서 시각을 고정해야 하는 로직이라
 * 표시 컴포넌트와 분리해 두는 편이 다루기 쉽다.
 *
 * 모양: 꽉 채운 노랑(attention) + 경고 아이콘 + 글자. 미매핑 배지와 같은 "주의" 계열이라 운영자가 같은 뜻으로 읽는다.
 * 날짜를 함께 적는 이유: "얼마나 오래됐는지"를 바로 알아야 확인 순서를 정할 수 있다.
 */
export function StaleVerifiedTag({ lastVerifiedDate, className }: StaleVerifiedTagProps) {
  // 잘못된 날짜를 엉뚱하게 보이느니 "확인일 없음"으로 알린다(formatDate는 해석할 수 없으면 null을 돌려준다)
  const dateText = formatIsoDate(lastVerifiedDate) ?? '확인일 없음'
  return (
    <AdminStatusPill
      icon={TriangleAlertIcon}
      label={`요금 확인일 오래됨 (${dateText})`}
      tone="attention"
      size="sm"
      className={className}
    />
  )
}
