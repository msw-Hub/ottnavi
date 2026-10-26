import { render, screen, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { AboutScreen } from '@/features/about/components/AboutScreen'

/*
 * About 화면 테스트(Task 027). 약관상 꼭 있어야 하는 TMDB 고지문과 JustWatch 출처, 비영리 안내가 보이는지,
 * 제공 상태 3가지가 글자로 설명되는지를 사용자가 보는 글자 기준으로 확인한다.
 */
describe('About 화면', () => {
  it('TMDB 고지문 원문과 로고 자리, JustWatch 출처, 비영리 운영 안내가 보인다', () => {
    render(<AboutScreen />)

    expect(screen.getByRole('heading', { level: 1, name: 'OTT내비 소개' })).toBeInTheDocument()
    // 고지문은 번역하지 않은 원문 그대로여야 한다
    expect(
      screen.getByText(
        'This product uses TMDB and the TMDB APIs but is not endorsed, certified, or otherwise approved by TMDB.',
      ),
    ).toBeInTheDocument()
    expect(screen.getByRole('img', { name: /TMDB 로고 자리/ })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /JustWatch/ })).toHaveAttribute(
      'href',
      'https://www.justwatch.com/',
    )
    expect(screen.getByRole('heading', { name: '비영리 운영 안내' })).toBeInTheDocument()
    expect(screen.getByText(/비영리 프로젝트/)).toBeInTheDocument()
  })

  it('결과를 "확정"이 아니라 "확인된 범위"로 설명하고, 제공 상태 3가지를 글자로 설명한다', () => {
    render(<AboutScreen />)

    expect(screen.getByText('확인된 범위')).toBeInTheDocument()
    const guide = within(
      screen.getByRole('heading', { name: '제공 상태 읽는 법' }).closest('section')!,
    )
    expect(guide.getAllByRole('listitem').map((item) => item.textContent)).toEqual([
      expect.stringContaining('있음'),
      expect.stringContaining('없음'),
      expect.stringContaining('모름'),
    ])
  })
})
