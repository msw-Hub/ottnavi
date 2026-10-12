import { useRef, useState, type ReactNode } from 'react'
import { InfoIcon, PlusIcon } from 'lucide-react'
import { useSearchParams } from 'react-router'
import { EmptyState } from '@/components/common/EmptyState'
import { ErrorState } from '@/components/common/ErrorState'
import { LoadingState } from '@/components/common/LoadingState'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { PriceHistoryPanel } from '@/features/admin/components/products/PriceHistoryPanel'
import {
  ProductFormDialog,
  type ProductDialogState,
} from '@/features/admin/components/products/ProductFormDialog'
import { ProductTable } from '@/features/admin/components/products/ProductTable'
import { useAdminProducts } from '@/features/admin/hooks/useAdminProducts'
import { useOttServices } from '@/hooks/useOttServices'
import { getTodayDateString } from '@/lib/dateUtils'

// 고른 OTT 구독상품을 담는 주소 검색 파라미터 이름. 예: /admin/products?product=101
const SELECTED_PRODUCT_PARAM = 'product'

/**
 * SCR-14 OTT 구독상품·월 구독요금 관리 화면의 본문이다(Task 029).
 *
 * 위에서 아래로: 제목 + [OTT 구독상품 등록] → MVP 범위 안내 → OTT 구독상품 목록 → 고른 상품의 월 구독요금 이력.
 *
 * 고른 OTT 구독상품은 URL(?product=ID)에 둔다(frontend.md: 새로고침·공유 때 유지돼야 하는 화면 위치는 URL).
 * useState로 두면 새로고침하면 선택이 풀리고, 운영자가 "이 상품 이력 좀 봐" 하고 주소를 전달할 수 없다.
 * 목록에 없는 ID가 주소에 있으면 선택하지 않은 것으로 본다.
 *
 * 로딩·오류·빈 상태를 모두 둔다. 서비스 이름(services)은 보조 정보라 못 불러와도 화면을 막지 않고 "서비스 N"으로 대신 보인다.
 */
export function ProductsScreen() {
  const products = useAdminProducts()
  const services = useOttServices()
  const [searchParams, setSearchParams] = useSearchParams()
  const [dialogState, setDialogState] = useState<ProductDialogState>(null)
  const dialogOpenerRef = useRef<HTMLElement | null>(null) // 대화상자를 연 버튼. 닫힐 때 포커스를 돌려줄 곳

  // 대화상자를 연다. 누른 버튼을 기억해 두는 것은 닫힐 때 키보드 사용자가 있던 자리로 돌아가게 하려는 것이다
  function openDialog(state: ProductDialogState) {
    dialogOpenerRef.current =
      document.activeElement instanceof HTMLElement ? document.activeElement : null
    setDialogState(state)
  }

  // 오늘 날짜는 그릴 때마다 계산한다(날짜 문자열 하나라 비용이 없고, 자정을 넘긴 화면도 다음 그리기에 맞아진다)
  const today = getTodayDateString()

  const serviceNames = new Map((services.data ?? []).map((s) => [s.ottServiceId, s.name]))

  const selectedId = Number(searchParams.get(SELECTED_PRODUCT_PARAM))
  const selectedProduct = products.data?.products.find((p) => p.productId === selectedId) ?? null

  function selectProduct(productId: number) {
    setSearchParams(
      (prev) => {
        const next = new URLSearchParams(prev) // 목업 확인용 파라미터(mockScenario 등)를 지우지 않으려고 이어받는다
        next.set(SELECTED_PRODUCT_PARAM, String(productId))
        return next
      },
      { replace: true },
    )
  }

  let content: ReactNode
  if (products.isPending) {
    content = <LoadingState message="OTT 구독상품을 불러오고 있어요" />
  } else if (products.isError) {
    content = <ErrorState error={products.error} onRetry={() => products.refetch()} />
  } else if (products.data.products.length === 0) {
    content = (
      <EmptyState
        title="등록된 OTT 구독상품이 없어요"
        description="OTT 구독상품을 등록하면 월 구독요금을 관리할 수 있어요."
        action={
          <Button type="button" onClick={() => openDialog({ mode: 'create' })}>
            <PlusIcon aria-hidden="true" />
            OTT 구독상품 등록
          </Button>
        }
      />
    )
  } else {
    content = (
      <>
        <ProductTable
          products={products.data.products}
          serviceNames={serviceNames}
          selectedProductId={selectedProduct?.productId ?? null}
          today={today}
          onSelect={selectProduct}
          onEdit={(product) => openDialog({ mode: 'edit', product })}
        />
        {selectedProduct ? (
          <PriceHistoryPanel
            key={selectedProduct.productId}
            product={selectedProduct}
            serviceName={
              serviceNames.get(selectedProduct.ottServiceId) ??
              `서비스 ${selectedProduct.ottServiceId}`
            }
            today={today}
          />
        ) : (
          <p className="rounded-xl border border-dashed border-border px-4 py-6 text-center text-muted-foreground">
            OTT 구독상품을 고르면 월 구독요금 이력이 보입니다.
          </p>
        )}
      </>
    )
  }

  return (
    <section className="flex flex-col gap-5">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <h1 className="text-2xl font-semibold">OTT 구독상품 관리</h1>
        <Button type="button" onClick={() => openDialog({ mode: 'create' })}>
          <PlusIcon aria-hidden="true" />
          OTT 구독상품 등록
        </Button>
      </div>

      {/* role="note": 기본 role="alert"는 화면을 열 때마다 낭독기가 끊고 읽는다. 이 띠는 급한 알림이 아닌 범위 안내다 */}
      <Alert role="note">
        <InfoIcon aria-hidden="true" />
        <AlertDescription>
          MVP는 단품(STANDARD)만 다룹니다. 번들·광고형 OTT 구독상품은 2단계부터 등록할 수 있습니다.
        </AlertDescription>
      </Alert>

      {content}

      <ProductFormDialog
        state={dialogState}
        services={(services.data ?? []).map((s) => ({
          ottServiceId: s.ottServiceId,
          name: s.name,
        }))}
        today={today}
        onClose={() => setDialogState(null)}
        onCreated={selectProduct}
        openerRef={dialogOpenerRef}
      />
    </section>
  )
}
