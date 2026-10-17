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
  checkBundleComponents,
  TIER_OPTION_LABELS,
  TIER_SELECT_CLASS,
} from '@/features/admin/bundleRules'
import { PhaseLaterTag } from '@/features/admin/components/PhaseLaterTag'
import { BundleComponentsField } from '@/features/admin/components/products/ProductCompositionFields'
import {
  useCreateAdminProduct,
  useUpdateAdminProduct,
} from '@/features/admin/hooks/useAdminProducts'
import type { AdminProduct, ProviderTier } from '@/api/generated/model'
import type { ProductComponentDraft, ProductKind } from '@/features/admin/mockTypes'
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
  // 번들·광고형 1차안(Task 075) 미리보기인지(목업 모드 + ?productsPreview=bundle). 켜면 번들 유형과 등급을 고를 수 있다.
  // 번들·광고형 등록은 계약에 아직 없어 서버로 보내지 않고 안내만 띄운다. 꺼져 있으면 지금(MVP) 동작 그대로다
  isBundlePreview?: boolean
}

/**
 * OTT 구독상품 등록·수정 대화상자다(SCR-14).
 *
 * 대화상자(Dialog)를 쓴 이유: 등록은 이름·서비스·첫 월 구독요금을 한 번에 받는 짧은 작업이라 목록 위에 띄워 집중시키는 편이 낫다.
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
  isBundlePreview = false,
}: ProductFormDialogProps) {
  return (
    <Dialog
      open={state !== null}
      onOpenChange={(isOpen) => {
        if (!isOpen) onClose()
      }}
    >
      <CloseLabeledDialogContent
        className="sm:max-w-md"
        // Radix는 닫힐 때 DialogTrigger로 포커스를 돌려준다. 우리는 Trigger 없이 상태로 여닫아 돌려줄 곳을 모르므로 직접 연 버튼으로 돌려준다
        onCloseAutoFocus={(event) => {
          event.preventDefault()
          openerRef.current?.focus()
        }}
      >
        {state?.mode === 'create' && (
          <CreateForm
            services={services}
            today={today}
            onClose={onClose}
            onCreated={onCreated}
            isBundlePreview={isBundlePreview}
          />
        )}
        {state?.mode === 'edit' && <EditForm product={state.product} onClose={onClose} />}
      </CloseLabeledDialogContent>
    </Dialog>
  )
}

// 서버 오류를 폼 위 문장으로 바꾼다
function errorText(error: unknown): string {
  return isApiError(error) ? error.userMessage : getErrorMessage(null)
}

interface CreateFormValues {
  name: string
  ottServiceId: string // select 값은 문자열이라 제출할 때 숫자로 바꾼다
  monthlyPrice: string
  effectiveFrom: string
}

function CreateForm({
  services,
  today,
  onClose,
  onCreated,
  isBundlePreview = false,
}: Pick<
  ProductFormDialogProps,
  'services' | 'today' | 'onClose' | 'onCreated' | 'isBundlePreview'
>) {
  const create = useCreateAdminProduct()
  // ===== 번들·광고형 1차안(Task 075, 미리보기에서만 바뀐다) =====
  const [productType, setProductType] = useState<ProductKind>('SINGLE') // 고른 유형. 미리보기가 아니면 항상 SINGLE
  const [singleTier, setSingleTier] = useState<ProviderTier>('STANDARD') // 단품의 등급
  const [bundleComponents, setBundleComponents] = useState<ProductComponentDraft[]>([]) // 번들 구성
  const [bundleError, setBundleError] = useState<string | null>(null) // 번들 구성 검증 문장
  const isBundle = productType === 'BUNDLE'
  const {
    register,
    handleSubmit,
    control,
    setError,
    formState: { errors },
  } = useForm<CreateFormValues>({
    defaultValues: { name: '', ottServiceId: '', monthlyPrice: '', effectiveFrom: today },
  })

  const watchedStart = useWatch({ control, name: 'effectiveFrom' })
  const startCheck = checkFirstPriceStartDate(watchedStart, today)
  const startError = errors.effectiveFrom?.message
  const hasPastNotice = !startError && startCheck.isPastDate

  const submit = handleSubmit((values) => {
    // 1. 번들·광고형 1차안: 계약에 아직 없어 서버로 보내지 않는다. 번들은 구성부터 검사하고, 통과하면 안내만 띄우고 닫는다
    if (isBundlePreview && (isBundle || singleTier === 'AD')) {
      const message = isBundle ? checkBundleComponents(bundleComponents) : null
      if (message) {
        setBundleError(message)
        return
      }
      toast.info('1차안 미리보기라 저장하지 않았어요. 번들·광고형 등록은 계약 반영 뒤 저장돼요.')
      onClose()
      return
    }
    // 2. 단품(일반)은 지금처럼 등록 API로 보낸다
    create.mutate(
      {
        name: values.name.trim(),
        productType: 'SINGLE',
        ottServiceId: Number(values.ottServiceId),
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
          if (isApiError(error) && error.errorCode === 'PRICE_FIRST_START_DATE_IN_FUTURE') {
            setError('effectiveFrom', { message: error.userMessage }, { shouldFocus: true })
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
          placeholder="예: 넷플릭스 스탠다드"
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

      {/* 유형을 서비스보다 먼저 묻는다(미리보기): 번들이면 서비스 칸 대신 구성 목록이 나와, 고르는 순서가 화면 순서와 맞는다.
          미리보기가 아니면 예전처럼 서비스 → 유형 순서다(아래 원래 자리의 유형 칸) */}
      {isBundlePreview && (
        <fieldset className="grid gap-1.5">
          <legend className="mb-1.5 text-sm font-medium">유형</legend>
          <RadioGroup
            value={productType}
            onValueChange={(value) => {
              setProductType(value as ProductKind)
              setBundleError(null)
            }}
            aria-label="OTT 구독상품 유형"
            className="flex flex-wrap gap-4"
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
      )}

      {isBundlePreview && isBundle ? (
        <div className="grid gap-2">
          <BundleComponentsField
            idPrefix="product-create"
            services={services}
            value={bundleComponents}
            onChange={(next) => {
              setBundleComponents(next)
              setBundleError(null)
            }}
            error={bundleError ?? undefined}
          />
          {/* 구성은 등록 뒤 바꿀 수 없다(2026-10-17 사용자 확정). 가격 이력처럼 바뀐 상품은 새로 등록하고 기존 상품은 판매 종료한다 */}
          <p className="text-sm text-muted-foreground">
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
            className="h-8 w-full rounded-lg border border-input bg-background px-2 text-sm text-foreground outline-none focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/50 aria-invalid:border-destructive"
            {...register('ottServiceId', {
              // 번들(미리보기)에서는 서비스 칸이 사라지고 구성 목록이 대신하므로 검사하지 않는다
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

      {/* 단품의 등급(미리보기). 광고형은 그 서비스 작품 중 광고형 제공으로 확인된 것만 볼 수 있다고 계산한다 */}
      {isBundlePreview && !isBundle && (
        <div className="grid gap-1.5">
          <Label htmlFor="product-tier">등급</Label>
          <select
            id="product-tier"
            value={singleTier}
            onChange={(event) => setSingleTier(event.target.value as ProviderTier)}
            className={`${TIER_SELECT_CLASS} w-full`}
          >
            <option value="STANDARD">{TIER_OPTION_LABELS.STANDARD}</option>
            <option value="AD">{TIER_OPTION_LABELS.AD}</option>
          </select>
        </div>
      )}

      {/* MVP(미리보기 아님)의 유형 칸: 단품만 고를 수 있고 번들은 "2단계부터" 태그와 함께 막혀 있다 */}
      {!isBundlePreview && (
        <fieldset className="grid gap-1.5">
          <legend className="mb-1.5 text-sm font-medium">유형</legend>
          <RadioGroup value="SINGLE" aria-label="OTT 구독상품 유형" className="gap-2">
            <div className="flex items-center gap-2">
              <RadioGroupItem id="product-type-single" value="SINGLE" />
              <Label htmlFor="product-type-single">단품</Label>
            </div>
            <div className="flex flex-wrap items-center gap-2">
              <RadioGroupItem id="product-type-bundle" value="BUNDLE" disabled />
              <Label htmlFor="product-type-bundle" className="text-muted-foreground">
                번들
              </Label>
              <PhaseLaterTag />
            </div>
          </RadioGroup>
        </fieldset>
      )}

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
}

function EditForm({ product, onClose }: { product: AdminProduct; onClose: () => void }) {
  const update = useUpdateAdminProduct()
  const {
    register,
    handleSubmit,
    control,
    setError,
    formState: { errors },
  } = useForm<EditFormValues>({
    defaultValues: { name: product.name, salesStatus: product.isActive ? 'ACTIVE' : 'ENDED' },
  })

  const submit = handleSubmit((values) => {
    update.mutate(
      {
        productId: product.productId,
        name: values.name.trim(),
        isActive: values.salesStatus === 'ACTIVE',
      },
      {
        onSuccess: () => {
          toast.success('OTT 구독상품을 수정했어요')
          onClose()
        },
        onError: (error) => setError('root', { message: errorText(error) }),
      },
    )
  })

  return (
    <form onSubmit={submit} noValidate className="grid gap-4">
      <DialogHeader>
        <DialogTitle>OTT 구독상품 수정</DialogTitle>
        <DialogDescription>
          이름과 판매 상태를 바꿀 수 있어요. 판매를 종료해도 이력은 지워지지 않아요.
        </DialogDescription>
      </DialogHeader>

      {errors.root?.message && (
        <p role="alert" className="text-sm text-destructive">
          {errors.root.message}
        </p>
      )}

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

      <fieldset className="grid gap-1.5">
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
