package com.ottnavi.plan.dto.response;

import com.ottnavi.plan.domain.enums.PlanMailPreviewNoMailReason;
import com.ottnavi.plan.domain.enums.PlanMailPreviewType;
import com.ottnavi.plan.service.model.PlanMailPreviewView;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.ArrayList;
import java.util.List;

/**
 * 데모 계정용 예시 메일 모음(계약 PlanMailPreview).
 *
 * @param mails         만들 수 있는 예시 메일. 구독 안내, 월초 안내 순서
 * @param noMailReasons 만들 수 없는 메일의 사유. 모두 만들었으면 빈 배열
 */
@Schema(name = "PlanMailPreview")
public record PlanMailPreviewResponse(
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<Mail> mails,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<PlanMailPreviewNoMailReason> noMailReasons) {

	/** 서비스 계층 값을 응답으로 바꾼다 */
	public static PlanMailPreviewResponse from(PlanMailPreviewView view) {
		List<Mail> mails = new ArrayList<>();
		for (PlanMailPreviewView.Mail mail : view.mails()) {
			mails.add(new Mail(mail.type(), mail.subject(), mail.html()));
		}
		return new PlanMailPreviewResponse(mails, new ArrayList<>(view.noMailReasons()));
	}

	/**
	 * 예시 메일 1통(계약 PlanMailPreviewMail).
	 *
	 * @param type    메일 종류
	 * @param subject 메일 제목
	 * @param html    메일 본문 HTML(이메일용 완성 문서)
	 */
	@Schema(name = "PlanMailPreviewMail")
	public record Mail(
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) PlanMailPreviewType type,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "[OTT내비] 저장한 구독 플랜 안내") String subject,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String html) {
	}
}
