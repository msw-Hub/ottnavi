import { LayersIcon, MegaphoneIcon } from 'lucide-react'
import type { PricingComposition } from '@/features/pricing/mockTypes'

interface ProductKindTagsProps {
  composition: PricingComposition // 상품 유형과 구성(서비스·등급)
}

/**
 * 요금 줄의 서비스 이름 옆에 붙는 "번들"·"광고형" 태그다(SCR-09, Task 075 1차안). 일반 단품에는 아무것도 붙지 않는다.
 *
 * 모양이 서로 다르게 한 이유(색만으로 구분하지 않는다):
 * - 번들: 옅은 파랑으로 채운 실선 + 겹친 층 아이콘. "여러 서비스를 묶은 상품"이라는 긍정적 정보라 info 토큰(대비 약 9:1)을 쓴다.
 * - 광고형: 바탕 없는 점선 + 확성기 아이콘. 볼 수 있는 작품이 줄 수 있다는 제약이라 차분한 회색(muted-foreground)으로 둔다.
 * 높이와 글자 크기는 "데이터 부족" 태그(DataInsufficientTag)와 맞춰 같은 줄에 나란히 놓여도 어긋나지 않게 했다.
 */
export function ProductKindTags({ composition }: ProductKindTagsProps) {
  const isBundle = composition.productType === 'BUNDLE'
  const hasAdTier = composition.components.some((component) => component.tier === 'AD')
  if (!isBundle && !hasAdTier) return null

  return (
    <>
      {isBundle && (
        <span className="inline-flex items-center gap-1 rounded-lg border border-info bg-info-bg px-1.75 py-px align-[0.0625rem] text-xs font-bold whitespace-nowrap text-info">
          <LayersIcon aria-hidden="true" className="size-3.5 shrink-0" />
          번들
        </span>
      )}
      {hasAdTier && (
        <span className="inline-flex items-center gap-1 rounded-lg border border-dashed border-input px-1.75 py-px align-[0.0625rem] text-xs font-bold whitespace-nowrap text-muted-foreground">
          <MegaphoneIcon aria-hidden="true" className="size-3.5 shrink-0" />
          광고형
        </span>
      )}
    </>
  )
}
