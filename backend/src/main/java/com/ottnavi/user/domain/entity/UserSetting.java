package com.ottnavi.user.domain.entity;

import com.ottnavi.global.common.BaseTimeEntity;
import com.ottnavi.user.domain.enums.WatchPreset;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 사용자의 계산 조건(ERD user_setting). PK는 users의 PK를 그대로 쓴다(1:1).
 * 행이 있으면 온보딩을 마친 사용자이며, 행은 첫 설정 저장(PUT /api/me/settings)이 만든다(가입 때 만들지 않는다).
 */
@Getter
@Entity
@Table(name = "user_setting")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserSetting extends BaseTimeEntity {

	@Id
	@Column(name = "user_id")
	private Long userId; // users.id를 그대로 쓰는 PK

	@Column(nullable = false)
	private int monthlyBudget; // 월 예산(원)

	@Column(nullable = false)
	private int monthlyWatchMinutes; // 월 시청 시간(분)

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 10)
	private WatchPreset watchPreset; // 시청 시간을 고른 방식

	@Column(nullable = false)
	private boolean notificationEnabled; // 알림 수신 여부. 기본 꺼짐(D6)

	@Column(nullable = false)
	private boolean lowestPriceMode; // "가장 저렴한 요금제로 계산할래요". 기본 꺼짐. API에서는 isLowestPriceMode

	/** 첫 설정 저장 때 만든다. 알림은 꺼진 상태로 시작한다 */
	public static UserSetting create(Long userId, int monthlyBudget, int monthlyWatchMinutes, WatchPreset watchPreset,
			boolean lowestPriceMode) {
		UserSetting setting = new UserSetting();
		setting.userId = userId;
		setting.monthlyBudget = monthlyBudget;
		setting.monthlyWatchMinutes = monthlyWatchMinutes;
		setting.watchPreset = watchPreset;
		setting.lowestPriceMode = lowestPriceMode;
		setting.notificationEnabled = false;
		return setting;
	}

	/** 계산 조건을 바꾼다(더티 체킹으로 저장) */
	public void updateCondition(int monthlyBudget, int monthlyWatchMinutes, WatchPreset watchPreset,
			boolean lowestPriceMode) {
		this.monthlyBudget = monthlyBudget;
		this.monthlyWatchMinutes = monthlyWatchMinutes;
		this.watchPreset = watchPreset;
		this.lowestPriceMode = lowestPriceMode;
	}
}
