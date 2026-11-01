import { Link } from 'react-router'
import type { PrivacyRevision } from '@/features/legal/legalInfo'
import { formatDate } from '@/lib/format'

interface PrivacyRevisionListProps {
  revisions: readonly PrivacyRevision[] // 변경 이력(오래된 것이 앞). 보통 legalInfo.ts의 PRIVACY_REVISIONS
}

/**
 * 개인정보처리방침 13절(방침의 변경)의 변경 이력이다. 항목마다 한 줄("2026년 10월 31일 변경: …")로 그리고,
 * 이전 방침 경로(previousPolicyPath)가 있는 항목에만 "이전 방침 보기" 링크를 붙인다(없으면 링크가 없다).
 */
export function PrivacyRevisionList({ revisions }: PrivacyRevisionListProps) {
  return (
    <>
      {revisions.map((revision, index) => (
        // 같은 날 두 번 바꾼 항목도 있을 수 있어 날짜와 순번을 함께 키로 쓴다
        <p key={`${revision.effectiveDate}-${index}`}>
          <span className="whitespace-nowrap">
            {formatDate(revision.effectiveDate) ?? revision.effectiveDate}
          </span>{' '}
          변경: {revision.summary}
          {revision.previousPolicyPath && (
            <>
              {' '}
              <Link
                to={revision.previousPolicyPath}
                className="text-primary underline underline-offset-3 whitespace-nowrap"
              >
                이전 방침 보기
              </Link>
            </>
          )}
        </p>
      ))}
    </>
  )
}
