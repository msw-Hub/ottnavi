package com.ottnavi.plan.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

/** 월별 시청 배정 한 조각(ERD plan_assignment). 긴 시즌은 연속된 달에 걸쳐 여러 행이 된다 */
@Getter
@Entity
@Table(name = "plan_assignment")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PlanAssignment {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "plan_item_id", nullable = false)
	@OnDelete(action = OnDeleteAction.CASCADE) // 항목 삭제 시 DB가 함께 지운다
	private PlanItem planItem;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "plan_month_id", nullable = false)
	@OnDelete(action = OnDeleteAction.CASCADE) // 달 삭제 시에도 DB가 함께 지운다
	private PlanMonth planMonth;

	@Column(nullable = false)
	private Long ottServiceId; // 시청하는 OTT 서비스 ID(다른 도메인이라 ID만 보관)

	@Column(nullable = false)
	private int minutes; // 그 달에 배정한 시청 시간(분)

	/** 배정 한 조각을 만든다. 시간은 0보다 커야 한다(DB CHECK와 같은 규칙) */
	public static PlanAssignment create(PlanItem planItem, PlanMonth planMonth, Long ottServiceId, int minutes) {
		if (minutes <= 0) {
			throw new IllegalArgumentException("배정 시간은 0보다 커야 한다: " + minutes);
		}
		PlanAssignment assignment = new PlanAssignment();
		assignment.planItem = planItem;
		assignment.planMonth = planMonth;
		assignment.ottServiceId = ottServiceId;
		assignment.minutes = minutes;
		return assignment;
	}
}
