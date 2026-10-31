package com.ottnavi.user.service.model;

/**
 * USER 권한 회원의 가입·알림 집계(관리자 모니터링용). 서버 안에서만 쓰는 값 묶음이다.
 *
 * @param totalUserCount               현재 남아 있는 회원 수
 * @param todayNewUserCount            오늘 00:00(KST) 이후 가입한 회원 수
 * @param monthNewUserCount            이번 달 1일 00:00(KST) 이후 가입한 회원 수
 * @param notificationEnabledUserCount 알림을 켠 회원 수
 */
public record UserCountStats(long totalUserCount, long todayNewUserCount, long monthNewUserCount,
		long notificationEnabledUserCount) {
}
