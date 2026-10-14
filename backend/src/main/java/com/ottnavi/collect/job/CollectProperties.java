package com.ottnavi.collect.job;

import com.ottnavi.catalog.domain.rule.RuntimeDefaults;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** 수집 설정(app.collect.*). 값은 application.yml에 있다 */
@ConfigurationProperties(prefix = "app.collect")
public record CollectProperties(
        RuntimeDefaults runtimeDefaults) { // runtime이 비었을 때 쓰는 추정 기본값(PRD 11절 25번)
}
