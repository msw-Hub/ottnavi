import { fireEvent, render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { ApiError } from '@/api/http'
import { DataAsOf } from '@/components/common/DataAsOf'
import { EmptyState } from '@/components/common/EmptyState'
import { ErrorState } from '@/components/common/ErrorState'
import { EstimatedTag } from '@/components/common/EstimatedTag'
import { LoadingState } from '@/components/common/LoadingState'
import { PosterCard } from '@/components/common/PosterCard'
import { PosterImage } from '@/components/common/PosterImage'
import { PriceDisplay } from '@/components/common/PriceDisplay'
import { ProviderStatusBadge } from '@/components/common/ProviderStatusBadge'
import { ServiceStatusRow, type ServiceStatusEntry } from '@/components/common/ServiceStatusRow'
import { SourceAttribution } from '@/components/common/SourceAttribution'
import { DEFAULT_ERROR_MESSAGE, getErrorMessage } from '@/lib/errorMessages'

/*
 * 공통 표시 컴포넌트 테스트(Task 026).
 * 색·클래스 이름 같은 구현 세부가 아니라 "사용자가 화면에서 읽는 글자"를 기준으로 확인한다(RTL 관점, frontend.md).
 * 특히 제공 상태 3가지는 색 없이 글자만으로 구분되어야 하므로, 글자로 찾을 수 있는지가 핵심이다.
 */

describe('ProviderStatusBadge', () => {
  it.each([
    { status: 'AVAILABLE', label: '있음' },
    { status: 'NOT_AVAILABLE', label: '없음' },
    { status: 'UNKNOWN', label: '모름' },
  ] as const)('$status는 "$label" 글자로 보인다(색 없이도 읽힌다)', ({ status, label }) => {
    const { container } = render(<ProviderStatusBadge status={status} />)
    // 화면에 보이는 글자 전체가 정확히 이 상태 이름이다(아이콘은 낭독기에서 숨겨 글자만 남는다)
    expect(container).toHaveTextContent(new RegExp(`^${label}$`))
    expect(screen.getByText(label)).toBeVisible()
  })

  it('세 상태의 글자가 서로 다르다', () => {
    render(
      <>
        <ProviderStatusBadge status="AVAILABLE" />
        <ProviderStatusBadge status="NOT_AVAILABLE" />
        <ProviderStatusBadge status="UNKNOWN" />
      </>,
    )
    expect(screen.getByText('있음')).toBeInTheDocument()
    expect(screen.getByText('없음')).toBeInTheDocument()
    expect(screen.getByText('모름')).toBeInTheDocument()
  })
})

describe('ServiceStatusRow', () => {
  const entries: ServiceStatusEntry[] = [
    { service: { ottServiceId: 1, name: '넷플릭스', dataQuality: 'NORMAL' }, status: 'AVAILABLE' },
    { service: { ottServiceId: 6, name: '왓챠', dataQuality: 'NORMAL' }, status: 'NOT_AVAILABLE' },
    {
      service: { ottServiceId: 7, name: '쿠팡플레이', dataQuality: 'INSUFFICIENT' },
      status: 'UNKNOWN',
    },
  ]

  it('서비스마다 이름과 제공 상태 글자를 한 줄씩 보인다', () => {
    render(<ServiceStatusRow entries={entries} heading="구독 제공 상태" />)
    const rows = screen.getAllByRole('listitem')
    expect(rows).toHaveLength(3)
    expect(rows[0]).toHaveTextContent('넷플릭스있음')
    expect(rows[1]).toHaveTextContent('왓챠없음')
    expect(rows[2]).toHaveTextContent(/쿠팡플레이.*모름/)
  })

  it('데이터 충분도가 INSUFFICIENT인 서비스(쿠팡플레이)에만 "데이터 부족"을 보인다', () => {
    render(<ServiceStatusRow entries={entries} />)
    const coupangRow = screen.getByText('쿠팡플레이').closest('li')
    expect(coupangRow).not.toBeNull()
    expect(within(coupangRow!).getByText('데이터 부족')).toBeInTheDocument()
    // 다른 서비스에는 붙지 않는다(한 번만 나온다)
    expect(screen.getAllByText('데이터 부족')).toHaveLength(1)
  })
})

describe('PosterCard', () => {
  it('포스터 주소가 없으면 "포스터 없음" 대체 표시를 보인다', () => {
    render(<PosterCard title="예시 영화" posterUrl={null} />)
    expect(screen.getByRole('img', { name: '포스터 이미지 없음' })).toBeInTheDocument()
    expect(screen.getByText('포스터 없음')).toBeInTheDocument()
    expect(screen.getByText('예시 영화')).toBeInTheDocument()
  })

  it('포스터 주소가 있으면 제목이 담긴 대체 텍스트로 이미지를 그린다', () => {
    // jsdom은 이미지를 실제로 내려받지 않으므로 외부 요청이 나가지 않는다
    render(<PosterCard title="오징어 게임" posterUrl="https://example.invalid/poster.jpg" />)
    expect(screen.getByRole('img', { name: '오징어 게임 포스터' })).toBeInTheDocument()
  })

  it('이미지를 불러오지 못하면 대체 표시로 바뀐다', () => {
    render(<PosterCard title="오징어 게임" posterUrl="https://example.invalid/poster.jpg" />)
    fireEvent.error(screen.getByRole('img', { name: '오징어 게임 포스터' }))
    expect(screen.getByRole('img', { name: '포스터 이미지 없음' })).toBeInTheDocument()
    expect(screen.queryByRole('img', { name: '오징어 게임 포스터' })).not.toBeInTheDocument()
  })
})

describe('PosterImage', () => {
  it('포스터 주소가 있으면 제목이 담긴 대체 텍스트로 이미지를 그리고 제목 글자는 따로 보이지 않는다', () => {
    render(<PosterImage title="오징어 게임" posterUrl="https://example.invalid/poster.jpg" />)
    expect(screen.getByRole('img', { name: '오징어 게임 포스터' })).toBeInTheDocument()
    expect(screen.queryByText('오징어 게임')).not.toBeInTheDocument()
  })

  it('포스터 주소가 없으면 "포스터 없음" 대체 표시를 보인다', () => {
    render(<PosterImage title="예시 영화" posterUrl={null} />)
    expect(screen.getByRole('img', { name: '포스터 이미지 없음' })).toBeInTheDocument()
    expect(screen.getByText('포스터 없음')).toBeInTheDocument()
  })

  it('이미지를 불러오지 못하면 대체 표시로 바뀐다', () => {
    render(<PosterImage title="오징어 게임" posterUrl="https://example.invalid/poster.jpg" />)
    fireEvent.error(screen.getByRole('img', { name: '오징어 게임 포스터' }))
    expect(screen.getByRole('img', { name: '포스터 이미지 없음' })).toBeInTheDocument()
    expect(screen.queryByRole('img', { name: '오징어 게임 포스터' })).not.toBeInTheDocument()
  })
})

describe('PriceDisplay', () => {
  it.each([
    { priceSource: 'ADMIN', label: '기본 요금' },
    { priceSource: 'USER_OVERRIDE', label: '사용자 수정' },
    { priceSource: 'ALREADY_PAID', label: '이미 결제' },
    { priceSource: 'FREE', label: '무료' },
  ] as const)('가격 출처 $priceSource는 "$label" 라벨로 보인다', ({ priceSource, label }) => {
    render(<PriceDisplay amount={13500} priceSource={priceSource} />)
    expect(screen.getByText(label)).toBeInTheDocument()
  })

  it('금액은 formatWon 형식("13,500원")과 기간으로 보인다', () => {
    const { container } = render(<PriceDisplay amount={13500} priceSource="ADMIN" />)
    expect(container).toHaveTextContent('13,500원/월')
  })
})

describe('SourceAttribution', () => {
  it('JustWatch 출처를 새 탭 외부 링크로 보인다', () => {
    render(<SourceAttribution />)
    const link = screen.getByRole('link', { name: /JustWatch/ })
    expect(link).toHaveAttribute('href', 'https://www.justwatch.com/')
    expect(link).toHaveAttribute('target', '_blank')
    expect(link).toHaveAttribute('rel', 'noopener noreferrer')
  })
})

describe('DataAsOf', () => {
  it('올바른 날짜는 "2026년 10월 9일" 형식으로 보인다', () => {
    render(<DataAsOf value="2026-10-09" />)
    expect(screen.getByText('2026년 10월 9일')).toBeInTheDocument()
  })

  it.each([
    { label: '없는 날짜', value: '2026-02-30' },
    { label: '빈 문자열', value: '' },
    { label: '형식이 다른 문자열', value: 'abc' },
    { label: 'null', value: null },
  ])('잘못된 값($label)이면 "기준일 정보 없음"을 보인다', ({ value }) => {
    render(<DataAsOf value={value} />)
    expect(screen.getByText('기준일 정보 없음')).toBeInTheDocument()
    expect(screen.queryByText(/데이터 기준일/)).not.toBeInTheDocument()
  })
})

describe('EstimatedTag', () => {
  it('"추정" 글자를 보인다', () => {
    render(<EstimatedTag />)
    expect(screen.getByText('추정')).toBeInTheDocument()
  })
})

describe('로딩·오류·빈 상태', () => {
  it('로딩 상태는 status 역할로 안내 문장을 보인다', () => {
    render(<LoadingState message="제공처 정보를 불러오는 중입니다" />)
    expect(screen.getByRole('status')).toHaveTextContent('제공처 정보를 불러오는 중입니다')
  })

  it('오류 상태는 ApiError의 화면 메시지를 보인다', () => {
    const error = new ApiError({
      kind: 'problem',
      status: 502,
      errorCode: 'EXTERNAL_API_ERROR',
      title: 'Bad Gateway',
      detail: '서버 내부 설명(화면에 보이면 안 됨)',
      fieldErrors: [],
      userMessage: getErrorMessage('EXTERNAL_API_ERROR'),
    })
    render(<ErrorState error={error} />)
    const alert = screen.getByRole('alert')
    expect(alert).toHaveTextContent('불러오지 못했습니다')
    expect(alert).toHaveTextContent(getErrorMessage('EXTERNAL_API_ERROR'))
    // 서버가 쓴 detail은 화면 문구로 쓰지 않는다
    expect(alert).not.toHaveTextContent('서버 내부 설명')
  })

  it('ApiError가 아닌 오류는 기본 메시지를 보이고 개발자용 내용은 숨긴다', () => {
    render(<ErrorState error={new Error('undefined is not a function')} />)
    const alert = screen.getByRole('alert')
    expect(alert).toHaveTextContent(DEFAULT_ERROR_MESSAGE)
    expect(alert).not.toHaveTextContent('undefined is not a function')
  })

  it('message를 주면 그 문장을 우선해 보인다', () => {
    render(<ErrorState error={new Error('x')} message="찜 목록을 불러오지 못했습니다." />)
    expect(screen.getByRole('alert')).toHaveTextContent('찜 목록을 불러오지 못했습니다.')
  })

  it('onRetry가 있으면 "다시 시도" 버튼을 누를 때 호출한다', async () => {
    const user = userEvent.setup()
    const handleRetry = vi.fn()
    render(<ErrorState onRetry={handleRetry} />)
    await user.click(screen.getByRole('button', { name: '다시 시도' }))
    expect(handleRetry).toHaveBeenCalledTimes(1)
  })

  it('onRetry가 없으면 "다시 시도" 버튼을 숨긴다', () => {
    render(<ErrorState />)
    expect(screen.queryByRole('button', { name: '다시 시도' })).not.toBeInTheDocument()
  })

  it('빈 상태는 제목과 안내 문장을 보인다', () => {
    render(
      <EmptyState
        title="검색 결과가 없습니다"
        description="제목을 다르게 입력하거나 원제로 검색해 보세요."
      />,
    )
    expect(screen.getByText('검색 결과가 없습니다')).toBeInTheDocument()
    expect(screen.getByText('제목을 다르게 입력하거나 원제로 검색해 보세요.')).toBeInTheDocument()
  })
})
