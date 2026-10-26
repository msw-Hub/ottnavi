import { useEffect } from 'react'
import { useForm, useWatch } from 'react-hook-form'
import { toast } from 'sonner'
import { isApiError } from '@/api/http'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { useAddProductPrice, useUpdateProductPrice } from '@/features/admin/hooks/useAdminProducts'
import type { AdminProductPrice } from '@/api/generated/model'
import {
  checkAddPriceStartDate,
  checkEditLastPriceStartDate,
  checkMonthlyPriceText,
  PRICE_PAST_DATE_NOTICE,
  PRICE_START_DATE_MESSAGES,
} from '@/features/admin/priceRules'
import { getErrorMessage } from '@/lib/errorMessages'
import { formatWon } from '@/lib/format'

// 시작일 칸 오류로 보여 줄 서버 오류 코드(규칙 위반). 나머지 서버 오류는 폼 위쪽에 문장으로 보인다
const START_DATE_ERROR_CODES = new Set([
  'PRICE_START_DATE_DUPLICATE',
  'PRICE_START_DATE_NOT_AFTER_LAST',
  'PRICE_FIRST_START_DATE_IN_FUTURE',
])

// 입력 칸은 항상 문자열이라 문자열로 두고 제출할 때 숫자로 바꾼다
interface PriceFormValues {
  monthlyPrice: string // 월 구독요금(원)
  effectiveFrom: string // 적용 시작일('YYYY-MM-DD'). 비어 있으면 ''
}

interface PriceFormProps {
  productId: number // 줄을 추가·수정할 OTT 구독상품 ID
  sortedPrices: AdminProductPrice[] // 이 OTT 구독상품의 월 구독요금 줄(시작일 오름차순). 시작일 규칙 검사에 쓴다
  today: string // 오늘 날짜('YYYY-MM-DD')
  editingRow?: AdminProductPrice // 주면 "가장 최근 줄 수정" 모드, 없으면 "새 줄 추가" 모드
  onClose: () => void // 취소하거나 저장에 성공했을 때 폼을 닫는다
}

/**
 * 월 구독요금 이력 안에서 열리는 인라인 폼이다. 새 줄 추가와 가장 최근 줄 수정에 같이 쓴다(editingRow 유무로 구분).
 *
 * 대화상자(Dialog)가 아니라 이력 바로 아래·해당 줄 자리에 펼치는 이유: 기존 줄의 시작일/종료일을 보면서 새 시작일을 정해야 해서다.
 *
 * 시작일 오류는 "한 자리"에만 보인다: 폼 검사(순서·중복)와 서버 검사(409)가 같은 칸 아래 같은 위치에 한 문장으로 나온다.
 * 지난 날짜는 오류가 아니라 안내(info)다. 저장은 되고, "저장하면 바로 적용돼요"라고 미리 알린다.
 * 접근성: 오류가 있는 칸은 aria-invalid="true"이고 aria-describedby가 오류/안내 문장을 가리킨다. 저장에 실패하면 시작일 칸으로 포커스를 옮긴다.
 */
export function PriceForm({ productId, sortedPrices, today, editingRow, onClose }: PriceFormProps) {
  const isEditing = editingRow !== undefined
  const addPrice = useAddProductPrice()
  const updatePrice = useUpdateProductPrice()
  const isPending = addPrice.isPending || updatePrice.isPending

  const {
    register,
    handleSubmit,
    control,
    setError,
    setFocus,
    formState: { errors },
  } = useForm<PriceFormValues>({
    defaultValues: {
      monthlyPrice: editingRow ? String(editingRow.monthlyPrice) : '',
      effectiveFrom: editingRow?.effectiveFrom ?? '',
    },
  })

  // 폼이 열리면 첫 칸으로 포커스를 옮긴다. 열린 폼을 찾아 헤매지 않도록 하기 위함이다(키보드·낭독기 사용자)
  useEffect(() => {
    setFocus('monthlyPrice')
  }, [setFocus])

  // 시작일 입력값을 지켜보며 "지난 날짜" 안내를 낸다. 입력할 때마다 다시 그려지는 것은 안내가 즉시 바뀌어야 해서다
  const watchedStartDate = useWatch({ control, name: 'effectiveFrom' })
  const startCheck = isEditing
    ? checkEditLastPriceStartDate(sortedPrices, watchedStartDate, today)
    : checkAddPriceStartDate(sortedPrices, watchedStartDate, today)
  const startError = errors.effectiveFrom?.message
  const hasPastNotice = !startError && startCheck.isPastDate

  const priceError = errors.monthlyPrice?.message
  const rootError = errors.root?.message

  const submit = handleSubmit((values) => {
    const monthlyPrice = Number(values.monthlyPrice.trim())
    const body = { monthlyPrice, effectiveFrom: values.effectiveFrom }
    const callbacks = {
      onSuccess: () => {
        toast.success(
          isEditing
            ? `월 구독요금을 ${formatWon(monthlyPrice)}으로 수정했어요`
            : `월 구독요금 ${formatWon(monthlyPrice)}을 추가했어요`,
        )
        onClose()
      },
      onError: (error: unknown) => {
        const code = isApiError(error) ? error.errorCode : null
        const message = isApiError(error) ? error.userMessage : getErrorMessage(null)
        if (code !== null && START_DATE_ERROR_CODES.has(code)) {
          setError('effectiveFrom', { message }, { shouldFocus: true })
        } else {
          setError('root', { message })
        }
      },
    }
    if (editingRow)
      updatePrice.mutate({ productId, priceId: editingRow.priceId, ...body }, callbacks)
    else addPrice.mutate({ productId, ...body }, callbacks)
  })

  const idPrefix = editingRow ? `price-edit-${editingRow.priceId}` : 'price-add'
  const priceInputId = `${idPrefix}-price`
  const startInputId = `${idPrefix}-start`
  const priceErrorId = `${priceInputId}-error`
  const startMessageId = `${startInputId}-message`

  return (
    <form
      onSubmit={submit}
      noValidate
      aria-label={isEditing ? '월 구독요금 수정' : '월 구독요금 추가'}
      className="grid gap-3 rounded-xl border border-border bg-card p-4"
    >
      <h3 className="text-base font-semibold">
        {isEditing ? '월 구독요금 수정' : '월 구독요금 추가'}
      </h3>

      {rootError && (
        <p role="alert" className="text-sm text-destructive">
          {rootError}
        </p>
      )}

      <div className="grid gap-3 sm:grid-cols-2">
        <div className="grid gap-1.5">
          <Label htmlFor={priceInputId}>월 구독요금(원)</Label>
          <Input
            id={priceInputId}
            inputMode="numeric"
            autoComplete="off"
            aria-invalid={priceError ? true : undefined}
            aria-describedby={priceError ? priceErrorId : undefined}
            {...register('monthlyPrice', { validate: (v) => checkMonthlyPriceText(v) ?? true })}
          />
          {priceError && (
            <p id={priceErrorId} className="text-sm text-destructive">
              {priceError}
            </p>
          )}
        </div>

        <div className="grid gap-1.5">
          <Label htmlFor={startInputId}>적용 시작일</Label>
          <Input
            id={startInputId}
            type="date"
            aria-invalid={startError ? true : undefined}
            aria-describedby={startError || hasPastNotice ? startMessageId : undefined}
            {...register('effectiveFrom', {
              // 폼에서 먼저 같은 규칙을 검사한다. 서버(목업)도 같은 규칙을 한 번 더 검사해 409로 돌려준다
              validate: (v) => {
                const check = isEditing
                  ? checkEditLastPriceStartDate(sortedPrices, v, today)
                  : checkAddPriceStartDate(sortedPrices, v, today)
                return check.issue ? PRICE_START_DATE_MESSAGES[check.issue] : true
              },
            })}
          />
          {startError && (
            <p id={startMessageId} className="text-sm text-destructive">
              {startError}
            </p>
          )}
          {hasPastNotice && (
            <p id={startMessageId} role="status" className="text-sm text-info">
              {PRICE_PAST_DATE_NOTICE}
            </p>
          )}
        </div>
      </div>

      <div className="flex flex-wrap justify-end gap-2">
        <Button type="button" variant="outline" onClick={onClose} disabled={isPending}>
          취소
        </Button>
        <Button type="submit" disabled={isPending}>
          {isPending ? '저장하는 중…' : isEditing ? '저장' : '추가'}
        </Button>
      </div>
    </form>
  )
}
