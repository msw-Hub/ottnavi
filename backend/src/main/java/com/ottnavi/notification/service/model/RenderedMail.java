package com.ottnavi.notification.service.model;

/**
 * 템플릿으로 만든 메일 내용.
 *
 * @param subject  제목
 * @param htmlBody HTML 본문
 */
public record RenderedMail(String subject, String htmlBody) {
}
