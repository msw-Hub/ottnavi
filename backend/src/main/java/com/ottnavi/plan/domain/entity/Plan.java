package com.ottnavi.plan.domain.entity;

import com.ottnavi.engine.model.PlanType;
import com.ottnavi.engine.model.SolverType;
import com.ottnavi.global.common.BaseTimeEntity;
import com.ottnavi.plan.domain.enums.PlanStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 3개월 플랜 한 건(ERD plan). 계산 당시의 값을 복사한 스냅샷이라 이후 가격·설정이 바뀌어도 변하지 않는다.
 * 사용자·이전 플랜은 ID만 들고, 하위 행(월·상품·항목·배정)은 DB FK CASCADE가 지운다(JPA cascade 삭제 미사용, TECH T-1).
 */
@Getter
@Entity
@Table(name = "plan")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Plan extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private Long userId; // 플랜 주인

	private Long previousPlanId; // 재계산 전 플랜. 그 플랜이 지워지면 DB가 NULL로 바꾼다

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 10)
	private PlanStatus status; // DRAFT / ACTIVE / ARCHIVED

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 15)
	private PlanType planType; // RECOMMENDED / SAVER / SIMPLE

	@Column(nullable = false)
	private LocalDate startMonth; // 첫 달의 1일

	@Column(nullable = false)
	private int monthlyBudget; // 계산 당시 월 예산(원) 스냅샷

	@Column(nullable = false)
	private int monthlyWatchMinutes; // 계산 당시 월 시청 시간(분) 스냅샷

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 10)
	private SolverType algorithm; // EXACT / GREEDY

	@Column(nullable = false)
	private int mustTotal; // 꼭 볼 작품 수

	@Column(nullable = false)
	private int mustCompleted; // 시청을 끝내는 꼭 볼 작품 수

	@Column(nullable = false)
	private int score; // 시청 완료 점수 합

	@Column(nullable = false)
	private int totalCost; // 플랜 총비용(원)

	@Column(nullable = false)
	private int allSubscribeCost; // 전체 구독 비용(원). 절감액 = allSubscribeCost - totalCost (저장하지 않는다)

	@JdbcTypeCode(SqlTypes.TIMESTAMP_WITH_TIMEZONE)
	@Column(nullable = false)
	private Instant dataAsOf; // 계산에 쓴 마지막 완료 수집 시각

	@JdbcTypeCode(SqlTypes.TIMESTAMP_WITH_TIMEZONE)
	private Instant activatedAt; // ACTIVE로 저장된 시각. DRAFT만 null

	@Column(columnDefinition = "text")
	private String changeSummary; // 재계산 변경 요약(JSON, v 필드 포함). 직접 저장한 플랜과 변화 없는 재계산은 null

	/** 계산 결과 한 건을 DRAFT로 만든다. 하위 행은 만든 뒤 각각 저장한다 */
	public static Plan createDraft(Long userId, Long previousPlanId, PlanType planType, LocalDate startMonth,
			int monthlyBudget, int monthlyWatchMinutes, SolverType algorithm, int mustTotal, int mustCompleted,
			int score, int totalCost, int allSubscribeCost, Instant dataAsOf) {
		Plan plan = new Plan();
		plan.userId = userId;
		plan.previousPlanId = previousPlanId;
		plan.status = PlanStatus.DRAFT;
		plan.planType = planType;
		plan.startMonth = startMonth;
		plan.monthlyBudget = monthlyBudget;
		plan.monthlyWatchMinutes = monthlyWatchMinutes;
		plan.algorithm = algorithm;
		plan.mustTotal = mustTotal;
		plan.mustCompleted = mustCompleted;
		plan.score = score;
		plan.totalCost = totalCost;
		plan.allSubscribeCost = allSubscribeCost;
		plan.dataAsOf = dataAsOf;
		return plan;
	}

	/**
	 * 매월 재계산 결과를 DRAFT 없이 바로 ACTIVE로 만든다(Task 069). 저장 시각(activatedAt)은 재계산 시각(now)이다.
	 * previousPlanId는 방금 보관한 이전 ACTIVE의 ID이고, 기존 ACTIVE는 먼저 archive하고 flush한 뒤 저장해야 부분 유니크를 위반하지 않는다.
	 */
	public static Plan createRecalculated(Long userId, Long previousPlanId, PlanType planType, LocalDate startMonth,
			int monthlyBudget, int monthlyWatchMinutes, SolverType algorithm, int mustTotal, int mustCompleted,
			int score, int totalCost, int allSubscribeCost, Instant dataAsOf, Instant now) {
		if (previousPlanId == null) {
			throw new IllegalArgumentException("재계산 플랜은 이전 플랜이 있어야 한다");
		}
		Plan plan = createDraft(userId, previousPlanId, planType, startMonth, monthlyBudget, monthlyWatchMinutes,
				algorithm, mustTotal, mustCompleted, score, totalCost, allSubscribeCost, dataAsOf);
		plan.status = PlanStatus.ACTIVE;
		plan.activatedAt = now;
		return plan;
	}

	/** 재계산 변경 요약(JSON)을 남긴다. 재계산으로 만든 ACTIVE에만 쓰고, 한 번 남기면 바꾸지 않는다 */
	public void recordChangeSummary(String changeSummaryJson) {
		if (status != PlanStatus.ACTIVE) {
			throw new IllegalStateException("ACTIVE만 변경 요약을 남길 수 있다: " + status);
		}
		if (changeSummary != null) {
			throw new IllegalStateException("변경 요약이 이미 있다: planId=" + id);
		}
		if (changeSummaryJson == null || changeSummaryJson.isBlank()) {
			throw new IllegalArgumentException("변경 요약이 비어 있다");
		}
		this.changeSummary = changeSummaryJson;
	}

	/**
	 * DRAFT를 ACTIVE로 바꾼다. 기존 ACTIVE는 먼저 archive하고 flush한 뒤 호출해야 부분 유니크를 위반하지 않는다(TECH 2절).
	 * previousPlanId는 방금 보관한 ACTIVE의 ID로 덮어쓴다(없으면 null). 계산 시점의 값을 저장 시점 기준으로 바로잡는다.
	 */
	public void activate(Instant now, Long previousPlanId) {
		if (status != PlanStatus.DRAFT) {
			throw new IllegalStateException("DRAFT만 저장할 수 있다: " + status);
		}
		this.status = PlanStatus.ACTIVE;
		this.activatedAt = now;
		this.previousPlanId = previousPlanId;
	}

	/** ACTIVE를 보관 상태로 바꾼다. 저장 시각(activatedAt)은 유지한다 */
	public void archive() {
		if (status != PlanStatus.ACTIVE) {
			throw new IllegalStateException("ACTIVE만 보관할 수 있다: " + status);
		}
		this.status = PlanStatus.ARCHIVED;
	}
}
