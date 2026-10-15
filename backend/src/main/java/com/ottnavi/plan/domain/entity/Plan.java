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
	private String changeSummary; // 재계산 변경 요약. MVP는 null

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

	/** DRAFT를 ACTIVE로 바꾼다. 기존 ACTIVE는 먼저 archive하고 flush한 뒤 호출해야 부분 유니크를 위반하지 않는다(TECH 2절) */
	public void activate(Instant now) {
		if (status != PlanStatus.DRAFT) {
			throw new IllegalStateException("DRAFT만 저장할 수 있다: " + status);
		}
		this.status = PlanStatus.ACTIVE;
		this.activatedAt = now;
	}

	/** ACTIVE를 보관 상태로 바꾼다. 저장 시각(activatedAt)은 유지한다 */
	public void archive() {
		if (status != PlanStatus.ACTIVE) {
			throw new IllegalStateException("ACTIVE만 보관할 수 있다: " + status);
		}
		this.status = PlanStatus.ARCHIVED;
	}
}
