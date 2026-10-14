package com.ottnavi.admin.exception;

import com.ottnavi.global.error.BusinessException;
import com.ottnavi.global.error.ErrorCode;

/** 중단됨이 아닌 실행을 복구하려 할 때 던진다. 409 BATCH_NOT_STOPPED로 응답한다 */
public class BatchNotStoppedException extends BusinessException {

	public BatchNotStoppedException(String message) {
		super(message);
	}

	@Override
	public ErrorCode getErrorCode() {
		return ErrorCode.BATCH_NOT_STOPPED;
	}
}
