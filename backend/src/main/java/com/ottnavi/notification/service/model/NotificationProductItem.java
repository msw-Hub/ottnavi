package com.ottnavi.notification.service.model;

import java.util.List;

/**
 * 알림 메일에 나오는 구독 상품 한 줄(payload 안의 값).
 *
 * @param productName  상품 이름(예: 넷플릭스 스탠다드)
 * @param serviceNames 상품에 포함된 서비스 이름(단품은 1개, 번들은 여러 개)
 * @param appliedPrice 월 요금(원)
 */
public record NotificationProductItem(String productName, List<String> serviceNames, int appliedPrice) {
}
