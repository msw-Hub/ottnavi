package com.ottnavi.user.domain.entity;

import com.ottnavi.global.common.BaseTimeEntity;
import com.ottnavi.provider.domain.entity.OttService;
import com.ottnavi.user.domain.enums.SubscriptionStatus;
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

/**
 * 서비스별 이용 상태(ERD user_subscription). 미구독은 행을 두지 않는다.
 * product_id 컬럼은 MVP에서 항상 NULL이라 매핑하지 않는다(계산 단계가 판매 중 단품을 찾는다).
 */
@Getter
@Entity
@Table(name = "user_subscription")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserSubscription extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	private User user; // 사용자

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "ott_service_id", nullable = false)
	private OttService ottService; // 서비스

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private SubscriptionStatus status; // 이용 상태

	@Column(length = 30)
	private String planName; // 표시용 요금제 이름. SUBSCRIBED일 때만 값이 있다

	private Integer billingDay; // 결제일 1~31. SUBSCRIBED일 때만 값이 있다

	/** 이용 상태 한 줄을 만든다. 요금제 이름·결제일은 SUBSCRIBED일 때만 넘긴다(DB CHECK와 같은 규칙) */
	public static UserSubscription create(User user, OttService ottService, SubscriptionStatus status,
			String planName, Integer billingDay) {
		UserSubscription subscription = new UserSubscription();
		subscription.user = user;
		subscription.ottService = ottService;
		subscription.status = status;
		subscription.planName = planName;
		subscription.billingDay = billingDay;
		return subscription;
	}
}
