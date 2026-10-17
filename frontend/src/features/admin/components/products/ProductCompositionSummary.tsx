import { LayersIcon, MegaphoneIcon } from 'lucide-react'
import { AdminStatusPill } from '@/features/admin/components/AdminStatusPill'
import type { ProductCompositionDraft } from '@/features/admin/mockTypes'

interface ProductCompositionSummaryProps {
  composition: ProductCompositionDraft // 상품 유형과 들어 있는 서비스·등급
  serviceName: (ottServiceId: number) => string // 서비스 ID → 이름("서비스 N" 대체 포함)
}

/**
 * OTT 구독상품 목록의 "구성" 칸이다(SCR-14, Task 075 1차안). 예: 넷플릭스 [광고형] / [번들] 티빙 + 웨이브.
 *
 * 모양 규칙(색만으로 구분하지 않는다):
 * - 유형 배지: 번들에만 옅게 채운 실선(progress 톤) + 겹친 층 아이콘 + "번들" 글자를 붙인다. 단품은 배지 없이 서비스 이름만 보인다.
 * - 등급: 일반(STANDARD)은 따로 적지 않고, 광고형(AD)만 서비스 이름 옆에 회색 점선 "광고형" 태그 + 확성기 아이콘을 붙인다.
 *   대부분이 일반이라 모두 "일반"을 적으면 칸이 시끄러워지고, 예외(광고형)가 오히려 묻힌다.
 * - 번들 서비스 사이는 "+"로 잇는다. "여러 곳을 한 번에 결제한다"는 뜻이 글자로 드러난다.
 */
export function ProductCompositionSummary({
  composition,
  serviceName,
}: ProductCompositionSummaryProps) {
  const isBundle = composition.productType === 'BUNDLE'

  // 단품은 목록(ul) 없이 서비스 이름(+광고형 태그)만 쓴다. 이름표 붙은 목록으로 감싸면 표 칸의 접근 이름이 "넷플릭스"가 아니라
  // 목록 이름("서비스")으로 읽혀, 낭독기에서 칸 내용이 사라진다(기존 테스트가 이 차이를 잡았다)
  if (!isBundle) {
    const single = composition.components[0]
    if (!single) return null
    return (
      <span className="inline-flex flex-wrap items-center gap-1.5">
        {serviceName(single.ottServiceId)}
        {single.tier === 'AD' && (
          <AdminStatusPill icon={MegaphoneIcon} label="광고형" tone="neutral" size="sm" />
        )}
      </span>
    )
  }

  return (
    <div className="flex flex-wrap items-center gap-x-2 gap-y-1">
      {/* 번들에만 배지를 붙인다. 이전에는 단품에도 "단품" 배지를 붙였는데, 거의 모든 행이 단품이라 같은 배지가 반복되어
          번들이 오히려 덜 보였다(1차안 확인 후 판단). 단품은 서비스 이름 하나로 충분히 구분된다
          {isBundle ? (...) : <AdminStatusPill icon={PackageIcon} label="단품" tone="outline" size="sm" />} */}
      <AdminStatusPill icon={LayersIcon} label="번들" tone="progress" size="sm" />
      <ul className="flex flex-wrap items-center gap-x-1.5 gap-y-1">
        {composition.components.map((component, index) => (
          <li key={component.ottServiceId} className="inline-flex items-center gap-1.5">
            {/* "+"는 꾸밈 글자라 낭독기에서는 숨긴다. 목록에 aria-label을 달지 않은 이유: 달면 표 칸 이름이 서비스 이름 대신 목록 이름으로 읽힌다 */}
            {index > 0 && (
              <span aria-hidden="true" className="text-muted-foreground">
                +
              </span>
            )}
            <span>{serviceName(component.ottServiceId)}</span>
            {component.tier === 'AD' && (
              <AdminStatusPill icon={MegaphoneIcon} label="광고형" tone="neutral" size="sm" />
            )}
          </li>
        ))}
      </ul>
    </div>
  )
}
