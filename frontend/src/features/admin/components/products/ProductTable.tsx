import type { MouseEvent } from 'react'
import { BanIcon, CheckIcon, PencilIcon } from 'lucide-react'
import { cn } from 'cn'
import { Button } from '@/components/ui/button'
import {
  Table,
  TableBody,
  TableCaption,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'
import { AdminStatusPill } from '@/features/admin/components/AdminStatusPill'
import { StaleVerifiedTag } from '@/features/admin/components/StaleVerifiedTag'
import type { AdminProduct } from '@/api/generated/model'
import { getCurrentPrice, isVerifiedStale, sortPricesAscending } from '@/features/admin/priceRules'
import { formatWon } from '@/lib/format'

interface ProductTableProps {
  products: AdminProduct[] // 보여 줄 OTT 구독상품 목록
  serviceNames: Map<number, string> // 서비스 ID → 서비스 이름. 아직 못 불러왔으면 비어 있다(이때는 "서비스 N"으로 보인다)
  selectedProductId: number | null // 지금 고른(월 구독요금 이력을 보고 있는) OTT 구독상품 ID. 없으면 null
  today: string // 오늘 날짜('YYYY-MM-DD'). 현재 적용 요금과 "확인일 오래됨" 판정 기준
  onSelect: (productId: number) => void // 이름 버튼을 눌러 OTT 구독상품을 고른다
  onEdit: (product: AdminProduct) => void // [수정]을 눌러 이름·판매 상태 수정 대화상자를 연다
}

// 판매 상태 배지. 색만이 아니라 아이콘과 글자, 테두리 모양(실선/점선)으로 구분한다
function SalesStatusPill({ isActive }: { isActive: boolean }) {
  return isActive ? (
    <AdminStatusPill icon={CheckIcon} label="판매 중" tone="outline" size="sm" />
  ) : (
    <AdminStatusPill icon={BanIcon} label="판매 종료" tone="neutral" size="sm" />
  )
}

// 현재 월 구독요금 칸의 내용: 금액 + (확인일이 오래되면) 주의 태그. 적용 중인 줄이 없으면 안내 글자
function CurrentPriceCell({ product, today }: { product: AdminProduct; today: string }) {
  const current = getCurrentPrice(sortPricesAscending(product.prices), today)
  if (!current) return <span className="text-muted-foreground">적용 중인 요금 없음</span>
  return (
    <span className="flex flex-wrap items-center gap-x-2 gap-y-1">
      <span className="font-semibold tabular-nums">{formatWon(current.monthlyPrice)}</span>
      {isVerifiedStale(current.lastVerifiedAt, today) && (
        <StaleVerifiedTag lastVerifiedDate={current.lastVerifiedAt} />
      )}
    </span>
  )
}

/**
 * OTT 구독상품 목록이다(SCR-14). 넓은 화면(md 이상)은 표, 좁은 화면은 카드 목록으로 바뀐다.
 *
 * 표와 카드를 둘 다 그리고 CSS(hidden / md:hidden)로 하나만 보이게 하는 이유: 375px에서 5열 표는 가로로 넘쳐 스크롤이 생긴다.
 * display:none인 쪽은 화면 낭독기에도 읽히지 않아 같은 내용이 두 번 들리지 않는다.
 * (테스트처럼 CSS가 없는 환경에서는 두 벌이 다 보이므로 within으로 범위를 좁혀 찾는다.)
 *
 * 고르는 방법: 이름 칸을 <button>으로 둔다. 행 전체를 클릭 영역으로 만들면 키보드로 고를 수 없고 [수정]과 클릭이 겹친다.
 * 고른 행은 색만이 아니라 왼쪽 막대 + aria-current="true" + 낭독기용 "선택됨" 글자로 알린다.
 */
export function ProductTable({
  products,
  serviceNames,
  selectedProductId,
  today,
  onSelect,
  onEdit,
}: ProductTableProps) {
  const serviceName = (ottServiceId: number) =>
    serviceNames.get(ottServiceId) ?? `서비스 ${ottServiceId}`

  // 행·카드의 빈 곳을 눌러도 고른다(사용자 요청 2026-10-11). 이름 버튼은 키보드·낭독기용으로 그대로 두고 aria-current도 그쪽 기준이다.
  // 행 안의 버튼([수정], 이름 버튼)을 누른 클릭은 여기서 무시한다: 그 버튼이 자기 동작을 하고, 선택이 겹쳐 두 번 일어나지 않게 한다.
  // 글자를 드래그해 선택하던 중의 클릭도 선택으로 치지 않는다(복사하려는 동작을 방해하지 않으려는 것).
  const handleRowClick = (productId: number) => (event: MouseEvent<HTMLElement>) => {
    if ((event.target as HTMLElement).closest('button, a')) return
    if (window.getSelection()?.toString()) return
    onSelect(productId)
  }

  // 고른 행/카드 왼쪽 막대. 색 토큰(--primary)만 쓴다
  const selectedBar = 'data-[selected=true]:shadow-[inset_4px_0_0_0_var(--primary)]'

  const nameButton = (product: AdminProduct) => (
    <button
      type="button"
      onClick={() => onSelect(product.productId)}
      className="rounded-sm text-left font-semibold underline-offset-4 hover:underline focus-visible:ring-3 focus-visible:ring-ring/50 focus-visible:outline-none"
    >
      {product.name}
      {product.productId === selectedProductId && <span className="sr-only"> (선택됨)</span>}
    </button>
  )

  const editButton = (product: AdminProduct) => (
    <Button
      type="button"
      variant="outline"
      size="sm"
      onClick={() => onEdit(product)}
      aria-label={`${product.name} 수정`}
    >
      <PencilIcon aria-hidden="true" />
      수정
    </Button>
  )

  return (
    <>
      {/* 넓은 화면: 표 */}
      <div className="hidden rounded-xl border border-border md:block">
        <Table>
          <TableCaption className="sr-only">OTT 구독상품 목록</TableCaption>
          <TableHeader>
            <TableRow>
              <TableHead>OTT 구독상품</TableHead>
              <TableHead>서비스</TableHead>
              <TableHead>판매 상태</TableHead>
              <TableHead>현재 월 구독요금</TableHead>
              <TableHead>
                <span className="sr-only">동작</span>
              </TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {products.map((product) => {
              const isSelected = product.productId === selectedProductId
              return (
                <TableRow
                  key={product.productId}
                  data-selected={isSelected}
                  aria-current={isSelected ? 'true' : undefined}
                  onClick={handleRowClick(product.productId)}
                  className={cn('cursor-pointer', selectedBar, isSelected && 'bg-muted/50')}
                >
                  <TableCell>{nameButton(product)}</TableCell>
                  <TableCell>{serviceName(product.ottServiceId)}</TableCell>
                  <TableCell>
                    <SalesStatusPill isActive={product.isActive} />
                  </TableCell>
                  <TableCell>
                    <CurrentPriceCell product={product} today={today} />
                  </TableCell>
                  <TableCell className="text-right">{editButton(product)}</TableCell>
                </TableRow>
              )
            })}
          </TableBody>
        </Table>
      </div>

      {/* 좁은 화면: 카드 목록 */}
      <ul aria-label="OTT 구독상품 목록" className="grid gap-3 md:hidden">
        {products.map((product) => {
          const isSelected = product.productId === selectedProductId
          return (
            <li
              key={product.productId}
              data-selected={isSelected}
              aria-current={isSelected ? 'true' : undefined}
              onClick={handleRowClick(product.productId)}
              className={cn(
                'grid cursor-pointer gap-2 rounded-xl border border-border bg-card p-4 hover:bg-muted/50',
                selectedBar,
              )}
            >
              <div className="flex flex-wrap items-center justify-between gap-2">
                {nameButton(product)}
                <SalesStatusPill isActive={product.isActive} />
              </div>
              <p className="text-sm text-muted-foreground">{serviceName(product.ottServiceId)}</p>
              <div className="flex flex-wrap items-center justify-between gap-2">
                <div className="grid gap-0.5 text-sm">
                  <span className="text-muted-foreground">현재 월 구독요금</span>
                  <CurrentPriceCell product={product} today={today} />
                </div>
                {editButton(product)}
              </div>
            </li>
          )
        })}
      </ul>
    </>
  )
}
