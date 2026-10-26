package com.ottnavi.retention.exception;

import com.ottnavi.global.error.BusinessException;
import com.ottnavi.global.error.ErrorCode;

/** 정리·재계산·수집 중 하나가 이미 실행 중이어서 데이터 정리를 시작할 수 없을 때 던진다. 409 BATCH_ALREADY_RUNNING으로 응답한다 */
public class RetentionAlreadyRunningException extends BusinessException {

	public RetentionAlreadyRunningException(String message) {
		super(message);
	}

	@Override
	public ErrorCode getErrorCode() {
		return ErrorCode.BATCH_ALREADY_RUNNING;
	}
}
