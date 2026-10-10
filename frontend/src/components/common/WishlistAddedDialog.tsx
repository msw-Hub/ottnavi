import { useEffect, useState } from 'react'
import { CalculatorIcon } from 'lucide-react'
import { Link } from 'react-router'
import { PosterImage } from '@/components/common/PosterImage'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogDescription, DialogTitle } from '@/components/ui/dialog'
import {
  closeWishlistAddedDialog,
  useWishlistAddedDialogInfo,
} from '@/hooks/useWishlistAddedDialog'
import { withJosa } from '@/lib/josa'
import { getMockRequestParams } from '@/lib/mockScenario'

// 찜 목록 화면 경로
const WISHLIST_PATH = '/wishlist'

/**
 * 찜을 추가한 직후 뜨는 선택지 팝업이다(쇼핑몰의 "장바구니에 담았어요" 안내와 같은 흐름).
 * 앱 루트(RootLayout)에 한 번만 마운트하고, 열림 상태는 hooks/useWishlistAddedDialog가 관리한다.
 *
 * - 모바일(640px 미만): 화면 아래에서 올라오는 바텀 시트. 엄지 손가락이 닿는 곳에 두 버튼이 있고 375px에서도 폭이 꽉 찬다.
 *   데스크톱(sm 이상): 화면 가운데 작은 카드.
 * - 접근성은 Radix Dialog 기본을 따른다: 포커스 가두기, Esc로 닫기, 닫힌 뒤 처음 눌렀던 찜 버튼으로 포커스 복귀, 제목·설명 연결.
 *   기본 우상단 X 버튼은 영어 라벨("Close")이라 쓰지 않고, "더 둘러보기"가 닫기 역할을 한다(바깥 클릭·Esc도 가능).
 * - 두 버튼 모두 높이 44px(h-11)로 터치하기 쉽게 했고, 색만으로 구분하지 않도록 주 버튼에는 계산기 아이콘을 둔다.
 * - '찜 목록 보고 계산하기'의 이동 주소에는 목업 확인용 파라미터(mockScenario·mockVariant·mockVariantWatch)를 이어 붙인다.
 *   검색어 같은 다른 쿼리는 찜 목록과 무관해 가져가지 않는다. 운영(목업 아님)에서는 getMockRequestParams가 빈 객체다.
 */
export function WishlistAddedDialog() {
  const openInfo = useWishlistAddedDialogInfo()
  // 이 컴포넌트가 사라지면(앱 종료·테스트 정리) 열림 상태도 함께 닫는다. 열림 상태가 모듈에 있어 두면 다음 화면·테스트로 새어 나가기 때문이다
  useEffect(() => closeWishlistAddedDialog, [])
  // 닫힌 뒤 사라지는 애니메이션(약 0.1초) 동안 "을(를) 찜했어요"처럼 이름이 빈 문장이 비치지 않도록 마지막 내용을 기억한다.
  // useEffect 없이 렌더 중에 새 값이 오면 바로 갱신하는 것이 React가 권하는 "props에서 파생된 상태" 패턴이다
  const [shownInfo, setShownInfo] = useState(openInfo)
  if (openInfo !== null && openInfo !== shownInfo) setShownInfo(openInfo)
  const info = shownInfo

  const search = new URLSearchParams(getMockRequestParams()).toString()
  const wishlistTo = { pathname: WISHLIST_PATH, search: search ? `?${search}` : '' }

  return (
    <Dialog
      open={openInfo !== null}
      onOpenChange={(isOpen) => {
        if (!isOpen) closeWishlistAddedDialog()
      }}
    >
      <DialogContent
        showCloseButton={false}
        // 모바일은 아래 붙은 시트(위쪽만 둥글게, 안전 영역 여백), sm 이상은 기본 가운데 카드로 되돌린다
        className="top-auto bottom-0 left-0 max-w-none translate-x-0 translate-y-0 gap-5 rounded-b-none p-5 pb-[max(1.25rem,env(safe-area-inset-bottom))] data-open:slide-in-from-bottom-8 sm:top-1/2 sm:bottom-auto sm:left-1/2 sm:max-w-sm sm:-translate-x-1/2 sm:-translate-y-1/2 sm:rounded-xl sm:pb-5 sm:data-open:slide-in-from-bottom-0"
      >
        <div className="flex items-center gap-4">
          {/* 썸네일은 장식이다(작품 이름은 옆 제목 글자가 이미 알려 준다). 낭독기가 "포스터 이미지 없음"을 따로 읽지 않게 숨긴다 */}
          <div aria-hidden="true" className="w-16 shrink-0">
            <PosterImage title={info?.name ?? ''} posterUrl={info?.posterUrl ?? null} />
          </div>
          <div className="grid min-w-0 gap-1.5">
            <DialogTitle className="text-lg leading-snug font-bold break-keep">
              {/* 조사는 작품 이름의 받침에 맞춘다(기생충을 / 더 글로리를) */}
              {withJosa(info?.name ?? '', '을/를')} 찜했어요
            </DialogTitle>
            <DialogDescription>
              찜 목록에서 우선순위를 정하고 구독 플랜을 계산해 볼 수 있어요.
            </DialogDescription>
          </div>
        </div>

        <div className="grid gap-2 sm:grid-cols-2">
          {/* 모바일은 주 버튼이 위(엄지 가까이), 데스크톱은 왼쪽에 닫기·오른쪽에 주 버튼이 오는 일반적인 순서 */}
          <Button
            type="button"
            variant="outline"
            className="h-11 text-base max-sm:order-2"
            onClick={closeWishlistAddedDialog}
          >
            더 둘러보기
          </Button>
          <Button asChild className="h-11 text-base max-sm:order-1">
            <Link to={wishlistTo} onClick={closeWishlistAddedDialog}>
              <CalculatorIcon aria-hidden="true" />찜 목록 보고 계산하기
            </Link>
          </Button>
        </div>
      </DialogContent>
    </Dialog>
  )
}
