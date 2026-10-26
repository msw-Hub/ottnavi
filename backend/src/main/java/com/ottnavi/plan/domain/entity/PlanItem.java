package com.ottnavi.plan.domain.entity;

import com.ottnavi.engine.model.Priority;
import com.ottnavi.engine.model.UnitReason;
import com.ottnavi.plan.domain.enums.PlanItemOutcome;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

/** 찜 단위(작품 또는 시즌)의 계산 결과(ERD plan_item). "남은 작품과 이유"(FR-12)의 원천이다 */
@Getter
@Entity
@Table(name = "plan_item")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PlanItem {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "plan_id", nullable = false)
	@OnDelete(action = OnDeleteAction.CASCADE) // 플랜 삭제 시 DB가 함께 지운다
	private Plan plan;

	@Column(nullable = false)
	private Long watchUnitId; // 시청 단위 ID(다른 도메인이라 ID만 보관)

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 10)
	private Priority priority; // 계산 당시 우선순위 스냅샷

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 15)
	private PlanItemOutcome outcome; // SCHEDULED / UNSCHEDULED

	@Enumerated(EnumType.STRING)
	@Column(length = 15)
	private UnitReason reason; // UNSCHEDULED일 때만

	private Integer completedMonthIndex; // 시청을 끝내는 달의 month_index. UNSCHEDULED면 null

	/** 배정된 항목을 만든다 */
	public static PlanItem scheduled(Plan plan, Long watchUnitId, Priority priority, int completedMonthIndex) {
		if (completedMonthIndex < 0) {
			throw new IllegalArgumentException("완료 월 인덱스는 0 이상이어야 한다: " + completedMonthIndex);
		}
		PlanItem item = new PlanItem();
		item.plan = plan;
		item.watchUnitId = watchUnitId;
		item.priority = priority;
		item.outcome = PlanItemOutcome.SCHEDULED;
		item.completedMonthIndex = completedMonthIndex;
		return item;
	}

	/** 배정하지 못한 항목을 이유와 함께 만든다 */
	public static PlanItem unscheduled(Plan plan, Long watchUnitId, Priority priority, UnitReason reason) {
		if (reason == null) {
			throw new IllegalArgumentException("배정하지 못한 항목은 이유가 필요하다");
		}
		PlanItem item = new PlanItem();
		item.plan = plan;
		item.watchUnitId = watchUnitId;
		item.priority = priority;
		item.outcome = PlanItemOutcome.UNSCHEDULED;
		item.reason = reason;
		return item;
	}
}
