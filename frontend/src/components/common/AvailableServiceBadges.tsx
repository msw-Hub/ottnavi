import { DataInsufficientTag } from '@/components/common/DataInsufficientTag'
import type { ServiceStatusEntry } from '@/components/common/ServiceStatusRow'

interface AvailableServiceBadgesProps {
  entries: ServiceStatusEntry[] // 서비스별 상태(보통 7개). 이 중 "있음"인 서비스만 그린다
  emptyText?: string // "있음"이 하나도 없을 때 보일 글자
  label?: string // 목록의 이름(화면 낭독기용). 기본값은 "볼 수 있는 곳"이다
}

/**
 * 제공이 "있음"으로 확인된 서비스를 이름만 적은 칩으로 모아 보여 주는 요약이다(검색 결과 카드, 시즌 요약 줄).
 *
 * 칩마다 "(있음)"을 붙이지 않는 이유: 이 목록에 있다는 것 자체가 "볼 수 있다"는 뜻이라 서비스가 여러 개일 때 같은 글자가
 * 반복되어 번잡하다. 앞에 "볼 수 있는 곳" 라벨을 한 번만 붙여 뜻을 알린다. 칩에는 서비스 이름 글자가 있어 색만으로 뜻을 전하지 않는다.
 * 7개를 모두 그리지 않는 이유: 목록·요약에서는 "어디서 볼 수 있나"가 핵심이다. 없음/모름까지 구분하는 전체 상태는
 * 상세 화면의 서비스별 상태(ServiceStatusRow)에서 있음/없음/모름 글자와 모양으로 보인다.
 * 하나도 확인되지 않았을 때는 비워 두지 않고 글자("확인된 제공처가 없습니다")로 알려, 화면이 비어 보이는 것과 구분한다.
 */
export function AvailableServiceBadges({
  entries,
  emptyText = '확인된 제공처가 없습니다',
  label = '볼 수 있는 곳',
}: AvailableServiceBadgesProps) {
  const available = entries.filter((entry) => entry.status === 'AVAILABLE')

  if (available.length === 0) {
    return <p className="text-sm text-muted-foreground">{emptyText}</p>
  }

  return (
    <div className="flex flex-wrap items-center gap-x-2 gap-y-1.5">
      {/* 눈으로 보는 라벨. 목록의 접근 가능한 이름은 아래 ul의 aria-label이 맡으므로 낭독기에서는 건너뛴다(같은 말을 두 번 읽지 않게) */}
      <span aria-hidden="true" className="text-xs text-muted-foreground">
        볼 수 있는 곳
      </span>
      <ul aria-label={label} className="flex flex-wrap gap-1.5">
        {available.map(({ service }) => (
          <li
            key={service.ottServiceId}
            className="inline-flex items-center gap-1.5 rounded-lg border border-solid border-status-available-border bg-status-available-bg px-2 py-0.5 text-sm font-semibold text-status-available"
          >
            {service.name}
            {service.dataQuality === 'INSUFFICIENT' && <DataInsufficientTag />}
          </li>
        ))}
      </ul>
    </div>
  )
}
