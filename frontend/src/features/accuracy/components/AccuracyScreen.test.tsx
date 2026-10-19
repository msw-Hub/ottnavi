import { render, screen, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { AccuracyScreen } from '@/features/accuracy/components/AccuracyScreen'

/*
 * 데이터 정확도 안내 화면 테스트(Task 089). 정적 화면이라 API 없이 렌더링한다.
 * 수치는 docs/PRD.md 8.2의 검증 결과(70건 중 66건, 약 94%)와 같은지 확인하는 용도로 일부러 하드코딩했다.
 * 검증을 다시 해서 PRD 8.2가 바뀌면 이 기대값도 함께 고친다.
 */
describe('데이터 정확도 안내 화면', () => {
  it('제목과 합산 결과(66/70, 약 94%)가 보인다', () => {
    render(<AccuracyScreen />)

    expect(
      screen.getByRole('heading', { level: 1, name: '데이터 정확도 안내' }),
    ).toBeInTheDocument()
    expect(screen.getByText('66/70')).toBeInTheDocument()
    expect(screen.getByText(/약 94%/)).toBeInTheDocument()
  })

  it('서비스별 표에 7개 서비스 행이 있고, 왓챠는 "불일치 있음", 쿠팡플레이는 "데이터 부족"이 글자로 보인다', () => {
    render(<AccuracyScreen />)

    const table = within(screen.getByRole('table', { name: '서비스별 일치 건수와 비고' }))
    // 머리글 행을 빼고 서비스 행만 센다(행 머리글 th 7개)
    expect(table.getAllByRole('rowheader')).toHaveLength(7)

    const watcha = within(table.getByRole('rowheader', { name: /왓챠/ }).closest('tr')!)
    expect(watcha.getByText('불일치 있음')).toBeInTheDocument()
    expect(watcha.getByText('6/10')).toBeInTheDocument()

    const coupang = within(table.getByRole('rowheader', { name: /쿠팡플레이/ }).closest('tr')!)
    expect(coupang.getByText('데이터 부족')).toBeInTheDocument()
  })

  it('JustWatch 출처 링크와 재현율을 측정하지 않았다는 한계 문구가 보인다', () => {
    render(<AccuracyScreen />)

    expect(screen.getByRole('link', { name: /JustWatch/ })).toHaveAttribute(
      'href',
      'https://www.justwatch.com/',
    )
    const limits = within(
      screen.getByRole('heading', { name: '알아 두실 한계' }).closest('section')!,
    )
    expect(limits.getByText(/재현율을 재지 않아/)).toBeInTheDocument()
  })
})
