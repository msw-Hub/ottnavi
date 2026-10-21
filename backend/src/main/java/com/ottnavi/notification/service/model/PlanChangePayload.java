package com.ottnavi.notification.service.model;

import java.util.List;

/**
 * 플랜 변경(PLAN_CHANGE) 메일의 재료. 이번 달 확정 플랜의 작품이 배정된 서비스에서 빠졌을 때 만든다.
 * outbox payload(JSON)로 저장되고 템플릿 plan-change.html이 읽는다.
 *
 * @param v               payload 형식 버전
 * @param userName        생성 시점의 사용자 이름
 * @param dataDate        데이터 기준일(KST, YYYY-MM-DD)
 * @param month           바뀐 플랜의 첫 달(YYYY-MM)
 * @param summary         서버가 만든 한 줄 요약
 * @param leftTitles      배정된 서비스에서 빠진 작품(서비스 이름은 빠진 서비스)
 * @param changeReasons   바뀐 이유 문장
 * @param currentProducts 바뀐 플랜의 이번 달 상품
 */
public record PlanChangePayload(
		int v,
		String userName,
		String dataDate,
		String month,
		String summary,
		List<NotificationTitleItem> leftTitles,
		List<String> changeReasons,
		List<NotificationProductItem> currentProducts) {

	/** 현재 payload 형식 버전 */
	public static final int CURRENT_VERSION = 1;
}
