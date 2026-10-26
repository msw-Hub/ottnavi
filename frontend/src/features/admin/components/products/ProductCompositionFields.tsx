interface ServiceOption {
  ottServiceId: number // 서비스 ID
  name: string // 서비스 이름
}

interface BundleServicesFieldProps {
  idPrefix: string // 입력 id 앞부분. 같은 화면에서 입력 id가 겹치지 않게 한다
  services: ServiceOption[] // 고를 수 있는 서비스 7곳
  value: number[] // 지금 고른 서비스 ID(오름차순)
  onChange: (next: number[]) => void // 구성이 바뀌었을 때
  error?: string // 검증 문장(예: "2곳 이상 골라 주세요"). 있으면 목록 아래 빨간 글자로 보이고 목록과 연결된다
  disabled?: boolean // 저장 중 잠금
}

/**
 * 번들 구성 고르기 칸이다(SCR-14 번들 등록 대화상자). 구성은 등록할 때만 정하고 등록 뒤에는 바꾸지 않는다(계약: 구성 편집 API 없음).
 * 서비스 7곳을 한 줄씩 두고 넣을 서비스를 체크한다.
 *
 * 이전(Task 075 1차안): 줄마다 등급(일반/광고형) select가 붙어 있었다. Task 076에서 등급을 없애고 상품 단위 "저가형" 체크
 * (대화상자 아래쪽 칸)로 바꿔 체크박스만 남았다. 이름도 BundleComponentsField → BundleServicesField로 바꿨다(값이 서비스 ID 목록이라).
 *
 * 체크박스는 브라우저 기본 <input type="checkbox">를 쓴다(shadcn checkbox가 설치돼 있지 않고, 새 의존성 없이 키보드·낭독기를 그대로 지원한다).
 * 줄 높이 44px(min-h-11)은 손가락 터치 크기 기준이다. 줄 어디를 눌러도 체크된다(줄 전체가 label).
 * 7곳을 두 열로 나눈 이유(sm 이상): 한 열이면 대화상자가 길어져 아래 금액 칸이 화면 밖으로 밀린다. 375px에서는 한 열이다.
 *
 * 값은 서비스 ID 오름차순으로 돌려준다. 계약도 오름차순이고, 같은 구성이 고른 순서에 따라 다르게 보이지 않게 하려는 것이다.
 */
export function BundleServicesField({
  idPrefix,
  services,
  value,
  onChange,
  error,
  disabled = false,
}: BundleServicesFieldProps) {
  const errorId = `${idPrefix}-services-error`
  const selected = new Set(value)

  // 체크를 켜면 넣고, 끄면 뺀다
  function toggleService(ottServiceId: number, isChecked: boolean) {
    const next = isChecked ? [...value, ottServiceId] : value.filter((id) => id !== ottServiceId)
    onChange([...new Set(next)].sort((a, b) => a - b))
  }

  return (
    <fieldset className="grid gap-1.5" aria-describedby={error ? errorId : undefined}>
      <legend className="mb-1.5 text-sm font-medium">
        구성 서비스{' '}
        <span className="font-normal text-muted-foreground">
          (2곳 이상 · 지금 {value.length}곳)
        </span>
      </legend>
      <ul className="grid gap-1.5 sm:grid-cols-2">
        {services.map((service) => {
          const checkboxId = `${idPrefix}-service-${service.ottServiceId}`
          const isChecked = selected.has(service.ottServiceId)
          return (
            <li key={service.ottServiceId}>
              {/* 고른 줄은 테두리가 primary로 바뀌지만, 체크 표시 자체가 모양으로 상태를 알리므로 색에만 기대지 않는다 */}
              <label
                htmlFor={checkboxId}
                className="flex min-h-11 cursor-pointer items-center gap-2 rounded-lg border border-input px-3 text-sm has-[:checked]:border-primary has-[:disabled]:cursor-not-allowed has-[:disabled]:opacity-50 has-[:focus-visible]:ring-3 has-[:focus-visible]:ring-ring/50"
              >
                <input
                  id={checkboxId}
                  type="checkbox"
                  checked={isChecked}
                  disabled={disabled}
                  onChange={(event) => toggleService(service.ottServiceId, event.target.checked)}
                  className="size-4.5 accent-primary focus-visible:outline-none"
                />
                {service.name}
              </label>
            </li>
          )
        })}
      </ul>
      {error && (
        <p id={errorId} role="alert" className="text-sm text-destructive">
          {error}
        </p>
      )}
    </fieldset>
  )
}
