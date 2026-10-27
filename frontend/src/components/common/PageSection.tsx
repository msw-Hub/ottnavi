import type { ReactNode } from 'react'
import { cn } from '@/lib/utils'

interface PageSectionProps {
  headingId: string // 구간 제목(h2)의 id. section의 aria-labelledby가 이 값으로 제목을 가리킨다. 화면 안에서 겹치지 않아야 한다
  title: ReactNode // 구간 제목 글자(h2 안에 들어간다)
  description?: ReactNode // 제목 바로 아래 설명 한두 줄(기준일 등). 없으면 생략한다
  className?: string // section에 덧붙일 클래스. 내용 요소 사이 간격은 화면마다 달라 호출하는 쪽이 내용을 감싸 정한다
  children: ReactNode // 구간 내용
}

/**
 * 화면 안의 한 구간(제목 + 설명 + 내용)을 같은 간격 규칙으로 그린다(styles/tokens.css의 구간 간격 토큰, 규칙 R3·R4).
 * - 제목(h2)은 text-xl font-bold다. 구간 안 카드 제목(h3, text-lg 이하)보다 커야 구간이 시작된다는 신호가 된다(R4).
 * - 제목과 설명은 4px(gap-1)로 붙이고, 제목 묶음과 내용 사이는 16px(gap-section-inner)이다. 제목이 위 구간보다 자기 내용에 가깝게 붙는다(R3).
 * - 구간과 구간 사이 간격은 이 컴포넌트가 정하지 않는다. 부모가 gap-section 하나로 정한다(R2). 그래서 바깥 여백(mt-*)을 두지 않는다.
 * <section aria-labelledby>를 그대로 그리는 이유: 낭독기의 "영역(region)" 목록에 제목 이름으로 나오고,
 * 테스트가 closest('section')이나 getByRole('region', { name })으로 구간을 찾기 때문이다(바깥 div로 감싸지 않는다).
 * 제목 줄 오른쪽 액션 슬롯은 아직 쓰는 화면이 없어 두지 않았다(필요해지면 그때 더한다).
 */
export function PageSection({
  headingId,
  title,
  description,
  className,
  children,
}: PageSectionProps) {
  return (
    <section aria-labelledby={headingId} className={cn('grid gap-section-inner', className)}>
      <div className="grid gap-1">
        <h2 id={headingId} className="text-xl font-bold">
          {title}
        </h2>
        {description}
      </div>
      {children}
    </section>
  )
}
