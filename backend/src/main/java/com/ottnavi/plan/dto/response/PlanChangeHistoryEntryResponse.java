package com.ottnavi.plan.dto.response;

import com.ottnavi.engine.model.PlanType;
import com.ottnavi.plan.domain.enums.PlanChangeReasonType;
import com.ottnavi.plan.domain.enums.PlanTitleChangeType;
import com.ottnavi.plan.domain.rule.PlanChangeSummary;
import com.ottnavi.plan.service.model.PlanChangeHistoryEntryView;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * 변경 내역 한 건(계약 PlanChangeHistoryEntry). 저장해 둔 변경 요약에서 내부용 필드(watchUnitId, 이유의 ottServiceId)를 뺀 모양이다.
 *
 * @param changeId     변경 내역 ID(새 플랜의 ID)
 * @param changedAt    재계산해 플랜이 바뀐 시각(KST 오프셋)
 * @param planType     플랜 유형
 * @param summary      서버가 만든 한 줄 요약
 * @param reasons      바뀐 이유(1건 이상)
 * @param monthChanges 달별 구독 변화
 * @param titleChanges 작품 변화
 * @param dataAsOf     이 재계산에 쓴 데이터의 기준일(KST)
 */
public record PlanChangeHistoryEntryResponse(
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "312") long changeId,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "2026-11-01T03:50:00+09:00") OffsetDateTime changedAt,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "RECOMMENDED") PlanType planType,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String summary,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<Reason> reasons,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<MonthChange> monthChanges,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<TitleChange> titleChanges,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "2026-11-01") LocalDate dataAsOf) {

	private static final ZoneId KST = ZoneId.of("Asia/Seoul");

	/** 바뀐 이유 한 건(계약 PlanChangeReason) */
	public record Reason(
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) PlanChangeReasonType reasonType,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String description) {
	}

	/** 달별 구독 변화에 보이는 상품 한 개(계약 PlanChangeProduct) */
	public record Product(
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "1") long productId,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<Long> ottServiceIds,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String productName) {
	}

	/** 한 달의 구독 변화(계약 PlanMonthChange) */
	public record MonthChange(
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED, pattern = "^\\d{4}-(0[1-9]|1[0-2])$", example = "2026-11") String month,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean isConfirmed,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<Product> previousProducts,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<Product> currentProducts,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) Integer previousCost,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) int currentCost) {
	}

	/** 작품을 어느 달·어느 서비스에서 보는지(계약 PlanTitlePlacement) */
	public record Placement(
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED, pattern = "^\\d{4}-(0[1-9]|1[0-2])$", example = "2026-11") String month,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) long ottServiceId) {
	}

	/** 작품(시즌) 하나의 변화(계약 PlanTitleChange) */
	public record TitleChange(
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) PlanTitleChangeType changeType,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String title,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) Integer seasonNumber,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) Placement previous,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) Placement current) {
	}

	/** 서비스 계층 값을 응답으로 바꾼다. 내부용 필드는 옮기지 않는다 */
	public static PlanChangeHistoryEntryResponse from(PlanChangeHistoryEntryView view) {
		PlanChangeSummary summary = view.summary();
		List<Reason> reasons = new ArrayList<>();
		for (PlanChangeSummary.Reason reason : summary.reasons()) {
			reasons.add(new Reason(reason.reasonType(), reason.description()));
		}
		List<MonthChange> monthChanges = new ArrayList<>();
		for (PlanChangeSummary.MonthChange change : summary.monthChanges()) {
			monthChanges.add(new MonthChange(change.month(), change.isConfirmed(), toProducts(change.previousProducts()),
					toProducts(change.currentProducts()), change.previousCost(), change.currentCost()));
		}
		List<TitleChange> titleChanges = new ArrayList<>();
		for (PlanChangeSummary.TitleChange change : summary.titleChanges()) {
			titleChanges.add(new TitleChange(change.changeType(), change.title(), change.seasonNumber(),
					toPlacement(change.previous()), toPlacement(change.current())));
		}
		return new PlanChangeHistoryEntryResponse(view.changeId(), view.changedAt().atZone(KST).toOffsetDateTime(),
				view.planType(), summary.summary(), reasons, monthChanges, titleChanges, view.dataAsOf());
	}

	private static List<Product> toProducts(List<PlanChangeSummary.Product> products) {
		List<Product> result = new ArrayList<>();
		for (PlanChangeSummary.Product product : products) {
			result.add(new Product(product.productId(), product.ottServiceIds(), product.productName()));
		}
		return result;
	}

	private static Placement toPlacement(PlanChangeSummary.Placement placement) {
		if (placement == null) {
			return null;
		}
		return new Placement(placement.month(), placement.ottServiceId());
	}
}
