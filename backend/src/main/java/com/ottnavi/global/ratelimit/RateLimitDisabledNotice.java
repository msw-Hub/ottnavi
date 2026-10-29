package com.ottnavi.global.ratelimit;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

/**
 * app.rate-limit.enabled가 false로 켜졌을 때 기동 로그에 경고를 남긴다.
 * 요청 제한이 꺼지면 공개 API와 데모 로그인(비밀번호 시도 한도)이 한도 없이 열리므로, 운영에 실수로 남지 않았는지 로그로 알아챌 수 있게 한다.
 */
@Slf4j
@Configuration
@ConditionalOnProperty(prefix = "app.rate-limit", name = "enabled", havingValue = "false")
public class RateLimitDisabledNotice {

	@PostConstruct
	void warn() {
		log.warn("요청 제한이 꺼져 있습니다(app.rate-limit.enabled=false). 공개 API·데모 로그인 시도 한도가 적용되지 않습니다. 로컬·테스트 외에는 켜 두세요.");
	}
}
