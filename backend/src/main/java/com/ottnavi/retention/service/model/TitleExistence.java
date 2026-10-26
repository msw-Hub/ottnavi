package com.ottnavi.retention.service.model;

/** TMDB에 작품이 아직 있는지 다시 물은 결과 */
public enum TitleExistence {
	EXISTS,  // 응답이 왔다(작품이 있다)
	GONE,    // 작품 단위 조회가 404다(TMDB에서 사라졌다)
	UNKNOWN  // 429·5xx·연결 실패·빈 응답처럼 있는지 없는지 알 수 없다. 절대 삭제 근거로 쓰지 않는다
}
