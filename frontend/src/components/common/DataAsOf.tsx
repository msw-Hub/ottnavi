import { CalendarIcon } from 'lucide-react'
import { formatDataAsOf } from '@/lib/format'

interface DataAsOfProps {
  value: string | Date | null | undefined // 데이터 기준일. 'YYYY-MM-DD', ISO-8601 일시 문자열, Date 중 하나
}

/**
 * 화면에 보이는 제공처·가격 데이터가 언제 기준인지 보여 준다(PRD 5.6 "데이터 기준일을 함께 보여준다").
 *
 * 데이터는 하루 단위로 갱신되고 몇 시간 늦을 수 있어, 날짜와 함께 그 사실도 짧게 알린다.
 * 값이 없거나 잘못된 날짜(빈 문자열, '2026-02-30' 등)면 엉뚱한 날짜를 보이지 않고 "기준일 정보 없음"을 보인다.
 * 날짜 글자는 lib/format.ts의 formatDataAsOf로 만든다(해석할 수 없으면 null을 돌려준다).
 */
export function DataAsOf({ value }: DataAsOfProps) {
  const formatted = formatDataAsOf(value)

  return (
    <p className="flex items-start gap-1.75 text-sm text-muted-foreground tabular-nums">
      <CalendarIcon aria-hidden="true" className="mt-0.75 size-4 shrink-0" />
      {formatted ? (
        <span>
          데이터 기준일{' '}
          {/* <time>: 기계가 읽을 수 있는 날짜를 함께 둔다. formatted가 있으면 value는 해석 가능한 값이다 */}
          <time
            dateTime={value instanceof Date ? value.toISOString() : value?.trim()}
            className="font-semibold text-foreground"
          >
            {formatted}
          </time>{' '}
          · 하루 단위로 갱신되며 몇 시간 늦을 수 있습니다.
        </span>
      ) : (
        <span>기준일 정보 없음</span>
      )}
    </p>
  )
}
