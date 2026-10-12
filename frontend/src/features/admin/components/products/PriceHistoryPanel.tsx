import { useEffect, useRef, useState } from 'react'
import {
  CalendarClockIcon,
  CheckIcon,
  HistoryIcon,
  PencilIcon,
  PlusIcon,
  Trash2Icon,
} from 'lucide-react'
import { toast } from 'sonner'
import { isApiError } from '@/api/http'
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from '@/components/ui/alert-dialog'
import { Button } from '@/components/ui/button'
import { AdminStatusPill } from '@/features/admin/components/AdminStatusPill'
import { PriceForm } from '@/features/admin/components/products/PriceForm'
import { StaleVerifiedTag } from '@/features/admin/components/StaleVerifiedTag'
import {
  useDeleteProductPrice,
  useVerifyProductPrice,
} from '@/features/admin/hooks/useAdminProducts'
import type { AdminProduct, AdminProductPrice } from '@/api/generated/model'
import {
  getPriceEndDate,
  getPriceRowKind,
  isVerifiedStale,
  sortPricesAscending,
  type PriceRowKind,
} from '@/features/admin/priceRules'
import { getErrorMessage } from '@/lib/errorMessages'
import { formatIsoDate, formatWon } from '@/lib/format'

interface PriceHistoryPanelProps {
  product: AdminProduct // 이력을 볼 OTT 구독상품
  serviceName: string // 속한 서비스 이름
  today: string // 오늘 날짜('YYYY-MM-DD')
}

// 줄의 적용 구분 표시. 색만이 아니라 아이콘·글자·테두리 모양으로 구분한다
const KIND_PILL: Record<PriceRowKind, Parameters<typeof AdminStatusPill>[0]> = {
  CURRENT: { icon: CheckIcon, label: '현재 적용', tone: 'success', size: 'sm' },
  UPCOMING: { icon: CalendarClockIcon, label: '예정', tone: 'progress', size: 'sm' },
  PAST: { icon: HistoryIcon, label: '지난 요금', tone: 'neutral', size: 'sm' },
}

// 오류를 토스트로 알린다(행 동작은 줄 자리에 오류를 둘 곳이 마땅치 않다)
function toastError(error: unknown) {
  toast.error(isApiError(error) ? error.userMessage : getErrorMessage(null))
}

/**
 * 고른 OTT 구독상품의 월 구독요금 이력이다(SCR-14).
 *
 * 줄 = (월 구독요금, 적용 시작일, 마지막 확인일). 적용 종료일은 저장하지 않고 "다음 줄 시작일의 전날"로 계산해 보인다(마지막 줄은 "계속").
 * 시작일이 늦은 줄이 위에 온다(최근 것이 먼저 보이고, 수정·삭제 가능한 줄이 맨 위다).
 * 수정·삭제는 가장 최근 줄에만 있다: 과거 줄을 고치면 앞뒤 줄의 종료일 계산이 어긋나 이력이 틀어지기 때문이다.
 * 줄이 하나뿐이면 삭제 대신 이유를 글자로 알린다(월 구독요금이 없는 OTT 구독상품이 되지 않게 하는 규칙).
 * [요금 확인함]과 "확인일 오래됨" 태그는 지금 적용 중인 줄에만 있다(지난·예정 줄은 확인 대상이 아니라 날짜 값만 보인다).
 * [요금 확인함]은 마지막 확인일만 오늘로 바꾸고, 확인일이 90일을 넘으면 StaleVerifiedTag가 붙는다.
 *
 * 포커스: 폼을 닫으면 열었던 버튼으로 돌아간다. 수정 폼은 그 줄의 [수정] 버튼 자리를 대신 차지해 사라지므로,
 * 닫힌 뒤 같은 id의 버튼을 찾아 포커스를 준다(useEffect).
 */
export function PriceHistoryPanel({ product, serviceName, today }: PriceHistoryPanelProps) {
  const verify = useVerifyProductPrice()
  const remove = useDeleteProductPrice()

  const [isAdding, setIsAdding] = useState(false)
  const [editingPriceId, setEditingPriceId] = useState<number | null>(null)
  const [deleteTarget, setDeleteTarget] = useState<AdminProductPrice | null>(null)
  const addButtonRef = useRef<HTMLButtonElement>(null)
  // 폼이 닫힌 뒤 포커스를 돌려줄 대상. 'add'는 추가 버튼, 숫자는 그 줄의 수정 버튼
  const returnFocusTo = useRef<'add' | number | null>(null) // ref인 이유: 화면에 그릴 값이 아니라 effect가 읽고 비우는 메모라 바뀌어도 다시 그릴 필요가 없다

  // 다른 OTT 구독상품을 고르면 열려 있던 폼이 사라져야 한다(다른 상품의 줄에 잘못 저장하지 않게). 그래서 부모(ProductsScreen)가
  // 이 컴포넌트에 key={product.id}를 줘서 상품이 바뀔 때 상태째 새로 만든다.

  // 폼이 닫힌 직후(다음 그리기 뒤)에 포커스를 되돌린다. 닫는 시점에는 대상 버튼이 아직 없을 수 있어 effect에서 처리한다
  useEffect(() => {
    if (returnFocusTo.current === null || isAdding || editingPriceId !== null) return
    if (returnFocusTo.current === 'add') addButtonRef.current?.focus()
    else document.getElementById(`edit-price-${returnFocusTo.current}`)?.focus()
    returnFocusTo.current = null
  }, [isAdding, editingPriceId])

  const sortedAsc = sortPricesAscending(product.prices)
  const latestId = sortedAsc[sortedAsc.length - 1]?.priceId
  const hasOnlyOneRow = sortedAsc.length === 1
  const rowsNewestFirst = [...sortedAsc].reverse()

  function closeAddForm() {
    setIsAdding(false)
    returnFocusTo.current = 'add'
  }

  function closeEditForm(priceId: number) {
    setEditingPriceId(null)
    returnFocusTo.current = priceId
  }

  return (
    <section aria-labelledby="price-history-heading" className="grid gap-3">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <div className="grid gap-0.5">
          <h2 id="price-history-heading" className="text-lg font-semibold">
            {product.name} 월 구독요금 이력
          </h2>
          <p className="text-sm text-muted-foreground">{serviceName}</p>
        </div>
        <Button
          ref={addButtonRef}
          type="button"
          variant="outline"
          disabled={isAdding || editingPriceId !== null}
          onClick={() => setIsAdding(true)}
        >
          <PlusIcon aria-hidden="true" />월 구독요금 추가
        </Button>
      </div>

      {isAdding && (
        <PriceForm
          productId={product.productId}
          sortedPrices={sortedAsc}
          today={today}
          onClose={closeAddForm}
        />
      )}

      {/* 열 제목은 넓은 화면에서만 한 번 보이고, 좁은 화면에서는 칸마다 라벨이 붙는다 */}
      <div
        aria-hidden="true"
        className="hidden grid-cols-[1.4fr_1.6fr_1.4fr_1.6fr] gap-4 px-4 text-sm text-muted-foreground md:grid"
      >
        <span>월 구독요금</span>
        <span>적용 기간</span>
        <span>마지막 확인일</span>
        <span>동작</span>
      </div>

      <ul className="grid gap-3">
        {rowsNewestFirst.map((row) => {
          const index = sortedAsc.findIndex((r) => r.priceId === row.priceId)
          const kind = getPriceRowKind(sortedAsc, index, today)
          const endDate = getPriceEndDate(sortedAsc, index)
          const isLatest = row.priceId === latestId

          if (editingPriceId === row.priceId) {
            return (
              <li key={row.priceId}>
                <PriceForm
                  productId={product.productId}
                  sortedPrices={sortedAsc}
                  today={today}
                  editingRow={row}
                  onClose={() => closeEditForm(row.priceId)}
                />
              </li>
            )
          }

          return (
            <li
              key={row.priceId}
              className="grid gap-3 rounded-xl border border-border bg-card p-4 md:grid-cols-[1.4fr_1.6fr_1.4fr_1.6fr] md:items-start md:gap-4"
            >
              <div className="grid gap-1">
                <span className="text-sm text-muted-foreground md:sr-only">월 구독요금</span>
                <span className="text-base font-bold tabular-nums">
                  {formatWon(row.monthlyPrice)}
                </span>
                <AdminStatusPill {...KIND_PILL[kind]} className="w-fit" />
              </div>

              <div className="grid gap-1">
                <span className="text-sm text-muted-foreground md:sr-only">적용 기간</span>
                <span>
                  {formatIsoDate(row.effectiveFrom)} ~ {endDate ? formatIsoDate(endDate) : '계속'}
                </span>
              </div>

              <div className="grid content-start gap-1">
                <span className="text-sm text-muted-foreground md:sr-only">마지막 확인일</span>
                {kind === 'CURRENT' && isVerifiedStale(row.lastVerifiedAt, today) ? (
                  <StaleVerifiedTag lastVerifiedDate={row.lastVerifiedAt} className="w-fit" />
                ) : (
                  <span>{formatIsoDate(row.lastVerifiedAt)}</span>
                )}
              </div>

              <div className="flex flex-wrap items-center gap-2">
                {kind === 'CURRENT' && (
                  <Button
                    type="button"
                    variant="outline"
                    size="sm"
                    disabled={verify.isPending}
                    onClick={() =>
                      verify.mutate(
                        { productId: product.productId, priceId: row.priceId },
                        {
                          onSuccess: () => toast.success('요금 확인일을 오늘로 갱신했어요'),
                          onError: toastError,
                        },
                      )
                    }
                  >
                    <CheckIcon aria-hidden="true" />
                    요금 확인함
                    <span className="sr-only"> ({formatWon(row.monthlyPrice)})</span>
                  </Button>
                )}
                {isLatest && (
                  <Button
                    id={`edit-price-${row.priceId}`}
                    type="button"
                    variant="outline"
                    size="sm"
                    disabled={isAdding || editingPriceId !== null}
                    onClick={() => setEditingPriceId(row.priceId)}
                  >
                    <PencilIcon aria-hidden="true" />
                    수정
                    <span className="sr-only"> ({formatWon(row.monthlyPrice)})</span>
                  </Button>
                )}
                {isLatest && !hasOnlyOneRow && (
                  <Button
                    type="button"
                    variant="destructive"
                    size="sm"
                    disabled={remove.isPending}
                    onClick={() => setDeleteTarget(row)}
                  >
                    <Trash2Icon aria-hidden="true" />
                    삭제
                    <span className="sr-only"> ({formatWon(row.monthlyPrice)})</span>
                  </Button>
                )}
                {isLatest && hasOnlyOneRow && (
                  <p className="text-sm text-muted-foreground">
                    월 구독요금이 하나뿐이라 삭제할 수 없어요.
                  </p>
                )}
              </div>
            </li>
          )
        })}
      </ul>

      <AlertDialog
        open={deleteTarget !== null}
        onOpenChange={(isOpen) => {
          if (!isOpen) setDeleteTarget(null)
        }}
      >
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>가장 최근 월 구독요금을 삭제할까요?</AlertDialogTitle>
            <AlertDialogDescription>
              {deleteTarget
                ? `${formatWon(deleteTarget.monthlyPrice)} (${formatIsoDate(deleteTarget.effectiveFrom)}부터) 줄이 지워지고, 앞 줄이 다시 "계속" 적용돼요.`
                : ''}
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>취소</AlertDialogCancel>
            <AlertDialogAction
              onClick={() => {
                if (!deleteTarget) return
                remove.mutate(
                  { productId: product.productId, priceId: deleteTarget.priceId },
                  {
                    onSuccess: () => toast.success('월 구독요금을 삭제했어요'),
                    onError: toastError,
                  },
                )
              }}
            >
              삭제
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </section>
  )
}
