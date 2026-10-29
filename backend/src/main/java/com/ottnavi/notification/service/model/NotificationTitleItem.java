package com.ottnavi.notification.service.model;

/**
 * 알림 메일에 나오는 작품 한 줄(payload 안의 값).
 *
 * @param title        작품 제목
 * @param seasonNumber 시즌 번호. 영화·시즌 없는 작품은 null
 * @param serviceName  보는 서비스 이름
 */
public record NotificationTitleItem(String title, Integer seasonNumber, String serviceName) {
}
