import type { OttService } from '@/api/generated/model'
import type { ServiceStatusEntry } from '@/components/common/ServiceStatusRow'
import type { AvailabilityStatus } from '@/components/common/displayTypes'

/**
 * 서비스 목록(서비스 목록 API)과 작품의 제공 상태 목록을 합쳐 ServiceStatusRow가 받는 모양으로 만든다.
 *
 * 합치는 이유: 작품 API는 "서비스 ID별 상태"만 내려주고, 서비스 이름·데이터 충분도("데이터 부족")는 서비스 목록 API에 있다.
 * 서비스 목록을 기준으로 7개를 항상 모두 그리므로, 작품 쪽에 상태가 없는 서비스는 "모름"으로 보인다.
 * (없는 줄을 빼 버리면 사용자가 "없음"과 "정보 없음"을 구분할 수 없다. PRD 5.6)
 * displayOrder 순으로 정렬해 서비스 목록 API의 순서를 그대로 따른다.
 */
export function buildServiceStatusEntries(
  services: OttService[],
  availabilities: { ottServiceId: number; status: AvailabilityStatus }[],
): ServiceStatusEntry[] {
  const statusByServiceId = new Map(availabilities.map((a) => [a.ottServiceId, a.status]))
  return [...services]
    .sort((a, b) => a.displayOrder - b.displayOrder)
    .map((service) => ({
      service,
      status: statusByServiceId.get(service.ottServiceId) ?? 'UNKNOWN',
    }))
}
