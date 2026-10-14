package com.ottnavi.global.error;

/** 관리자 API 등에서 없는 데이터를 요청했을 때 던진다. 404 RESOURCE_NOT_FOUND로 응답한다 */
public class ResourceNotFoundException extends BusinessException {

	public ResourceNotFoundException(String message) {
		super(message);
	}

	@Override
	public ErrorCode getErrorCode() {
		return ErrorCode.RESOURCE_NOT_FOUND;
	}
}
