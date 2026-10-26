package com.ottnavi.plan.domain.entity;

import com.ottnavi.engine.model.PlanType;
import com.ottnavi.engine.model.SolverType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 계산 실행 기록(ERD calc_run, FR-13). 플랜이 지워져도 통계로 남기고 planId만 DB가 NULL로 바꾼다.
 * 수정하지 않는 기록이고 컬럼이 created_at뿐이라 BaseTimeEntity(updated_at 포함) 대신 직접 둔다.
 */
@Getter
@Entity
@Table(name = "calc_run")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CalcRun {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private Long planId; // 계산 결과로 만든 플랜. 플랜이 지워지면 null

	@JdbcTypeCode(SqlTypes.CHAR)
	@Column(nullable = false, length = 64)
	private String inputHash; // 정규화한 입력의 SHA-256(16진수 64자)

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 15)
	private PlanType planType; // 플랜 유형

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 10)
	private SolverType algorithm; // EXACT / GREEDY

	@Column(nullable = false)
	private int mustCompleted; // 시청을 끝내는 꼭 볼 작품 수

	@Column(nullable = false)
	private int score; // 시청 완료 점수 합

	@Column(nullable = false)
	private int totalCost; // 플랜 총비용(원)

	@Column(nullable = false)
	private int candidateCount; // 평가한 후보 조합 수

	@Column(nullable = false)
	private long elapsedMs; // 계산 소요 시간(ms)

	@CreationTimestamp
	@JdbcTypeCode(SqlTypes.TIMESTAMP_WITH_TIMEZONE)
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt; // 기록 시각

	/** 계산 실행 기록 한 건을 만든다 */
	public static CalcRun create(Long planId, String inputHash, PlanType planType, SolverType algorithm,
			int mustCompleted, int score, int totalCost, int candidateCount, long elapsedMs) {
		CalcRun run = new CalcRun();
		run.planId = planId;
		run.inputHash = inputHash;
		run.planType = planType;
		run.algorithm = algorithm;
		run.mustCompleted = mustCompleted;
		run.score = score;
		run.totalCost = totalCost;
		run.candidateCount = candidateCount;
		run.elapsedMs = elapsedMs;
		return run;
	}
}
