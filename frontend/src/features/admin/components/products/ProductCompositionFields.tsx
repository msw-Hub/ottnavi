import type { ProviderTier } from '@/api/generated/model'
import { TIER_OPTION_LABELS, TIER_SELECT_CLASS } from '@/features/admin/bundleRules'
import type { ProductComponentDraft } from '@/features/admin/mockTypes'

interface ServiceOption {
  ottServiceId: number // 서비스 ID
  name: string // 서비스 이름
}

interface BundleComponentsFieldProps {
  idPrefix: string // 입력 id 앞부분. 같은 화면에서 입력 id가 겹치지 않게 한다
  services: ServiceOption[] // 고를 수 있는 서비스 7곳
  value: ProductComponentDraft[] // 지금 고른 구성(서비스 ID 순)
  onChange: (next: ProductComponentDraft[]) => void // 구성이 바뀌었을 때
  error?: string // 검증 문장(예: "2곳 이상 골라 주세요"). 있으면 목록 아래 빨간 글자로 보이고 목록과 연결된다
  disabled?: boolean // 저장 중 잠금
}

/**
 * 번들 구성 고르기 칸이다(SCR-14 번들 등록 대화상자, Task 075 1차안). 구성은 등록할 때만 정하고 등록 뒤에는 바꾸지 않는다. 서비스 7곳을 한 줄씩 두고, 넣을 서비스를 체크한 뒤 등급을 고른다.
 *
 * 체크박스 + 등급 select를 한 줄에 둔 이유:
 * - "어느 서비스가 들어가는가"와 "그 서비스를 어떤 등급으로 보는가"는 서비스마다 짝이라, 같은 줄에 있어야 헷갈리지 않는다.
 * - 체크하지 않은 서비스의 등급 칸은 잠가(disabled) "넣어야 고를 수 있다"는 것을 보여 준다.
 * 체크박스는 브라우저 기본 <input type="checkbox">를 쓴다(shadcn checkbox가 설치돼 있지 않고, 새 의존성 없이 키보드·낭독기를 그대로 지원한다).
 * 줄 높이 44px(min-h-11)은 손가락 터치 크기 기준이다. 라벨 글자를 눌러도 체크된다.
 *
 * 값은 서비스 ID 순으로 정렬해 돌려준다. 같은 구성이 고른 순서에 따라 다르게 보이지 않게 하려는 것이다.
 */
export function BundleComponentsField({
  idPrefix,
  services,
  value,
  onChange,
  error,
  disabled = false,
}: BundleComponentsFieldProps) {
  const errorId = `${idPrefix}-components-error`
  const selected = new Map(value.map((component) => [component.ottServiceId, component.tier]))

  // 체크를 켜면 일반 등급으로 넣고, 끄면 뺀다
  function toggleService(ottServiceId: number, isChecked: boolean) {
    const next: ProductComponentDraft[] = isChecked
      ? [...value, { ottServiceId, tier: 'STANDARD' }]
      : value.filter((component) => component.ottServiceId !== ottServiceId)
    onChange(next.sort((a, b) => a.ottServiceId - b.ottServiceId))
  }

  // 넣은 서비스의 등급만 바꾼다
  function changeTier(ottServiceId: number, tier: ProviderTier) {
    onChange(
      value.map((component) =>
        component.ottServiceId === ottServiceId ? { ...component, tier } : component,
      ),
    )
  }

  return (
    <fieldset className="grid gap-1.5" aria-describedby={error ? errorId : undefined}>
      <legend className="mb-1.5 text-sm font-medium">
        구성 서비스 <span className="font-normal text-muted-foreground">(2곳 이상)</span>
      </legend>
      <ul className="grid divide-y divide-border rounded-lg border border-border">
        {services.map((service) => {
          const checkboxId = `${idPrefix}-service-${service.ottServiceId}`
          const tier = selected.get(service.ottServiceId)
          const isChecked = tier !== undefined
          return (
            <li
              key={service.ottServiceId}
              className="flex min-h-11 flex-wrap items-center justify-between gap-x-3 gap-y-1 px-3 py-1"
            >
              <label htmlFor={checkboxId} className="flex flex-1 items-center gap-2 text-sm">
                <input
                  id={checkboxId}
                  type="checkbox"
                  checked={isChecked}
                  disabled={disabled}
                  onChange={(event) => toggleService(service.ottServiceId, event.target.checked)}
                  className="size-4.5 accent-primary"
                />
                {service.name}
              </label>
              <select
                aria-label={`${service.name} 등급`}
                value={tier ?? 'STANDARD'}
                disabled={disabled || !isChecked}
                onChange={(event) =>
                  changeTier(service.ottServiceId, event.target.value as ProviderTier)
                }
                className={TIER_SELECT_CLASS}
              >
                <option value="STANDARD">{TIER_OPTION_LABELS.STANDARD}</option>
                <option value="AD">{TIER_OPTION_LABELS.AD}</option>
              </select>
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
