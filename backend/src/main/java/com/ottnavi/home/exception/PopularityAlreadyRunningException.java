package com.ottnavi.home.exception;

import com.ottnavi.global.error.BusinessException;
import com.ottnavi.global.error.ErrorCode;

/** 인기 수집·수집·재계산·정리 중 하나가 이미 실행 중이어서 인기 수집을 시작할 수 없을 때 던진다. 409 BATCH_ALREADY_RUNNING으로 응답한다 */
public class PopularityAlreadyRunningException extends BusinessException {

	public PopularityAlreadyRunningException(String message) {
		super(message);
	}

	@Override
	public ErrorCode getErrorCode() {
		return ErrorCode.BATCH_ALREADY_RUNNING;
	}
}
