import { render, screen } from '@testing-library/react'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { describe, expect, it } from 'vitest'
import { HomeIntro } from '@/features/home/components/HomeIntro'

/*
 * 홈 맨 위 배너(시안 A 포스터 콜라주) 테스트.
 * 확인하는 것: 포스터 경로가 있으면 배경 포스터가 그려지되 낭독기에서는 숨겨지는지, 경로가 없어도(받는 중·실패) 제목·버튼이 그대로 보이는지.
 * 막으려는 사고: 꾸밈 이미지가 낭독기에 "이미지"로 잔뜩 읽히는 것, 순위 데이터가 없을 때 배너가 비거나 시작 버튼이 사라지는 것.
 */
function renderIntro(heroPosterPaths: (string | null)[]) {
  const router = createMemoryRouter(
    [{ path: '/', element: <HomeIntro isMember={false} heroPosterPaths={heroPosterPaths} /> }],
    { initialEntries: ['/'] },
  )
  const { container } = render(<RouterProvider router={router} />)
  return container
}

describe('홈 배너', () => {
  it('포스터 경로가 있으면 배경 포스터를 그리고, 꾸밈이라 낭독기에서 숨긴다', () => {
    const container = renderIntro(['/a.jpg', null, '/b.jpg'])

    // null은 건너뛰어 실제 이미지는 2장이다
    const posters = container.querySelectorAll('img')
    expect(posters).toHaveLength(2)
    expect(posters[0]).toHaveAttribute('src', 'https://image.tmdb.org/t/p/w342/a.jpg')
    for (const poster of posters) {
      expect(poster).toHaveAttribute('alt', '')
      expect(poster.closest('[aria-hidden="true"]')).not.toBeNull()
    }
    // 낭독기 기준으로는 이미지가 하나도 없다
    expect(screen.queryAllByRole('img')).toHaveLength(0)
  })

  it('포스터 경로가 없어도 소개 제목과 시작 버튼 두 개가 보인다', () => {
    const container = renderIntro([])

    expect(container.querySelectorAll('img')).toHaveLength(0)
    expect(
      screen.getByRole('heading', {
        level: 1,
        name: '찜한 작품, 어느 달에 어디서 볼지 계산해 드려요',
      }),
    ).toBeInTheDocument()
    expect(screen.getByRole('link', { name: '무료로 플랜 계산 시작하기' })).toHaveAttribute(
      'href',
      '/login',
    )
    expect(screen.getByRole('link', { name: '먼저 인기 작품 둘러보기' })).toHaveAttribute(
      'href',
      '/ranking',
    )
  })
})
