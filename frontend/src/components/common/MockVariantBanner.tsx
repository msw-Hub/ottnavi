import { FlaskConicalIcon } from 'lucide-react'
import { isMockMode, type MockVariant } from '@/lib/mockScenario'

interface MockVariantBannerProps {
  decisionLabel: string // 어떤 결정의 비교인지(예: "R11-18 환산 입력 저장")
  variant: MockVariant // 지금 화면에 적용된 안(a 또는 b). 주소의 ?mockVariant…= 값에서 온다
  description: Record<MockVariant, string> // 안마다 "지금 화면이 어떻게 달라지는지" 한 줄
}

/**
 * 사용자 결정이 남은 항목(R11-18·R11-19)의 두 안을 비교할 수 있게, 지금 어느 안인지 알리는 띠다(Task 030 검토용).
 *
 * 목업 모드(VITE_USE_MOCK=true)에서만 그린다. 운영에서는 null이라 보이지 않고, 운영 빌드에서는 Vite가
 * isMockMode()를 상수로 바꿔 이 분기까지 지워 준다. Task 030에서 안이 정해지면 이 컴포넌트와 사용처를 함께 지운다.
 * 점선 테두리 + 플라스크 아이콘 + "검토용" 글자로 "실제 기능이 아니라 비교용 표시"라는 것을 색 없이도 알 수 있게 한다.
 */
export function MockVariantBanner({ decisionLabel, variant, description }: MockVariantBannerProps) {
  if (!isMockMode()) return null

  return (
    <p className="flex items-start gap-2 rounded-lg border border-dashed border-info bg-info-bg px-3 py-2 text-sm text-info">
      <FlaskConicalIcon aria-hidden="true" className="mt-0.5 size-4 shrink-0" />
      <span>
        <strong className="font-bold">
          Task 030 검토용 · 안 {variant.toUpperCase()} ({decisionLabel})
        </strong>
        <br />
        {description[variant]}
      </span>
    </p>
  )
}
