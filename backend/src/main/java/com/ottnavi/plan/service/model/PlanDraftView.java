package com.ottnavi.plan.service.model;

import com.ottnavi.catalog.domain.enums.MediaType;
import com.ottnavi.engine.model.PlanType;
import com.ottnavi.engine.model.Priority;
import com.ottnavi.engine.model.SolverType;
import com.ottnavi.engine.model.UnitReason;
import com.ottnavi.product.domain.enums.PriceSource;
import java.time.LocalDate;
import java.util.List;

/**
 * 계산 결과 DRAFT 한 건을 응답으로 옮기기 전의 값 묶음(계약 PlanDraft). 서비스가 만들고 컨트롤러 DTO가 이 값을 옮겨 담는다.
 *
 * @param planId              플랜 ID
 * @param planType            플랜 유형
 * @param startMonth          첫 달의 1일
 * @param algorithm           실제로 푼 알고리즘
 * @param monthlyBudget       계산에 쓴 월 예산(원)
 * @param monthlyWatchMinutes 계산에 쓴 월 시청 시간(분)
 * @param mustTotal           꼭 볼 작품 수
 * @param mustCompleted       그중 3개월 안에 다 보는 수
 * @param scheduledCount      배정된 작품(시즌) 수
 * @param totalCount          계산 대상 작품(시즌) 수
 * @param score               점수
 * @param totalCost           3개월 총비용(원)
 * @param allSubscribeCost    전체 구독 비용(원)
 * @param savedAmount         절감액 = allSubscribeCost - totalCost
 * @param months              3개월(monthIndex 순)
 * @param unscheduled         배정하지 못한 작품(꼭 → 보고 싶음 → 여유될 때 순)
 * @param dataAsOf            데이터 기준일(KST)
 */
public record PlanDraftView(
		long planId,
		PlanType planType,
		LocalDate startMonth,
		SolverType algorithm,
		int monthlyBudget,
		int monthlyWatchMinutes,
		int mustTotal,
		int mustCompleted,
		int scheduledCount,
		int totalCount,
		int score,
		int totalCost,
		int allSubscribeCost,
		int savedAmount,
		List<Month> months,
		List<Unscheduled> unscheduled,
		LocalDate dataAsOf) {

	/** 한 달(계약 PlanMonth). */
	public record Month(
			int monthIndex,
			LocalDate month,
			boolean isConfirmed,
			int cost,
			List<Product> products,
			List<Assignment> assignments) {
	}

	/** 그 달의 상품 하나(계약 PlanMonthProduct). */
	public record Product(
			long productId,
			String productName,
			List<Long> ottServiceIds,
			int appliedPrice,
			PriceSource priceSource) {
	}

	/** 그 달에 볼 작품 한 조각(계약 PlanAssignment). */
	public record Assignment(
			long planItemId,
			MediaType mediaType,
			long tmdbId,
			String title,
			Integer seasonNumber, // 영화는 null
			Priority priority,
			long ottServiceId,
			int watchMin,
			int segmentIndex,
			int segmentCount,
			boolean isContinuation) {
	}

	/** 배정하지 못한 작품 하나(계약 UnscheduledItem). */
	public record Unscheduled(
			long planItemId,
			MediaType mediaType,
			long tmdbId,
			String title,
			Integer seasonNumber, // 영화는 null
			Priority priority,
			UnitReason reason) {
	}
}
