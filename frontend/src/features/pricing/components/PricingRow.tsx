import { CircleCheckIcon, GiftIcon, RotateCcwIcon } from 'lucide-react'
import { useForm } from 'react-hook-form'
import { toast } from 'sonner'
import { isApiError } from '@/api/http'
import { DataInsufficientTag } from '@/components/common/DataInsufficientTag'
import { SUBSCRIPTION_STATUS_LABELS } from '@/components/common/displayTypes'
import { PriceSourceLabel } from '@/components/common/PriceDisplay'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { useUpdatePricing } from '@/features/pricing/hooks/usePricing'
import type { PricingService } from '@/features/pricing/mockTypes'
import { formatWon } from '@/lib/format'

// 이 화면의 입력 폼 값. 입력 칸은 항상 문자열이라 문자열로 두고 제출할 때 숫자로 바꾼다
interface PriceFormValues {
  price: string // 내가 정한 월 요금(원). 비어 있으면 기본 요금으로 되돌린다는 뜻이다
}

interface PricingRowProps {
  service: PricingService // 요금 한 줄(상품, 기본 요금, 내 금액, 적용 금액, 이용 상태)
  serviceName: string // 서비스 이름(예: "넷플릭스"). 서비스 목록 API에서 온다
  isInsufficientData: boolean // 데이터 부족 서비스(쿠팡플레이)인지
  isLocked: boolean // 플랜 계산 중이라 금액을 바꾸지 못하게 잠글지(계산 도중 값이 바뀌면 결과가 어긋난다)
}

// 숫자 칸에 넣을 글자. 고친 금액이 없으면 비운다(비어 있음 = 기본 요금 사용)
function toInputText(userPrice: number | null): string {
  return userPrice === null ? '' : String(userPrice)
}

/**
 * 요금 확인 화면(SCR-09)의 서비스 한 줄이다. 넓은 화면(md 이상)에서는 표처럼 열이 맞고, 좁은 화면에서는 카드로 쌓인다.
 *
 * 표(<table>)를 쓰지 않고 한 벌의 목록 마크업을 CSS grid로 두 가지 모양에 쓰는 이유: 표와 카드를 따로 그리면 입력 칸이 두 벌이 되어
 * id·낭독기·폼 상태가 겹친다. 열 제목은 넓은 화면에서만 위에 한 번 보이고, 칸 라벨은 좁은 화면에서만 보인다(md:sr-only로 낭독기에는 항상 읽힘).
 *
 * 금액 입력: 0 이상의 정수(원). 비운 채로 "적용"을 누르면 고친 금액을 지우고 기본 요금으로 되돌린다(PATCH monthlyPrice: null).
 * 적용된 금액 옆에는 출처 라벨(기본 요금 / 사용자 수정)을 붙여, 이 금액이 요금표 값인지 내가 고친 값인지 알 수 있게 한다.
 */
export function PricingRow({
  service,
  serviceName,
  isInsufficientData,
  isLocked,
}: PricingRowProps) {
  const update = useUpdatePricing()
  const {
    register,
    handleSubmit,
    reset,
    formState: { errors, isDirty },
  } = useForm<PriceFormValues>({ defaultValues: { price: toInputText(service.userPrice) } })

  const inputId = `price-${service.ottServiceId}`
  const errorId = `${inputId}-error`
  const isFreeOrSubscribed =
    service.subscriptionStatus === 'SUBSCRIBED' || service.subscriptionStatus === 'FREE'

  // 제출: 비어 있으면 기본 요금 복원(null), 값이 있으면 그 금액으로 바꾼다. 형식 검사는 register의 validate가 먼저 한다
  const submit = handleSubmit(({ price }) => {
    const text = price.trim()
    update.mutate(
      { ottServiceId: service.ottServiceId, monthlyPrice: text === '' ? null : Number(text) },
      {
        onSuccess: (res) => {
          reset({ price: toInputText(res.data.userPrice) })
          toast.success(
            res.data.userPrice === null
              ? `${serviceName} 요금을 기본 요금으로 되돌렸어요`
              : `${serviceName} 요금을 ${formatWon(res.data.userPrice)}으로 바꿨어요`,
          )
        },
      },
    )
  })

  // "기본 요금으로 되돌리기" 버튼: 입력 칸을 비우고 바로 되돌린다
  function handleRestore() {
    update.mutate(
      { ottServiceId: service.ottServiceId, monthlyPrice: null },
      {
        onSuccess: () => {
          reset({ price: '' })
          toast.success(`${serviceName} 요금을 기본 요금으로 되돌렸어요`)
        },
      },
    )
  }

  return (
    <li className="grid gap-3 rounded-xl border border-border bg-card p-4 md:grid-cols-[minmax(0,1.4fr)_minmax(0,0.8fr)_minmax(0,1.6fr)_minmax(0,1fr)] md:items-start md:gap-4">
      {/* 서비스·상품 */}
      <div className="grid gap-1">
        <p className="flex flex-wrap items-center gap-x-2 gap-y-1 text-base font-bold">
          {serviceName}
          {isInsufficientData && <DataInsufficientTag />}
        </p>
        <p className="text-sm text-muted-foreground">{service.productName}</p>
        {/* 이용 상태: 구독 중·무료 이용이면 이번 달 비용이 달라질 수 있어 알린다. 아이콘 + 글자 */}
        {isFreeOrSubscribed && (
          <p className="inline-flex w-fit items-center gap-1 text-sm font-semibold text-info">
            {service.subscriptionStatus === 'SUBSCRIBED' ? (
              <CircleCheckIcon aria-hidden="true" className="size-4" />
            ) : (
              <GiftIcon aria-hidden="true" className="size-4" />
            )}
            {SUBSCRIPTION_STATUS_LABELS[service.subscriptionStatus]}
          </p>
        )}
      </div>

      {/* 기본 요금(관리자가 등록한 요금표 값) */}
      <div className="grid gap-0.5">
        <span className="text-sm text-muted-foreground md:sr-only">기본 요금</span>
        <span className="text-base font-semibold tabular-nums">
          {formatWon(service.adminPrice)}
        </span>
      </div>

      {/* 내 금액 입력 */}
      <form onSubmit={submit} noValidate className="grid gap-1.5">
        <Label htmlFor={inputId} className="md:sr-only">
          내 금액 ({serviceName})
        </Label>
        <div className="flex items-center gap-2">
          <Input
            id={inputId}
            type="number"
            inputMode="numeric"
            min={0}
            step={1}
            placeholder={`기본 ${service.adminPrice}`}
            disabled={isLocked || update.isPending}
            aria-invalid={errors.price ? true : undefined}
            aria-describedby={errors.price ? errorId : undefined}
            className="h-11 min-w-0 flex-1 tabular-nums"
            {...register('price', {
              // 0 이상 정수만 허용한다. 비어 있는 것은 "기본 요금으로 되돌리기"라 통과시킨다
              validate: (value) =>
                value.trim() === '' ||
                /^\d+$/.test(value.trim()) ||
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
        </div>
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
        {service.userPrice !== null ? (
          <Button
            type="button"
            variant="ghost"
            disabled={isLocked || update.isPending}
            onClick={handleRestore}
            className="h-11 w-fit justify-start px-2 text-sm"
          >
            <RotateCcwIcon aria-hidden="true" />
            기본 요금으로 되돌리기
          </Button>
        ) : (
          <p className="text-sm text-muted-foreground">비워서 적용하면 기본 요금을 써요.</p>
        )}
      </form>

      {/* 적용 금액과 출처. 계산에는 이 금액이 쓰인다 */}
      <div className="grid justify-items-start gap-1">
        <span className="text-sm text-muted-foreground md:sr-only">계산에 쓰는 금액</span>
        <span className="text-xl font-extrabold tabular-nums">
          {formatWon(service.appliedPrice)}
        </span>
        <PriceSourceLabel priceSource={service.priceSource} />
      </div>
    </li>
  )
}
