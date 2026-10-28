import type { ReactNode } from 'react'
import { PRIVACY_CONTACT_EMAIL } from '@/features/legal/legalInfo'

interface PolicySectionProps {
  id: string // 제목(h2)의 id. 구역의 이름으로 연결한다(낭독기가 "○○, 구역"으로 읽는다)
  title: string // 구역 제목
  children: ReactNode
}

// 방침·약관의 한 구역. 카드로 감싸지 않고 위쪽 가는 선으로만 나눈다(긴 글이라 상자가 겹겹이 보이면 읽기 흐름이 끊긴다)
export function PolicySection({ id, title, children }: PolicySectionProps) {
  return (
    <section aria-labelledby={id} className="grid gap-3 border-t border-border pt-6">
      <h2 id={id} className="text-lg font-bold break-keep">
        {title}
      </h2>
      {children}
    </section>
  )
}

// 본문 목록. 점(disc) 목록에 줄 간격을 넉넉히 둔다
export function PolicyList({ children }: { children: ReactNode }) {
  return <ul className="grid list-disc gap-1.5 pl-5">{children}</ul>
}

// 문의 이메일 링크. 누르면 메일 앱이 열린다. 본문 링크라 밑줄과 primary 색을 함께 써서 색만으로 링크임을 알리지 않는다
export function ContactEmailLink() {
  return (
    <a
      href={`mailto:${PRIVACY_CONTACT_EMAIL}`}
      className="break-all text-primary underline underline-offset-3"
    >
      {PRIVACY_CONTACT_EMAIL}
    </a>
  )
}
