import { render, screen, within } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import { describe, expect, it } from 'vitest'
import { PrivacyRevisionList } from '@/features/legal/components/PrivacyRevisionList'
import { PRIVACY_EFFECTIVE_DATE, PRIVACY_REVISIONS } from '@/features/legal/legalInfo'

/*
 * 방침 변경 이력 목록 테스트.
 * 확인하는 것: 항목마다 자기 날짜와 요약이 한 줄로 보이는지, 이전 방침 경로가 있는 항목에만 링크가 붙는지,
 *   그리고 시행일이 이력의 맨 뒤 항목에서 나오고 이력이 날짜 순서·ISO 형식을 지키는지.
 * 막으려는 사고: 방침을 바꾸고 이력 항목만 더했는데 시행일이 옛 날짜로 남는 것, 날짜를 순서 없이·잘못된 형식으로 적는 것.
 */

describe('방침 변경 이력 목록', () => {
  it('항목마다 자기 날짜와 요약을 한 줄로 보이고, 이전 방침 경로가 있는 항목에만 링크를 붙인다', () => {
    render(
      <MemoryRouter>
        <PrivacyRevisionList
          revisions={[
            { effectiveDate: '2026-03-01', summary: '첫 번째 변경입니다.' },
            {
              effectiveDate: '2026-04-15',
              summary: '두 번째 변경입니다.',
              previousPolicyPath: '/privacy/versions/2026-03-01',
            },
          ]}
        />
      </MemoryRouter>,
    )

    const first = screen.getByText(/첫 번째 변경입니다/)
    expect(first.textContent).toContain('2026년 3월 1일')
    expect(within(first).queryByRole('link')).not.toBeInTheDocument()

    const second = screen.getByText(/두 번째 변경입니다/)
    expect(second.textContent).toContain('2026년 4월 15일')
    expect(within(second).getByRole('link', { name: '이전 방침 보기' })).toHaveAttribute(
      'href',
      '/privacy/versions/2026-03-01',
    )
  })

  it('같은 날 바꾼 항목이 둘이어도 둘 다 보인다', () => {
    render(
      <MemoryRouter>
        <PrivacyRevisionList
          revisions={[
            { effectiveDate: '2026-05-01', summary: '아침에 바꾼 내용입니다.' },
            { effectiveDate: '2026-05-01', summary: '저녁에 바꾼 내용입니다.' },
          ]}
        />
      </MemoryRouter>,
    )

    expect(screen.getByText(/아침에 바꾼 내용입니다/)).toBeInTheDocument()
    expect(screen.getByText(/저녁에 바꾼 내용입니다/)).toBeInTheDocument()
  })
})

describe('방침 변경 이력 데이터(legalInfo)', () => {
  it('시행일은 이력의 맨 뒤 항목 날짜이고, 날짜는 ISO 형식이며 오래된 것부터 순서대로다', () => {
    expect(PRIVACY_REVISIONS.length).toBeGreaterThan(0)
    expect(PRIVACY_EFFECTIVE_DATE).toBe(
      PRIVACY_REVISIONS[PRIVACY_REVISIONS.length - 1].effectiveDate,
    )

    for (const revision of PRIVACY_REVISIONS) {
      expect(revision.effectiveDate).toMatch(/^\d{4}-\d{2}-\d{2}$/)
      expect(revision.summary.trim()).not.toBe('')
    }
    const dates = PRIVACY_REVISIONS.map((revision) => revision.effectiveDate)
    expect(dates).toEqual([...dates].sort()) // ISO 형식은 문자열 순서가 날짜 순서와 같다
  })
})
