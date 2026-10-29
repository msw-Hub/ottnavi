package com.ottnavi.notification.service.model;

import java.util.List;

/**
 * 월초 안내(MONTHLY) 메일의 재료. outbox payload(JSON)로 저장되고 템플릿 monthly.html이 읽는다.
 *
 * @param v                     payload 형식 버전
 * @param userName              생성 시점의 사용자 이름
 * @param dataDate              데이터 기준일(KST, YYYY-MM-DD)
 * @param month                 안내하는 달(YYYY-MM)
 * @param summary               서버가 만든 한 줄 요약
 * @param joinProducts          이번 달 새로 가입할 상품
 * @param keepProducts          이미 구독 중이라 이번 달 비용이 0원인 상품
 * @param cancelReserveProducts 해지 예약을 권하는 상품
 * @param titles                이번 달에 볼 작품
 * @param monthCost             이번 달 비용(원)
 * @param changeReasons         지난 플랜과 달라진 이유(재계산 변경 요약의 이유 문장). 없으면 빈 목록
 */
public record MonthlyPayload(
		int v,
		String userName,
		String dataDate,
		String month,
		String summary,
		List<NotificationProductItem> joinProducts,
		List<NotificationProductItem> keepProducts,
		List<NotificationProductItem> cancelReserveProducts,
		List<NotificationTitleItem> titles,
		int monthCost,
		List<String> changeReasons) {

	/** 현재 payload 형식 버전 */
	public static final int CURRENT_VERSION = 1;
}
