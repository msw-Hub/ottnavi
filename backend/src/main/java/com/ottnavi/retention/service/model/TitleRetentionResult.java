package com.ottnavi.retention.service.model;

/** 오래된 작품 한 건을 처리한 결과 */
public enum TitleRetentionResult {
	REFRESHED,        // TMDB에 있어서 상세를 다시 받아 저장했다(마지막 수집 시각이 새로워졌다)
	REFRESH_SKIPPED,  // TMDB에는 있지만 상세 갱신을 하지 못했다(시즌 조회 404 등). 안전하게 그대로 두고 다음 주에 다시 묻는다
	PURGED,           // TMDB에서 사라져 찜·플랜과 함께 삭제했다
	DEFERRED          // 일시 오류·경합이라 이번에는 건드리지 않았다. 다음 실행에서 다시 처리한다
}
