import type { CastMember } from '@/features/title-detail/mockTypes'

interface CastListProps {
  cast: CastMember[] // 주요 출연진(상위 10명까지)
}

/**
 * 주요 출연진 목록이다(PRD FR-03: 상위 10명). 출연진 정보가 없는 작품도 있어 빈 경우의 안내를 함께 둔다.
 * 목록(<ul>)으로 그리는 이유: 화면 낭독기가 "목록, 항목 N개"로 인원을 알려 준다.
 */
export function CastList({ cast }: CastListProps) {
  return (
    <section className="grid gap-3">
      <h2 className="text-lg font-semibold">주요 출연진</h2>
      {cast.length === 0 ? (
        <p className="text-sm text-muted-foreground">출연진 정보가 없습니다.</p>
      ) : (
        <ul className="grid grid-cols-2 gap-x-6 gap-y-1.5 sm:grid-cols-3 md:grid-cols-4">
          {cast.map((member) => (
            // 이름이 같은 배우가 한 작품에 둘 나올 가능성은 낮지만 배역까지 합쳐 key를 만든다
            <li key={`${member.name}-${member.character ?? ''}`} className="text-sm">
              <span className="font-semibold">{member.name}</span>
              {member.character && (
                <span className="text-muted-foreground"> · {member.character}</span>
              )}
            </li>
          ))}
        </ul>
      )}
    </section>
  )
}
