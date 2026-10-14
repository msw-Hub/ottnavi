package com.ottnavi.admin.exception;

import com.ottnavi.global.error.BusinessException;
import com.ottnavi.global.error.ErrorCode;

/** 같은 날짜·범위에 중단된 실행이 있어 먼저 복구해야 할 때 던진다. 409 BATCH_RECOVERY_REQUIRED로 응답한다 */
public class BatchRecoveryRequiredException extends BusinessException {

	public BatchRecoveryRequiredException(String message) {
		super(message);
	}

	@Override
	public ErrorCode getErrorCode() {
		return ErrorCode.BATCH_RECOVERY_REQUIRED;
	}
}
