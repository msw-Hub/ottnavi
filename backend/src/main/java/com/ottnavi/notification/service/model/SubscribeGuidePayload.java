package com.ottnavi.notification.service.model;

import java.util.List;

/**
 * 구독 안내(SUBSCRIBE_GUIDE) 메일의 재료. outbox payload(JSON)로 저장되고 템플릿 subscribe-guide.html이 읽는다.
 * 공통 필드(v, userName, dataDate, month, summary)는 메일 렌더러가 읽는 이름과 같다.
 *
 * @param v                     payload 형식 버전
 * @param userName              생성 시점의 사용자 이름
 * @param dataDate              데이터 기준일(KST, YYYY-MM-DD)
 * @param month                 안내하는 달(YYYY-MM)
 * @param summary               서버가 만든 한 줄 요약
 * @param joinProducts          이번 달 새로 가입할 상품
 * @param cancelReserveProducts 해지 예약을 권하는 상품(이번 달에는 쓰고 다음 달에는 쓰지 않는 상품)
 * @param titles                이번 달에 볼 작품
 * @param monthCost             이번 달 비용(원)
 */
public record SubscribeGuidePayload(
		int v,
		String userName,
		String dataDate,
		String month,
		String summary,
		List<NotificationProductItem> joinProducts,
		List<NotificationProductItem> cancelReserveProducts,
		List<NotificationTitleItem> titles,
		int monthCost) {

	/** 현재 payload 형식 버전 */
	public static final int CURRENT_VERSION = 1;
}
