package com.ottnavi.infra.mail.exception;

/** 메일 발송 실패(연결·인증·주소 오류 등). 원인은 cause로 남기고, 호출한 알림 발송기가 재시도 여부를 정한다 */
public class MailSendException extends RuntimeException {

	public MailSendException(String message) {
		super(message);
	}

	public MailSendException(String message, Throwable cause) {
		super(message, cause);
	}
}
