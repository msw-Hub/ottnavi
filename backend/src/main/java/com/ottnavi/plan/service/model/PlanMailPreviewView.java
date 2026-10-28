package com.ottnavi.plan.service.model;

import com.ottnavi.plan.domain.enums.PlanMailPreviewNoMailReason;
import com.ottnavi.plan.domain.enums.PlanMailPreviewType;
import java.util.List;

/**
 * 예시 메일 모음의 서비스 계층 값. 응답 DTO로 바꿔 내보낸다.
 *
 * @param mails        만들 수 있는 예시 메일(구독 안내, 월초 안내 순서)
 * @param noMailReasons 만들 수 없는 메일의 사유
 */
public record PlanMailPreviewView(List<Mail> mails, List<PlanMailPreviewNoMailReason> noMailReasons) {

	/** 예시 메일 한 통(제목과 완성 HTML) */
	public record Mail(PlanMailPreviewType type, String subject, String html) {
	}
}
