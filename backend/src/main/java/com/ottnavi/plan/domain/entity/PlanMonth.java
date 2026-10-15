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
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

/** 플랜의 한 달(ERD plan_month). 0번은 확정(이번 달), 1·2번은 예상이다 */
@Getter
@Entity
@Table(name = "plan_month")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PlanMonth {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "plan_id", nullable = false)
	@OnDelete(action = OnDeleteAction.CASCADE) // 플랜 삭제 시 DB가 함께 지운다
	private Plan plan;

	@Column(nullable = false)
	private int monthIndex; // 0은 확정, 1·2는 예상

	@Column(name = "month", nullable = false)
	private LocalDate month; // 그 달의 1일

	@Column(nullable = false)
	private boolean confirmed; // monthIndex가 0이면 true(DB CHECK와 같은 규칙)

	@Column(nullable = false)
	private int cost; // 그 달 비용(원)

	/** 한 달을 만든다. confirmed는 monthIndex로 정한다 */
	public static PlanMonth create(Plan plan, int monthIndex, LocalDate month, int cost) {
		PlanMonth planMonth = new PlanMonth();
		planMonth.plan = plan;
		planMonth.monthIndex = monthIndex;
		planMonth.month = month;
		planMonth.confirmed = monthIndex == 0;
		planMonth.cost = cost;
		return planMonth;
	}
}
