import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { setupServer } from 'msw/node'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { afterAll, afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest'
import { Providers } from '@/app/providers'
import { ProductsScreen } from '@/features/admin/components/products/ProductsScreen'
import { addDays, getTodayDateString } from '@/lib/dateUtils'
import { formatIsoDate } from '@/lib/format'
import { resetMockAdminState } from '@/mocks/adminHandlers'
import { handlers } from '@/mocks/handlers'

/*
 * OTT 구독상품·월 구독요금 화면 테스트(Task 029, SCR-14): 선택 → 이력, 월 구독요금 추가·시작일 오류·지난 날짜 안내,
 * 가장 최근 줄만 수정·삭제, [요금 확인함], 등록·수정, 오류·빈 상태를 본다. API는 앱의 목업 핸들러(MSW)다.
 * 표(md 이상)와 카드(좁은 화면)가 CSS로만 갈리므로 테스트(CSS 없음)에서는 둘 다 보인다. 그래서 표 안/이력 영역 안으로 범위를 좁혀 찾는다.
 * 날짜는 fixture가 오늘 기준 상대 날짜라서 테스트도 오늘에서 계산한다.
 */

const server = setupServer(...handlers)

beforeAll(() => {
  server.listen({ onUnhandledFrame: 'error' })
  // Radix 라디오 그룹(판매 상태·유형)이 크기 감시(ResizeObserver)를 쓰는데 jsdom에는 없어 아무 일도 하지 않는 대역을 둔다
  vi.stubGlobal(
    'ResizeObserver',
    class {
      observe() {}
      unobserve() {}
      disconnect() {}
    },
  )
})
beforeEach(() => vi.stubEnv('VITE_USE_MOCK', 'true'))
afterEach(() => {
  server.resetHandlers()
  vi.unstubAllEnvs()
  resetMockAdminState()
  window.history.replaceState({}, '', '/') // 시나리오 테스트가 바꾼 주소를 되돌린다
})
afterAll(() => {
  server.close()
  vi.unstubAllGlobals()
})

const TODAY = getTodayDateString()

function renderProducts(initialPath = '/admin/products') {
  const router = createMemoryRouter([{ path: '/admin/products', element: <ProductsScreen /> }], {
    initialEntries: [initialPath],
  })
  render(
    <Providers>
      <RouterProvider router={router} />
    </Providers>,
  )
  return router
}

// 표(넓은 화면용)를 기다려 돌려준다
async function findTable() {
  return screen.findByRole('table', { name: 'OTT 구독상품 목록' })
}

// 표에서 OTT 구독상품 이름 버튼을 눌러 고르고, 이력 영역을 돌려준다
async function selectProduct(user: ReturnType<typeof userEvent.setup>, name: string) {
  const table = await findTable()
  await user.click(within(table).getByRole('button', { name }))
  return screen.findByRole('region', { name: `${name} 월 구독요금 이력` })
}

describe('OTT 구독상품 목록', () => {
  it('OTT 구독상품 12개(단품 7개 + 저가형·번들 추가 5개)와 현재 월 구독요금을 보이고, 확인일이 오래된 것엔 주의 태그가 붙는다', async () => {
    renderProducts()
    const table = await findTable()

    const rows = within(table).getAllByRole('row').slice(1) // 첫 줄은 열 제목
    expect(rows).toHaveLength(12) // 목업은 기본 7개 + Task 076 추가 상품 108~112

    const netflix = within(table).getByRole('row', { name: /넷플릭스 스탠다드/ })
    expect(within(netflix).getByText('13,500원')).toBeInTheDocument() // 예정 줄(14,500원)이 아니라 지금 적용 중인 줄

    const apple = within(table).getByRole('row', { name: /애플 TV\+/ })
    expect(within(apple).getByText(/요금 확인일 오래됨/)).toBeInTheDocument()
    expect(within(netflix).queryByText(/요금 확인일 오래됨/)).not.toBeInTheDocument()

    // 판매 종료는 글자로 구분된다
    expect(
      within(within(table).getByRole('row', { name: /왓챠 베이직/ })).getByText('판매 종료'),
    ).toBeInTheDocument()
  })

  it('고르기 전에는 안내 문장이 보이고, 고르면 주소에 ?product=가 붙고 이력이 나온다', async () => {
    const user = userEvent.setup()
    const router = renderProducts()
    await findTable()
    expect(
      screen.getByText('OTT 구독상품을 고르면 월 구독요금 이력이 보입니다.'),
    ).toBeInTheDocument()

    const panel = await selectProduct(user, '넷플릭스 스탠다드')
    expect(router.state.location.search).toBe('?product=101')
    // 줄 3개: 시작일 늦은 순으로 예정 / 현재 적용 / 지난 요금
    expect(within(panel).getByText('예정')).toBeInTheDocument()
    expect(within(panel).getByText('현재 적용')).toBeInTheDocument()
    expect(within(panel).getByText('지난 요금')).toBeInTheDocument()
    expect(within(panel).getAllByText(/~ 계속/)).toHaveLength(1) // 마지막 줄만 "계속"
  })

  it('주소에 ?product=가 있으면 새로 열어도 그 OTT 구독상품의 이력이 바로 보인다', async () => {
    renderProducts('/admin/products?product=102')
    expect(
      await screen.findByRole('region', { name: '디즈니+ 스탠다드 월 구독요금 이력' }),
    ).toBeInTheDocument()
  })
})

describe('행 눌러 고르기', () => {
  it('표의 이름 버튼이 아닌 칸(서비스 칸)을 눌러도 고르고, [수정]은 선택을 바꾸지 않고 대화상자만 연다', async () => {
    const user = userEvent.setup()
    const router = renderProducts()
    const table = await findTable()

    await user.click(
      within(within(table).getByRole('row', { name: /넷플릭스 스탠다드/ })).getByRole('cell', {
        name: '넷플릭스',
      }),
    )
    expect(router.state.location.search).toBe('?product=101')
    expect(
      await screen.findByRole('region', { name: '넷플릭스 스탠다드 월 구독요금 이력' }),
    ).toBeInTheDocument()

    await user.click(within(table).getByRole('button', { name: '디즈니+ 스탠다드 수정' }))
    expect(await screen.findByRole('dialog', { name: 'OTT 구독상품 수정' })).toBeInTheDocument()
    expect(router.state.location.search).toBe('?product=101') // 디즈니+가 선택되지 않았다
  })
})

describe('월 구독요금 추가', () => {
  it('추가하면 이력에 새 줄이 생기고 이전 줄의 종료일이 새 시작일의 전날로 바뀐다', async () => {
    const user = userEvent.setup()
    renderProducts()
    const panel = await selectProduct(user, '넷플릭스 스탠다드')

    const newStart = addDays(TODAY, 60)
    await user.click(within(panel).getByRole('button', { name: '월 구독요금 추가' }))
    const form = within(panel).getByRole('form', { name: '월 구독요금 추가' })
    await user.type(within(form).getByLabelText('월 구독요금(원)'), '15500')
    fireEvent.change(within(form).getByLabelText('적용 시작일'), { target: { value: newStart } })
    await user.click(within(form).getByRole('button', { name: '추가' }))

    expect(await within(panel).findByText('15,500원')).toBeInTheDocument()
    // 이전 마지막 줄(예정, 오늘+30일 시작)은 새 시작일의 전날까지가 된다
    const previousStart = addDays(TODAY, 30)
    expect(
      within(panel).getByText(
        `${formatIsoDate(previousStart)} ~ ${formatIsoDate(addDays(newStart, -1))}`,
      ),
    ).toBeInTheDocument()
    expect(within(panel).queryByRole('form')).not.toBeInTheDocument() // 성공하면 폼이 닫힌다
  })

  it('마지막 줄과 같은 시작일이면 오류 문구가 시작일 칸 아래에 보이고 aria-invalid가 된다', async () => {
    const user = userEvent.setup()
    renderProducts()
    const panel = await selectProduct(user, '넷플릭스 스탠다드')

    await user.click(within(panel).getByRole('button', { name: '월 구독요금 추가' }))
    const form = within(panel).getByRole('form', { name: '월 구독요금 추가' })
    await user.type(within(form).getByLabelText('월 구독요금(원)'), '15500')
    const startInput = within(form).getByLabelText('적용 시작일')
    fireEvent.change(startInput, { target: { value: addDays(TODAY, 30) } }) // 예정 줄과 같은 날
    await user.click(within(form).getByRole('button', { name: '추가' }))

    expect(
      await within(form).findByText('같은 적용 시작일의 월 구독요금이 이미 있어요.'),
    ).toBeInTheDocument()
    expect(startInput).toHaveAttribute('aria-invalid', 'true')
    expect(startInput).toHaveAccessibleDescription('같은 적용 시작일의 월 구독요금이 이미 있어요.')
    await waitFor(() => expect(startInput).toHaveFocus()) // 실패하면 포커스가 시작일 칸으로 간다
  })

  it('마지막 줄보다 이른 시작일이면 순서 오류 문구가 보인다', async () => {
    const user = userEvent.setup()
    renderProducts()
    const panel = await selectProduct(user, '넷플릭스 스탠다드')

    await user.click(within(panel).getByRole('button', { name: '월 구독요금 추가' }))
    const form = within(panel).getByRole('form', { name: '월 구독요금 추가' })
    await user.type(within(form).getByLabelText('월 구독요금(원)'), '15500')
    fireEvent.change(within(form).getByLabelText('적용 시작일'), {
      target: { value: addDays(TODAY, 20) },
    })
    await user.click(within(form).getByRole('button', { name: '추가' }))

    expect(
      await within(form).findByText('적용 시작일은 앞 줄의 시작일보다 늦어야 해요.'),
    ).toBeInTheDocument()
  })

  it('지난 날짜는 오류가 아니라 안내만 보이고 저장할 수 있다', async () => {
    const user = userEvent.setup()
    renderProducts()
    const panel = await selectProduct(user, '디즈니+ 스탠다드') // 줄 1개(시작일 오늘-180일)

    await user.click(within(panel).getByRole('button', { name: '월 구독요금 추가' }))
    const form = within(panel).getByRole('form', { name: '월 구독요금 추가' })
    const startInput = within(form).getByLabelText('적용 시작일')
    fireEvent.change(startInput, { target: { value: addDays(TODAY, -10) } })

    expect(
      await within(form).findByText('이미 지난 날짜예요. 저장하면 바로 적용돼요.'),
    ).toBeInTheDocument()
    expect(startInput).not.toHaveAttribute('aria-invalid')

    await user.type(within(form).getByLabelText('월 구독요금(원)'), '10900')
    await user.click(within(form).getByRole('button', { name: '추가' }))
    expect(await within(panel).findByText('10,900원')).toBeInTheDocument()
  })
})

describe('수정·삭제·확인', () => {
  it('가장 최근 줄에만 수정·삭제 버튼이 있다', async () => {
    const user = userEvent.setup()
    renderProducts()
    const panel = await selectProduct(user, '넷플릭스 스탠다드')

    expect(within(panel).getAllByRole('button', { name: /^수정/ })).toHaveLength(1)
    expect(within(panel).getAllByRole('button', { name: /^삭제/ })).toHaveLength(1)
    // 확인은 지금 적용 중인 줄 하나에만 있고, 지난·예정 줄에는 버튼도 "오래됨" 태그도 없다
    expect(within(panel).getAllByRole('button', { name: /^요금 확인함/ })).toHaveLength(1)
    expect(within(panel).queryByText(/요금 확인일 오래됨/)).not.toBeInTheDocument()
  })

  it('줄이 하나뿐이면 삭제 버튼 대신 이유를 알린다', async () => {
    const user = userEvent.setup()
    renderProducts()
    const panel = await selectProduct(user, '디즈니+ 스탠다드')

    expect(within(panel).queryByRole('button', { name: /^삭제/ })).not.toBeInTheDocument()
    expect(
      within(panel).getByText('월 구독요금이 하나뿐이라 삭제할 수 없어요.'),
    ).toBeInTheDocument()
  })

  it('최근 줄을 삭제하면 확인 대화상자를 거쳐 줄이 사라지고 앞 줄이 다시 "계속"이 된다', async () => {
    const user = userEvent.setup()
    renderProducts()
    const panel = await selectProduct(user, '넷플릭스 스탠다드')

    await user.click(within(panel).getByRole('button', { name: /^삭제/ }))
    const dialog = await screen.findByRole('alertdialog')
    await user.click(within(dialog).getByRole('button', { name: '삭제' }))

    await waitFor(() => expect(within(panel).queryByText('예정')).not.toBeInTheDocument())
    expect(within(panel).getAllByText(/~ 계속/)).toHaveLength(1)
  })

  it('최근 줄을 수정하면 금액이 바뀐다', async () => {
    const user = userEvent.setup()
    renderProducts()
    const panel = await selectProduct(user, '넷플릭스 스탠다드')

    await user.click(within(panel).getByRole('button', { name: /^수정/ }))
    const form = within(panel).getByRole('form', { name: '월 구독요금 수정' })
    const priceInput = within(form).getByLabelText('월 구독요금(원)')
    await user.clear(priceInput)
    await user.type(priceInput, '16000')
    await user.click(within(form).getByRole('button', { name: '저장' }))

    expect(await within(panel).findByText('16,000원')).toBeInTheDocument()
    expect(within(panel).queryByText('14,500원')).not.toBeInTheDocument()
  })

  it('오래된 줄의 금액을 수정하면 확인일도 오늘로 바뀌어 주의 태그가 사라진다', async () => {
    const user = userEvent.setup()
    renderProducts()
    const panel = await selectProduct(user, '애플 TV+')
    expect(within(panel).getByText(/요금 확인일 오래됨/)).toBeInTheDocument()

    await user.click(within(panel).getByRole('button', { name: /^수정/ }))
    const form = within(panel).getByRole('form', { name: '월 구독요금 수정' })
    const priceInput = within(form).getByLabelText('월 구독요금(원)')
    await user.clear(priceInput)
    await user.type(priceInput, '7000')
    await user.click(within(form).getByRole('button', { name: '저장' }))

    expect(await within(panel).findByText('7,000원')).toBeInTheDocument()
    expect(within(panel).queryByText(/요금 확인일 오래됨/)).not.toBeInTheDocument()
    expect(within(panel).getByText(formatIsoDate(TODAY)!)).toBeInTheDocument()
  })

  it('[요금 확인함]을 누르면 확인일이 오늘로 바뀌어 주의 태그가 사라진다', async () => {
    const user = userEvent.setup()
    renderProducts()
    const panel = await selectProduct(user, '애플 TV+')
    expect(within(panel).getByText(/요금 확인일 오래됨/)).toBeInTheDocument()

    await user.click(within(panel).getByRole('button', { name: /^요금 확인함/ }))

    await waitFor(() =>
      expect(within(panel).queryByText(/요금 확인일 오래됨/)).not.toBeInTheDocument(),
    )
    expect(within(panel).getByText(formatIsoDate(TODAY)!)).toBeInTheDocument()
    // 목록의 현재 월 구독요금 옆 태그도 함께 사라진다
    const table = await findTable()
    expect(within(table).queryByText(/요금 확인일 오래됨/)).not.toBeInTheDocument()
  })
})

describe('OTT 구독상품 등록·수정', () => {
  it('첫 월 구독요금과 함께 등록하면 목록에 생기고 바로 선택된다', async () => {
    const user = userEvent.setup()
    renderProducts()
    await findTable()

    await user.click(screen.getByRole('button', { name: 'OTT 구독상품 등록' }))
    const dialog = await screen.findByRole('dialog', { name: 'OTT 구독상품 등록' })
    await user.type(within(dialog).getByLabelText('이름'), '티빙 광고형 테스트')
    await user.selectOptions(await within(dialog).findByLabelText('서비스'), '티빙')
    await user.type(within(dialog).getByLabelText('첫 월 구독요금(원)'), '5500')
    await user.click(within(dialog).getByRole('button', { name: '등록' }))

    const panel = await screen.findByRole('region', { name: '티빙 광고형 테스트 월 구독요금 이력' })
    expect(within(panel).getByText('5,500원')).toBeInTheDocument()
    expect(
      within(await findTable()).getByRole('button', { name: /^티빙 광고형 테스트\s*\(선택됨\)$/ }),
    ).toBeInTheDocument()
  })

  it('필수 값이 비어 있으면 등록되지 않고 오류가 보인다', async () => {
    const user = userEvent.setup()
    renderProducts()
    await findTable()

    await user.click(screen.getByRole('button', { name: 'OTT 구독상품 등록' }))
    const dialog = await screen.findByRole('dialog', { name: 'OTT 구독상품 등록' })
    await user.click(within(dialog).getByRole('button', { name: '등록' }))

    expect(await within(dialog).findByText('이름을 입력해 주세요.')).toBeInTheDocument()
    expect(within(dialog).getByText('서비스를 선택해 주세요.')).toBeInTheDocument()
    expect(within(dialog).getByText('월 구독요금을 입력해 주세요.')).toBeInTheDocument()
  })

  it('번들 유형을 골라 구성 서비스 2곳을 체크하면 번들 상품으로 등록되고, 대화상자 닫기 버튼은 한국어다', async () => {
    const user = userEvent.setup()
    renderProducts()
    await findTable()

    await user.click(screen.getByRole('button', { name: 'OTT 구독상품 등록' }))
    const dialog = await screen.findByRole('dialog', { name: 'OTT 구독상품 등록' })
    // Task 076에서 번들 등록이 열렸다(이전: 라디오가 막혀 있고 "2단계부터" 태그가 붙었다)
    const bundleRadio = within(dialog).getByRole('radio', { name: /^번들/ })
    expect(bundleRadio).toBeEnabled()
    await user.click(bundleRadio)
    await user.type(within(dialog).getByLabelText('이름'), '번들 등록 테스트')
    await user.click(within(dialog).getByRole('checkbox', { name: '티빙' }))
    await user.click(within(dialog).getByRole('checkbox', { name: '웨이브' }))
    await user.type(within(dialog).getByLabelText('첫 월 구독요금(원)'), '16000')
    // 닫기 버튼의 접근 이름은 영어 Close가 아니라 한국어 "닫기"다
    expect(within(dialog).getByRole('button', { name: '닫기' })).toBeInTheDocument()
    expect(within(dialog).queryByRole('button', { name: 'Close' })).not.toBeInTheDocument()
    await user.click(within(dialog).getByRole('button', { name: '등록' }))
    expect(
      await screen.findByRole('region', { name: '번들 등록 테스트 월 구독요금 이력' }),
    ).toBeInTheDocument()
  })

  it('판매 종료로 수정하면 목록에 "판매 종료"로 보인다', async () => {
    const user = userEvent.setup()
    renderProducts()
    const table = await findTable()

    await user.click(within(table).getByRole('button', { name: '넷플릭스 스탠다드 수정' }))
    const dialog = await screen.findByRole('dialog', { name: 'OTT 구독상품 수정' })
    await user.click(within(dialog).getByRole('radio', { name: '판매 종료' }))
    await user.click(within(dialog).getByRole('button', { name: '저장' }))

    await waitFor(() =>
      expect(
        within(within(table).getByRole('row', { name: /넷플릭스 스탠다드/ })).getByText(
          '판매 종료',
        ),
      ).toBeInTheDocument(),
    )
  })
})

describe('오류·빈 상태', () => {
  it('?mockScenario=error면 오류 안내와 다시 시도 버튼이 보인다', async () => {
    window.history.replaceState({}, '', '/?mockScenario=error')
    renderProducts()
    expect(
      await screen.findByText('불러오지 못했습니다', {}, { timeout: 8000 }),
    ).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '다시 시도' })).toBeInTheDocument()
  }, 15000)

  it('?mockScenario=empty면 빈 상태 안내와 등록 버튼이 보인다', async () => {
    window.history.replaceState({}, '', '/?mockScenario=empty')
    renderProducts()
    expect(await screen.findByText('등록된 OTT 구독상품이 없어요')).toBeInTheDocument()
    expect(screen.getAllByRole('button', { name: 'OTT 구독상품 등록' })).toHaveLength(2)
  })
})

describe('키보드', () => {
  it('등록 대화상자를 Esc로 닫으면 포커스가 열었던 버튼으로 돌아온다', async () => {
    const user = userEvent.setup()
    renderProducts()
    await findTable()

    const opener = screen.getByRole('button', { name: 'OTT 구독상품 등록' })
    await user.click(opener)
    await screen.findByRole('dialog', { name: 'OTT 구독상품 등록' })
    await user.keyboard('{Escape}')

    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
    await waitFor(() => expect(opener).toHaveFocus())
  })
})
