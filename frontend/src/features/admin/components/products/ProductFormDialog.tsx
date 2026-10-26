import { useState, type RefObject } from 'react'
import { Controller, useForm, useWatch } from 'react-hook-form'
import { toast } from 'sonner'
import { isApiError } from '@/api/http'
import { Button } from '@/components/ui/button'
import {
  Dialog,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { CloseLabeledDialogContent } from '@/components/common/CloseLabeledDialogContent'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { RadioGroup, RadioGroupItem } from '@/components/ui/radio-group'
import {
  BUNDLE_LIMIT_MESSAGES,
  checkBundleServices,
  LOW_COST_HELP,
} from '@/features/admin/bundleRules'
import { BundleServicesField } from '@/features/admin/components/products/ProductCompositionFields'
import { ProductCompositionSummary } from '@/features/admin/components/products/ProductCompositionSummary'
import {
  useCreateAdminProduct,
  useUpdateAdminProduct,
} from '@/features/admin/hooks/useAdminProducts'
import type { AdminProduct, ProductType } from '@/api/generated/model'
import {
  checkFirstPriceStartDate,
  checkMonthlyPriceText,
  PRICE_PAST_DATE_NOTICE,
  PRICE_START_DATE_MESSAGES,
} from '@/features/admin/priceRules'
import { getErrorMessage } from '@/lib/errorMessages'

// 대화상자가 열린 목적. null이면 닫힘
export type ProductDialogState = { mode: 'create' } | { mode: 'edit'; product: AdminProduct } | null

interface ServiceOption {
  ottServiceId: number // 서비스 ID
  name: string // 서비스 이름
}

interface ProductFormDialogProps {
  state: ProductDialogState // 열림 상태와 목적(등록/수정)
  services: ServiceOption[] // 등록 때 고를 수 있는 서비스 목록
  today: string // 오늘 날짜('YYYY-MM-DD')
  onClose: () => void // 닫기(취소·저장 성공·Esc)
  onCreated: (productId: number) => void // 등록에 성공했을 때. 새 OTT 구독상품을 바로 골라 이력을 보여 준다
  openerRef: RefObject<HTMLElement | null> // 대화상자를 연 버튼. 닫히면 포커스를 여기로 돌려준다
}

// "저가형" 체크 칸 모양. 등록·수정 대화상자가 같이 쓴다. 줄 전체가 label이라 글자를 눌러도 켜지고, 높이 44px(min-h-11)는 터치 크기 기준이다
const LOW_COST_LABEL_CLASS =
  'flex min-h-11 cursor-pointer items-start gap-2.5 rounded-lg border border-input px-3 py-2.5 has-[:checked]:border-primary has-[:disabled]:cursor-not-allowed has-[:disabled]:opacity-50 has-[:focus-visible]:ring-3 has-[:focus-visible]:ring-ring/50'

/**
 * OTT 구독상품 등록·수정 대화상자다(SCR-14).
 *
 * 대화상자(Dialog)를 쓴 이유: 등록은 이름·유형·서비스·첫 월 구독요금을 한 번에 받는 짧은 작업이라 목록 위에 띄워 집중시키는 편이 낫다.
 * Radix Dialog가 포커스를 가두고 Esc로 닫고, 닫히면 열었던 버튼으로 포커스를 돌려준다.
 * 안쪽 폼은 열릴 때마다 새로 만들어져(DialogContent가 닫히면 사라진다) 이전 입력이 남지 않는다.
 */
export function ProductFormDialog({
  state,
  services,
  today,
  onClose,
  onCreated,
  openerRef,
}: ProductFormDialogProps) {
  return (
    <Dialog
      open={state !== null}
      onOpenChange={(isOpen) => {
        if (!isOpen) onClose()
      }}
    >
      <CloseLabeledDialogContent
        // 번들 구성(서비스 7곳 두 열)이 들어가 이전 sm:max-w-md(448px)보다 넓혔다. 넘치면 대화상자 안에서 세로로 스크롤된다
        className="max-h-[calc(100dvh-2rem)] overflow-y-auto sm:max-w-lg"
        // Radix는 닫힐 때 DialogTrigger로 포커스를 돌려준다. 우리는 Trigger 없이 상태로 여닫아 돌려줄 곳을 모르므로 직접 연 버튼으로 돌려준다
        onCloseAutoFocus={(event) => {
          event.preventDefault()
          openerRef.current?.focus()
        }}
      >
        {state?.mode === 'create' && (
          <CreateForm services={services} today={today} onClose={onClose} onCreated={onCreated} />
        )}
        {state?.mode === 'edit' && (
          <EditForm product={state.product} services={services} onClose={onClose} />
        )}
      </CloseLabeledDialogContent>
    </Dialog>
  )
}

// 서버 오류를 폼 위 문장으로 바꾼다
function errorText(error: unknown): string {
  return isApiError(error) ? error.userMessage : getErrorMessage(null)
}

// 서버 400 VALIDATION_FAILED의 fieldErrors에서 이 필드 오류를 찾는다. 없으면 undefined
function findFieldError(error: unknown, field: string) {
  if (!isApiError(error) || error.errorCode !== 'VALIDATION_FAILED') return undefined
  return error.fieldErrors.find((fieldError) => fieldError.field === field)
}

interface CreateFormValues {
  name: string
  ottServiceId: string // 단품의 서비스. select 값은 문자열이라 제출할 때 숫자로 바꾼다
  isLowCost: boolean // 저가형 체크
  monthlyPrice: string
  effectiveFrom: string
}

function CreateForm({
  services,
  today,
  onClose,
  onCreated,
}: Pick<ProductFormDialogProps, 'services' | 'today' | 'onClose' | 'onCreated'>) {
  const create = useCreateAdminProduct()
  // 유형과 번들 구성은 RHF 밖에서 둔다: 번들 구성은 체크박스 묶음이라 값이 배열이고, 유형에 따라 검사할 칸이 바뀐다
  const [productType, setProductType] = useState<ProductType>('SINGLE') // 고른 유형
  const [bundleServiceIds, setBundleServiceIds] = useState<number[]>([]) // 번들 구성(서비스 ID 오름차순)
  const [bundleError, setBundleError] = useState<string | null>(null) // 번들 구성 검증 문장
  const isBundle = productType === 'BUNDLE'
  const {
    register,
    handleSubmit,
    control,
    setError,
    formState: { errors },
  } = useForm<CreateFormValues>({
    defaultValues: {
      name: '',
      ottServiceId: '',
      isLowCost: false,
      monthlyPrice: '',
      effectiveFrom: today,
    },
  })

  const watchedStart = useWatch({ control, name: 'effectiveFrom' })
  const startCheck = checkFirstPriceStartDate(watchedStart, today)
  const startError = errors.effectiveFrom?.message
  const hasPastNotice = !startError && startCheck.isPastDate

  const submit = handleSubmit(
    (values) => {
      // 1. 번들은 구성부터 검사한다(서버도 같은 규칙으로 다시 검사한다)
      if (isBundle) {
        const message = checkBundleServices(bundleServiceIds)
        if (message) {
          setBundleError(message)
          return
        }
      }
      // 2. 등록 API로 보낸다. 단품은 서비스 1개, 번들은 고른 서비스 전부
      create.mutate(
        {
          name: values.name.trim(),
          productType,
          ottServiceIds: isBundle ? bundleServiceIds : [Number(values.ottServiceId)],
          isLowCost: values.isLowCost,
          monthlyPrice: Number(values.monthlyPrice.trim()),
          effectiveFrom: values.effectiveFrom,
        },
        {
          onSuccess: (product) => {
            toast.success('OTT 구독상품을 등록했어요')
            onClose()
            onCreated(product.productId)
          },
          onError: (error) => {
            // 3. 서버 거절을 알맞은 칸에 붙인다: 첫 요금 시작일 → 시작일 칸 / 번들 13개 상한(field productType) → 폼 위 /
            //    구성 개수·중복(field ottServiceIds) → 구성 칸 또는 서비스 칸 / 그 밖 → 폼 위
            const serviceError = findFieldError(error, 'ottServiceIds')
            if (isApiError(error) && error.errorCode === 'PRICE_FIRST_START_DATE_IN_FUTURE') {
              setError('effectiveFrom', { message: error.userMessage }, { shouldFocus: true })
            } else if (findFieldError(error, 'productType')) {
              setError('root', { message: BUNDLE_LIMIT_MESSAGES.create })
            } else if (serviceError && isBundle) {
              setBundleError(serviceError.message)
            } else if (serviceError) {
              setError('ottServiceId', { message: serviceError.message }, { shouldFocus: true })
            } else {
              setError('root', { message: errorText(error) })
            }
          },
        },
      )
    },
    () => {
      // 다른 칸에 오류가 있어 위 단계가 돌지 않을 때도 번들 구성 오류를 함께 보인다(한 번에 모든 오류를 고치게)
      if (isBundle) setBundleError(checkBundleServices(bundleServiceIds))
    },
  )

  return (
    <form onSubmit={submit} noValidate className="grid gap-4">
      <DialogHeader>
        <DialogTitle>OTT 구독상품 등록</DialogTitle>
        <DialogDescription>첫 월 구독요금과 적용 시작일을 함께 입력해 주세요.</DialogDescription>
      </DialogHeader>

      {errors.root?.message && (
        <p role="alert" className="text-sm text-destructive">
          {errors.root.message}
        </p>
      )}

      <div className="grid gap-1.5">
        <Label htmlFor="product-name">이름</Label>
        <Input
          id="product-name"
          autoComplete="off"
          placeholder={isBundle ? '예: 티빙·웨이브 더블 이용권' : '예: 넷플릭스 스탠다드'}
          aria-invalid={errors.name ? true : undefined}
          aria-describedby={errors.name ? 'product-name-error' : undefined}
          {...register('name', { validate: (v) => v.trim() !== '' || '이름을 입력해 주세요.' })}
        />
        {errors.name && (
          <p id="product-name-error" className="text-sm text-destructive">
            {errors.name.message}
          </p>
        )}
      </div>

      {/* 유형을 서비스보다 먼저 묻는다: 번들이면 서비스 칸 대신 구성 목록이 나와, 고르는 순서가 화면 순서와 맞는다.
          이전(MVP): 번들 라디오가 막혀 있고 "2단계부터" 태그가 붙어 있었다(Task 076에서 열림) */}
      <fieldset className="grid gap-1.5">
        <legend className="mb-1.5 text-sm font-medium">유형</legend>
        <RadioGroup
          value={productType}
          onValueChange={(value) => {
            setProductType(value as ProductType)
            setBundleError(null)
          }}
          aria-label="OTT 구독상품 유형"
          className="flex flex-wrap gap-x-4 gap-y-1"
        >
          <div className="flex min-h-11 items-center gap-2">
            <RadioGroupItem id="product-type-single" value="SINGLE" />
            <Label htmlFor="product-type-single">단품 (서비스 1곳)</Label>
          </div>
          <div className="flex min-h-11 items-center gap-2">
            <RadioGroupItem id="product-type-bundle" value="BUNDLE" />
            <Label htmlFor="product-type-bundle">번들 (서비스 2곳 이상)</Label>
          </div>
        </RadioGroup>
      </fieldset>

      {isBundle ? (
        <div className="grid gap-2">
          <BundleServicesField
            idPrefix="product-create"
            services={services}
            value={bundleServiceIds}
            onChange={(next) => {
              setBundleServiceIds(next)
              setBundleError(null)
            }}
            error={bundleError ?? undefined}
            disabled={create.isPending}
          />
          {/* 구성은 등록 뒤 바꿀 수 없다(2026-10-17 사용자 확정, 계약에 구성 편집 API 없음). 바뀐 상품은 새로 등록하고 기존 상품은 판매 종료한다 */}
          <p className="text-sm break-keep text-muted-foreground">
            등록한 구성은 바꿀 수 없어요. 바꾸려면 새 상품을 등록하고 기존 상품은 판매 종료하세요.
          </p>
        </div>
      ) : (
        <div className="grid gap-1.5">
          <Label htmlFor="product-service">서비스</Label>
          <select
            id="product-service"
            aria-invalid={errors.ottServiceId ? true : undefined}
            aria-describedby={errors.ottServiceId ? 'product-service-error' : undefined}
            className="h-11 w-full rounded-lg border border-input bg-background px-2 text-sm text-foreground outline-none focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/50 aria-invalid:border-destructive"
            {...register('ottServiceId', {
              // 번들에서는 서비스 칸이 사라지고 구성 목록이 대신하므로 검사하지 않는다
              validate: (v) => isBundle || v !== '' || '서비스를 선택해 주세요.',
            })}
          >
            <option value="">서비스를 선택해 주세요</option>
            {services.map((service) => (
              <option key={service.ottServiceId} value={service.ottServiceId}>
                {service.name}
              </option>
            ))}
          </select>
          {errors.ottServiceId && (
            <p id="product-service-error" className="text-sm text-destructive">
              {errors.ottServiceId.message}
            </p>
          )}
        </div>
      )}

      {/* 저가형 체크(Task 076). 이전(Task 075 1차안): 단품은 "등급" select(일반/광고형), 번들은 서비스마다 등급 select였다.
          저가형은 요금 분류일 뿐 볼 수 있는 작품을 줄이지 않아(2026-10-17 확정) 상품 단위 체크 하나로 바꿨다 */}
      <label htmlFor="product-low-cost" className={LOW_COST_LABEL_CLASS}>
        <input
          id="product-low-cost"
          type="checkbox"
          aria-describedby="product-low-cost-help"
          disabled={create.isPending}
          className="mt-0.5 size-4.5 shrink-0 accent-primary focus-visible:outline-none"
          {...register('isLowCost')}
        />
        <span className="grid gap-0.5">
          <span className="text-sm font-medium">저가형 요금제</span>
          <span id="product-low-cost-help" className="text-sm break-keep text-muted-foreground">
            {LOW_COST_HELP}
          </span>
        </span>
      </label>

      <div className="grid gap-4 sm:grid-cols-2">
        <div className="grid gap-1.5">
          <Label htmlFor="product-price">첫 월 구독요금(원)</Label>
          <Input
            id="product-price"
            inputMode="numeric"
            autoComplete="off"
            aria-invalid={errors.monthlyPrice ? true : undefined}
            aria-describedby={errors.monthlyPrice ? 'product-price-error' : undefined}
            {...register('monthlyPrice', { validate: (v) => checkMonthlyPriceText(v) ?? true })}
          />
          {errors.monthlyPrice && (
            <p id="product-price-error" className="text-sm text-destructive">
              {errors.monthlyPrice.message}
            </p>
          )}
        </div>
        <div className="grid gap-1.5">
          <Label htmlFor="product-start">적용 시작일</Label>
          <Input
            id="product-start"
            type="date"
            aria-invalid={startError ? true : undefined}
            aria-describedby={startError || hasPastNotice ? 'product-start-message' : undefined}
            {...register('effectiveFrom', {
              validate: (v) => {
                const check = checkFirstPriceStartDate(v, today)
                return check.issue ? PRICE_START_DATE_MESSAGES[check.issue] : true
              },
            })}
          />
          {startError && (
            <p id="product-start-message" className="text-sm text-destructive">
              {startError}
            </p>
          )}
          {hasPastNotice && (
            <p id="product-start-message" role="status" className="text-sm text-info">
              {PRICE_PAST_DATE_NOTICE}
            </p>
          )}
        </div>
      </div>

      <DialogFooter>
        <Button type="button" variant="outline" onClick={onClose} disabled={create.isPending}>
          취소
        </Button>
        <Button type="submit" disabled={create.isPending}>
          {create.isPending ? '등록하는 중…' : '등록'}
        </Button>
      </DialogFooter>
    </form>
  )
}

interface EditFormValues {
  name: string
  salesStatus: 'ACTIVE' | 'ENDED' // 판매 중 / 판매 종료
  isLowCost: boolean // 저가형 체크
}

function EditForm({
  product,
  services,
  onClose,
}: {
  product: AdminProduct
  services: ServiceOption[]
  onClose: () => void
}) {
  const update = useUpdateAdminProduct()
  const {
    register,
    handleSubmit,
    control,
    setError,
    formState: { errors },
  } = useForm<EditFormValues>({
    defaultValues: {
      name: product.name,
      salesStatus: product.isActive ? 'ACTIVE' : 'ENDED',
      isLowCost: product.isLowCost,
    },
  })
  const serviceNameById = new Map(services.map((service) => [service.ottServiceId, service.name]))
  const serviceName = (ottServiceId: number) =>
    serviceNameById.get(ottServiceId) ?? `서비스 ${ottServiceId}`

  const submit = handleSubmit((values) => {
    update.mutate(
      {
        productId: product.productId,
        name: values.name.trim(),
        isActive: values.salesStatus === 'ACTIVE',
        isLowCost: values.isLowCost, // 계약 UpdateAdminProductRequest.isLowCost(선택)
      },
      {
        onSuccess: () => {
          toast.success('OTT 구독상품을 수정했어요')
          onClose()
        },
        onError: (error) => {
          // 판매 중 번들 13개 상한(field isActive) → 판매 상태 칸 아래 / 그 밖 → 폼 위
          if (findFieldError(error, 'isActive')) {
            setError('salesStatus', { message: BUNDLE_LIMIT_MESSAGES.reactivate })
          } else {
            setError('root', { message: errorText(error) })
          }
        },
      },
    )
  })

  return (
    <form onSubmit={submit} noValidate className="grid gap-4">
      <DialogHeader>
        <DialogTitle>OTT 구독상품 수정</DialogTitle>
        <DialogDescription>
          이름, 저가형 여부, 판매 상태를 바꿀 수 있어요. 판매를 종료해도 이력은 지워지지 않아요.
        </DialogDescription>
      </DialogHeader>

      {errors.root?.message && (
        <p role="alert" className="text-sm text-destructive">
          {errors.root.message}
        </p>
      )}

      {/* 구성은 읽기 전용으로만 보인다(계약에 구성 편집 API 없음). 바꾸는 방법을 바로 아래에 적는다 */}
      <div className="grid gap-1.5">
        <p className="text-sm font-medium">구성</p>
        <div className="rounded-lg bg-muted px-3 py-2.5 text-sm">
          <ProductCompositionSummary product={product} serviceName={serviceName} />
        </div>
        <p className="text-sm break-keep text-muted-foreground">
          구성은 바꿀 수 없어요. 바꾸려면 판매를 종료하고 새로 등록하세요.
        </p>
      </div>

      <div className="grid gap-1.5">
        <Label htmlFor="product-edit-name">이름</Label>
        <Input
          id="product-edit-name"
          autoComplete="off"
          aria-invalid={errors.name ? true : undefined}
          aria-describedby={errors.name ? 'product-edit-name-error' : undefined}
          {...register('name', { validate: (v) => v.trim() !== '' || '이름을 입력해 주세요.' })}
        />
        {errors.name && (
          <p id="product-edit-name-error" className="text-sm text-destructive">
            {errors.name.message}
          </p>
        )}
      </div>

      <label htmlFor="product-edit-low-cost" className={LOW_COST_LABEL_CLASS}>
        <input
          id="product-edit-low-cost"
          type="checkbox"
          aria-describedby="product-edit-low-cost-help"
          disabled={update.isPending}
          className="mt-0.5 size-4.5 shrink-0 accent-primary focus-visible:outline-none"
          {...register('isLowCost')}
        />
        <span className="grid gap-0.5">
          <span className="text-sm font-medium">저가형 요금제</span>
          <span
            id="product-edit-low-cost-help"
            className="text-sm break-keep text-muted-foreground"
          >
            {LOW_COST_HELP}
          </span>
        </span>
      </label>

      <fieldset
        className="grid gap-1.5"
        aria-describedby={errors.salesStatus ? 'sales-status-error' : undefined}
      >
        <legend className="mb-1.5 text-sm font-medium">판매 상태</legend>
        <Controller
          control={control}
          name="salesStatus"
          render={({ field }) => (
            <RadioGroup
              value={field.value}
              onValueChange={field.onChange}
              aria-label="판매 상태"
              className="gap-2"
            >
              <div className="flex items-center gap-2">
                <RadioGroupItem id="sales-active" value="ACTIVE" />
                <Label htmlFor="sales-active">판매 중</Label>
              </div>
              <div className="flex items-center gap-2">
                <RadioGroupItem id="sales-ended" value="ENDED" />
                <Label htmlFor="sales-ended">판매 종료</Label>
              </div>
            </RadioGroup>
          )}
        />
        {errors.salesStatus && (
          <p id="sales-status-error" role="alert" className="text-sm text-destructive">
            {errors.salesStatus.message}
          </p>
        )}
      </fieldset>

      <DialogFooter>
        <Button type="button" variant="outline" onClick={onClose} disabled={update.isPending}>
          취소
        </Button>
        <Button type="submit" disabled={update.isPending}>
          {update.isPending ? '저장하는 중…' : '저장'}
        </Button>
      </DialogFooter>
    </form>
  )
}
