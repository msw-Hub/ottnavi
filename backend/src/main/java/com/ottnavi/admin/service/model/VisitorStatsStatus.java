package com.ottnavi.admin.service.model;

/** 방문자 통계 조회 상태(계약 VisitorStatsStatus) */
public enum VisitorStatsStatus {
	OK,          // 숫자가 채워졌다
	DISABLED,    // 서버에 Google Analytics 연동 설정이 없다
	UNAVAILABLE  // 조회가 실패했거나 시간이 초과됐다
}
