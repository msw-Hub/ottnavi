import { PageTitle } from '@/components/common/PageTitle'
import { ProductsScreen } from '@/features/admin/components/products/ProductsScreen'

/** SCR-14 관리자 OTT 구독상품·월 구독요금 화면(`/admin/products`, 관리자). 화면 조립은 features/admin이 맡는다(Task 029). */
export function AdminProductsPage() {
  return (
    <>
      <PageTitle name="[관리] 상품·요금" />
      <ProductsScreen />
    </>
  )
}
