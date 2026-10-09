package com.ottnavi.engine.model;

/**
 * 시청 단위 하나를 어느 달에 어느 상품으로 몇 분 보는지(배정 행 하나).
 * 긴 시즌은 달마다 한 행씩 생기고 달마다 상품이 다를 수 있다(PRD 5.4 긴 시즌, B안).
 * MVP는 단품이라 서비스는 productId로 정해지고(plan이 ott_service_id로 변환), 번들을 지원하는 2단계에 ottServiceId를 추가한다.
 */
public record Assignment(
		int monthIndex, // 0=이번 달(확정), 1·2=다음 두 달(예상)
		long productId, // 이 달에 이 분량을 볼 상품. 볼 수 있는 상품이 여럿이면 ID가 가장 작은 것(D9)
		int minutes     // 이 달에 배정한 시간(분), 1 이상
) {

	/** 달 범위와 배정 시간을 검증한다. */
	public Assignment {
		if (monthIndex < 0 || monthIndex >= Selection.MONTH_COUNT) {
			throw new IllegalArgumentException("배정 달은 0~2여야 한다: monthIndex=" + monthIndex);
		}
		if (minutes <= 0) {
			throw new IllegalArgumentException("배정 시간은 1분 이상이어야 한다: minutes=" + minutes);
		}
	}
}
