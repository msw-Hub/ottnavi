import { useRef, useState, type ReactNode } from 'react'
import { InfoIcon, PlusIcon } from 'lucide-react'
import type { AdminProduct } from '@/api/generated/model'
import { useSearchParams } from 'react-router'
import { EmptyState } from '@/components/common/EmptyState'
import { ErrorState } from '@/components/common/ErrorState'
import { LoadingState } from '@/components/common/LoadingState'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { MAX_ACTIVE_BUNDLES } from '@/features/admin/bundleRules'
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

  const productList: AdminProduct[] | undefined = products.data?.products

  const selectedId = Number(searchParams.get(SELECTED_PRODUCT_PARAM))
  const selectedProduct = productList?.find((p) => p.productId === selectedId) ?? null

  // 판매 중 번들 수. 상한(13개)에 얼마나 가까운지 안내 띠에 보인다
  const activeBundleCount =
    productList?.filter((p) => p.productType === 'BUNDLE' && p.isActive).length ?? 0

  // 월 구독요금 이력 제목 아래 보조 글자. 번들은 구성 서비스를 "+"로 잇는다(예: "티빙 + 웨이브")
  function productServiceLabel(product: AdminProduct): string {
    return product.ottServiceIds.map((id) => serviceNames.get(id) ?? `서비스 ${id}`).join(' + ')
  }

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
          products={productList ?? products.data.products}
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
            // 이전: serviceNames.get(selectedProduct.ottServiceId) ?? `서비스 ${selectedProduct.ottServiceId}` (번들은 서비스가 여러 곳이라 바꿨다)
            serviceName={productServiceLabel(selectedProduct)}
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
        {/* 이전 문장: "MVP는 단품(STANDARD)만 다룹니다. 번들·광고형 OTT 구독상품은 2단계부터 등록할 수 있습니다."(Task 076에서 번들·저가형 등록이 열렸다) */}
        {/* 문단을 둘로 나누면 AlertDescription의 문단 여백 때문에 사이가 크게 벌어져 한 문단으로 둔다 */}
        <AlertDescription>
          번들은 서비스 2곳 이상을 한 번에 결제하는 상품입니다. 구성은 등록할 때만 정하고, 바꾸려면
          새로 등록한 뒤 기존 상품을 판매 종료하세요. 판매 중인 번들은{' '}
          <strong className="font-semibold text-foreground tabular-nums">
            {activeBundleCount}/{MAX_ACTIVE_BUNDLES}개
          </strong>
          입니다.
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
