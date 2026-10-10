import type { ReactNode } from 'react'
import { CheckIcon } from 'lucide-react'
import type { UseFormRegisterReturn } from 'react-hook-form'
import { cn } from '@/lib/utils'

// 선택지 하나
export interface SegmentedRadioOption {
  value: string // 폼에 담기는 값
  label: string // 화면 글자
  hint?: string // 글자 아래 보조 설명(예: "월 8시간"). 있으면 두 줄로 보인다
  selectedClassName?: string // 선택됐을 때 칸의 색(바탕·테두리·글자). 없으면 강조색(primary). 선택지마다 다른 색으로 보이게 할 때 쓴다
  isDashed?: boolean // 점선 테두리로 "덜 중요한 선택지"(예: 잘 모르겠음)임을 모양으로 알린다
}

interface SegmentedRadioProps {
  legend: ReactNode // 선택 묶음의 이름. 화면 낭독기는 이 글자로 묶음을 소개한다
  isLegendHidden?: boolean // true면 눈에는 숨기고 낭독기에만 읽힌다(바로 위에 이미 같은 제목이 있을 때)
  options: SegmentedRadioOption[] // 선택지들(화면 순서)
  registration: UseFormRegisterReturn // React Hook Form의 register(...) 결과. 라디오 전부가 같은 이름으로 묶인다
  errorId?: string // 오류 문장 요소의 id. 있으면 모든 라디오가 오류 상태(aria-invalid)가 되고 문장과 연결된다
  className?: string // 선택지가 늘어서는 방식(그리드 열 수 등)을 호출부가 정할 때
}

/**
 * 한 줄에 나란히 놓이는 "칸 선택"(라디오 묶음)이다. 서비스 이용 상태, 시청 시간 입력 방식·프리셋에서 쓴다.
 *
 * 네이티브 <input type="radio">를 쓰는 이유: 키보드(화살표로 이동, Tab으로 묶음 진입)와 화면 낭독기 지원을 브라우저가 이미 해 주고,
 * React Hook Form의 register에 그대로 붙는다. 실제 라디오 동그라미는 눈에 보이지 않게(sr-only) 두고, 옆의 칸을 눌러도 선택되게
 * <label>로 감쌌다. 터치 영역은 칸 전체(최소 높이 44px).
 *
 * 선택된 칸은 색만으로 알리지 않는다: ① 체크 아이콘 ② 바탕이 채워짐 ③ 글자 굵기. 흑백으로 봐도 구분된다.
 * 포커스는 숨겨진 라디오가 받으므로 칸에 `has-[:focus-visible]`로 ring을 그려 키보드 위치가 항상 보인다.
 */
export function SegmentedRadio({
  legend,
  isLegendHidden = false,
  options,
  registration,
  errorId,
  className,
}: SegmentedRadioProps) {
  return (
    <fieldset className="min-w-0">
      <legend className={cn('mb-1.5 text-sm font-semibold', isLegendHidden && 'sr-only')}>
        {legend}
      </legend>
      <div className={cn('grid gap-2', className)}>
        {options.map((option) => (
          <label key={option.value} className="group/segment relative block min-w-0">
            <input
              type="radio"
              value={option.value}
              className="peer sr-only"
              aria-invalid={errorId ? true : undefined}
              aria-describedby={errorId}
              {...registration}
            />
            <span
              className={cn(
                'flex min-h-11 items-center justify-center gap-1.5 rounded-lg border-2 border-input bg-background px-2 py-1.5 text-center text-sm leading-tight font-medium transition-colors',
                'group-hover/segment:bg-muted',
                'peer-checked:border-primary peer-checked:bg-primary peer-checked:font-bold peer-checked:text-primary-foreground',
                'peer-focus-visible:ring-3 peer-focus-visible:ring-ring/50',
                option.isDashed && 'border-dashed',
                errorId && 'border-destructive',
                option.selectedClassName,
              )}
            >
              {/* 선택됨을 모양으로도 알리는 체크. 선택된 칸에서만 보인다 */}
              <CheckIcon
                aria-hidden="true"
                strokeWidth={3}
                className="hidden size-4 shrink-0 group-has-[:checked]/segment:block"
              />
              <span className="grid min-w-0">
                <span>{option.label}</span>
                {option.hint && (
                  <span className="text-xs font-normal opacity-90">{option.hint}</span>
                )}
              </span>
            </span>
          </label>
        ))}
      </div>
    </fieldset>
  )
}
