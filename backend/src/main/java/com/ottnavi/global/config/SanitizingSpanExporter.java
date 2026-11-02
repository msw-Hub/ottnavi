package com.ottnavi.global.config;

import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.trace.data.DelegatingSpanData;
import io.opentelemetry.sdk.trace.data.EventData;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.data.StatusData;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * span을 내보내기 직전에 예외 상세(메시지·스택)를 지우는 내보내기 래퍼다.
 *
 * 예외가 나면 span에 예외 이벤트(메시지·스택 전체)와 상태 설명(메시지)이 붙는데, 메시지에 이메일 같은 값이 섞일 수 있다.
 * 관측 필터(TracingPrivacyFilter)는 예외가 기록된 뒤에 돌아 지우지 못해서 마지막 관문인 여기서 비운다.
 * 예외의 종류는 낮은 카디널리티 값 exception(클래스 단순 이름)으로 남아 원인 구분에는 충분하다.
 */
public class SanitizingSpanExporter implements SpanExporter {

	private final SpanExporter delegate; // 실제로 내보내는 OTLP 내보내기

	public SanitizingSpanExporter(SpanExporter delegate) {
		this.delegate = delegate;
	}

	@Override
	public CompletableResultCode export(Collection<SpanData> spans) {
		// 1. span마다 예외 상세를 뺀 사본으로 바꾼다
		List<SpanData> sanitized = new ArrayList<>();
		for (SpanData span : spans) {
			sanitized.add(new SanitizedSpanData(span));
		}

		// 2. 실제 내보내기에 넘긴다
		return delegate.export(sanitized);
	}

	@Override
	public CompletableResultCode flush() {
		return delegate.flush();
	}

	@Override
	public CompletableResultCode shutdown() {
		return delegate.shutdown();
	}

	/** 이벤트를 비우고 상태 설명(예외 메시지)을 지운 span 데이터다. 나머지는 원본 그대로 넘긴다. */
	static class SanitizedSpanData extends DelegatingSpanData {

		SanitizedSpanData(SpanData delegate) {
			super(delegate);
		}

		@Override
		public List<EventData> getEvents() {
			return List.of();
		}

		@Override
		public int getTotalRecordedEvents() {
			return 0;
		}

		@Override
		public StatusData getStatus() {
			return StatusData.create(super.getStatus().getStatusCode(), "");
		}
	}
}
