import { useState } from 'react'
import { LayersIcon, PencilIcon, RotateCcwIcon, TagIcon } from 'lucide-react'
import { useForm } from 'react-hook-form'
import { toast } from 'sonner'
import type { ServicePrice } from '@/api/generated/model'
import { isApiError } from '@/api/http'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { useUpdatePricing } from '@/features/pricing/hooks/usePricing'
import { formatWon } from '@/lib/format'

// 이 화면의 입력 폼 값. 입력 칸은 항상 문자열이라 문자열로 두고 제출할 때 숫자로 바꾼다
interface PriceFormValues {
  price: string // 내가 정한 월 요금(원). 비어 있으면 기본 요금으로 되돌린다는 뜻이다
}

// 요금제 이름 옆 작은 표시: 저가형·번들. 색에 기대지 않고 아이콘과 글자를 함께 쓴다
function KindMark({ price }: { price: ServicePrice }) {
  const isBundle = price.productType === 'BUNDLE'
  if (!isBundle && !price.isLowCost) return null
  const Icon = isBundle ? LayersIcon : TagIcon
  return (
    <span className="inline-flex items-center gap-1 text-xs font-bold whitespace-nowrap text-muted-foreground">
      <Icon aria-hidden="true" className="size-3.5" />
      {isBundle ? (price.isLowCost ? '번들·저가형' : '번들') : '저가형'}
    </span>
  )
}

// "계산에 사용"/"안 씀" 태그(UseMark)는 뺐다. 처음 보이는 줄은 전부 계산에 쓰는 요금제라 모든 카드에 같은 태그가 붙어 정보가 없고,
// 안 쓰는 줄은 접기 버튼 문구("계산에 안 쓰는 요금제 N개")가 이미 같은 뜻을 말한다. 계산 기준은 화면 위 모드 안내 띠가 알린다.

interface PriceEditorProps {
  price: ServicePrice
  isLocked: boolean // 플랜 계산 중이라 금액을 바꾸지 못하게 잠글지(계산 도중 값이 바뀌면 결과가 어긋난다)
  collapsible: boolean // true면 좁은 화면에서 "내 금액 고치기"를 눌러야 입력 칸이 열린다(넓은 화면은 항상 보임)
}

/**
 * 내 금액 입력(적용·되돌리기). 0 이상의 정수(원)만 받고, 비운 채 "적용"하면 고친 금액을 지우고 기본 요금으로 되돌린다
 * (DELETE /me/service-prices/{productId}).
 * 입력 id는 상품 ID로 만든다: 한 서비스에 요금제가 여럿(표준·저가형)이고 번들은 서비스 ID가 null이라 서비스 ID로는 겹친다.
 */
function PriceEditor({ price, isLocked, collapsible }: PriceEditorProps) {
  const update = useUpdatePricing()
  const [open, setOpen] = useState(false)
  const {
    register,
    handleSubmit,
    reset,
    formState: { errors, isDirty },
  } = useForm<PriceFormValues>({
    defaultValues: { price: price.userPrice === null ? '' : String(price.userPrice) },
  })
  const inputId = `price-${price.productId}`
  const errorId = `${inputId}-error` // 입력 오류 문장. aria-describedby로 입력 칸과 이어 낭독기가 함께 읽는다

  const submit = handleSubmit(({ price: text }) => {
    const trimmed = text.trim()
    update.mutate(
      { productId: price.productId, monthlyPrice: trimmed === '' ? null : Number(trimmed) },
      {
        // 되돌리기(DELETE, 204)는 본문이 없어 null이 온다
        onSuccess: (updated) => {
          reset({ price: updated?.userPrice == null ? '' : String(updated.userPrice) })
          toast.success(
            updated?.userPrice == null
              ? '기본 요금으로 되돌렸어요'
              : `${formatWon(updated.userPrice)}으로 바꿨어요`,
          )
        },
      },
    )
  })

  const restore = () =>
    update.mutate(
      { productId: price.productId, monthlyPrice: null },
      {
        onSuccess: () => {
          reset({ price: '' })
          toast.success('기본 요금으로 되돌렸어요')
        },
      },
    )

  return (
    <div className="grid gap-1">
      {collapsible && (
        <Button
          type="button"
          variant="ghost"
          aria-expanded={open}
          onClick={() => setOpen((v) => !v)}
          className="h-11 w-fit px-2 text-sm md:hidden"
        >
          <PencilIcon aria-hidden="true" />내 금액 고치기
        </Button>
      )}
      <form
        onSubmit={submit}
        noValidate
        className={`${collapsible && !open ? 'hidden md:flex' : 'flex'} items-center gap-2`}
      >
        {/* 이름표에 상품 이름을 쓴다. 한 서비스에 요금제가 여럿이면 서비스 이름만으로는 낭독기로 구분되지 않는다 */}
        <Label htmlFor={inputId} className="sr-only">
          내 금액 ({price.productName})
        </Label>
        <Input
          id={inputId}
          type="number"
          inputMode="numeric"
          min={0}
          step={1}
          placeholder={`기본 ${price.adminPrice}`}
          disabled={isLocked || update.isPending}
          aria-invalid={errors.price ? true : undefined}
          aria-describedby={errors.price ? errorId : undefined}
          className="h-11 w-28 min-w-0 flex-1 tabular-nums md:w-28 md:flex-none"
          {...register('price', {
            // 0 이상 정수만 허용한다. 비어 있는 것은 "기본 요금으로 되돌리기"라 통과시킨다
            validate: (v) =>
              v.trim() === '' ||
              /^\d+$/.test(v.trim()) ||
              '0 이상의 정수(원 단위)로 입력해 주세요.',
          })}
        />
        <span className="shrink-0 text-sm">원</span>
        <Button
          type="submit"
          variant="outline"
          disabled={isLocked || update.isPending || !isDirty}
          className="h-11 shrink-0 px-4"
        >
          {update.isPending ? '적용 중' : '적용'}
        </Button>
        {price.userPrice !== null && (
          <Button
            type="button"
            variant="ghost"
            aria-label="기본 요금으로 되돌리기"
            title="기본 요금으로 되돌리기"
            disabled={isLocked || update.isPending}
            onClick={restore}
            className="size-11 shrink-0 p-0"
          >
            <RotateCcwIcon aria-hidden="true" />
          </Button>
        )}
      </form>
      {errors.price && (
        <p id={errorId} role="alert" className="text-sm text-destructive">
          {errors.price.message}
        </p>
      )}
      {update.isError && !errors.price && (
        <p role="alert" className="text-sm text-destructive">
          {isApiError(update.error) ? update.error.userMessage : '금액을 바꾸지 못했어요.'}
        </p>
      )}
    </div>
  )
}

// 적용 금액(계산에 쓰는 금액). 내가 고친 금액이면 "내가 고친 금액"을 덧붙인다(기본 요금일 때는 아무것도 붙이지 않는다)
function AppliedPrice({ price, large }: { price: ServicePrice; large: boolean }) {
  return (
    <div className="grid gap-0.5">
      <span
        className={`${large ? 'text-xl font-extrabold' : 'text-base font-semibold text-muted-foreground'} tabular-nums`}
      >
        {formatWon(price.appliedPrice)}
      </span>
      {price.priceSource === 'USER_OVERRIDE' && (
        <span className="inline-flex items-center gap-1 text-xs font-semibold">
          <PencilIcon aria-hidden="true" className="size-3" />
          내가 고친 금액
        </span>
      )}
    </div>
  )
}

interface PricingRowProps {
  price: ServicePrice // 요금제 한 줄(기본 요금, 내 금액, 적용 금액)
  title: string // 요금제 이름(번들은 구성 서비스를 "+"로 이은 이름)
  head?: React.ReactNode // 카드의 첫 줄에만 서비스 이름·구독 상태를 얹는다
  isLocked: boolean
  isPrimary: boolean // 계산에 쓰는 요금제로 위쪽에 크게 보이는 줄인지(false면 접힌 안의 줄)
}

/**
 * 요금 확인 화면(SCR-09)의 요금제 한 줄이다. 넓은 화면(md 이상)에서는 [이름 | 적용 금액 | 입력] 세 칸으로 맞고,
 * 좁은 화면에서는 위아래로 쌓인다. 한 벌의 마크업을 CSS grid로 두 모양에 써서 입력 칸이 두 벌이 되지 않는다.
 * 서비스 카드(PricingGroupCard) 안에서 쓰이며, 줄마다 반복하던 확인일·안내 문장은 목록 위 한 줄과 카드 경고로 모았다.
 */
export function PricingRow({ price, title, head, isLocked, isPrimary }: PricingRowProps) {
  return (
    <div className="grid gap-2 md:grid-cols-[minmax(0,1.5fr)_8rem_auto] md:items-center md:gap-4">
      <div className="grid gap-0.5">
        {head}
        <p
          className={`flex flex-wrap items-center gap-x-2 gap-y-0.5 ${isPrimary ? 'text-sm text-muted-foreground' : 'text-sm'}`}
        >
          {title}
          <KindMark price={price} />
        </p>
      </div>
      <AppliedPrice price={price} large={isPrimary} />
      <PriceEditor price={price} isLocked={isLocked} collapsible={!isPrimary} />
    </div>
  )
}

// 이전(Task 076 1차안): 요금제마다 카드 한 장을 그렸다(서비스·상품 / 기본 요금 / 내 금액 / 계산에 쓰는 금액 4열, 줄별 요금 확인일·안내 문장·ProductKindTags·CalcCandidateTag).
// 시안 A(서비스당 카드 한 장, 안 쓰는 요금제는 접기)로 확정해 PricingGroupCard가 묶고 이 파일은 줄만 맡는다.
