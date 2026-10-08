import type { OttService } from '@/api/generated/model'
import { DataInsufficientTag } from '@/components/common/DataInsufficientTag'
import type { AvailabilityStatus } from '@/components/common/displayTypes'
import { ProviderStatusBadge } from '@/components/common/ProviderStatusBadge'

// 서비스 하나의 제공 상태. 서비스 정보는 계약에 있는 서비스 목록 API(listOttServices)의 생성 타입을 그대로 쓴다
export interface ServiceStatusEntry {
  service: Pick<OttService, 'ottServiceId' | 'name' | 'dataQuality'> // 서비스 ID(목록 key), 이름, 데이터 충분도
  status: AvailabilityStatus // 이 작품(시즌)이 이 서비스에서 구독으로 제공되는지
}

interface ServiceStatusRowProps {
  entries: ServiceStatusEntry[] // 보여 줄 서비스들(보통 7개). 받은 순서대로 그린다(서비스 목록 API의 displayOrder 순서를 그대로 넘긴다)
  heading?: string // 목록 위 제목. 생략하면 제목 없이 목록만 그린다
}

/**
 * 작품 하나에 대해 서비스별(국내 OTT 7곳) 제공 상태를 한 묶음으로 보여 준다.
 *
 * - 각 줄은 "서비스 이름 + 제공 상태 배지(ProviderStatusBadge)"다.
 * - dataQuality가 INSUFFICIENT인 서비스(쿠팡플레이)는 이름 옆에 "데이터 부족"을 붙인다(PRD 5.6).
 * - 목록(<ul>)으로 그리는 이유: 화면 낭독기가 "목록, 항목 7개"처럼 개수를 알려 줘 전체 범위를 파악하기 쉽다.
 * 넓은 화면은 두 칸, 좁은 화면(640px 미만)은 한 칸으로 시안 D2c의 배치를 따른다.
 * 출처(SourceAttribution)와 기준일(DataAsOf)은 화면마다 놓는 자리가 달라 이 컴포넌트에 넣지 않고 호출부가 함께 둔다.
 */
export function ServiceStatusRow({ entries, heading }: ServiceStatusRowProps) {
  return (
    <div>
      {heading && <h3 className="mb-2 font-semibold">{heading}</h3>}
      <ul className="grid grid-cols-1 gap-x-6 sm:grid-cols-2">
        {entries.map(({ service, status }) => (
          <li
            key={service.ottServiceId}
            className="flex min-h-11 items-center justify-between gap-2 border-b border-border py-1.5"
          >
            <span className="text-[0.9375rem] font-semibold">
              {service.name}
              {service.dataQuality === 'INSUFFICIENT' && (
                <>
                  {' '}
                  <DataInsufficientTag />
                </>
              )}
            </span>
            <ProviderStatusBadge status={status} />
          </li>
        ))}
      </ul>
    </div>
  )
}
