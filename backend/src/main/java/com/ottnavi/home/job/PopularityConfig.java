package com.ottnavi.home.job;

import com.ottnavi.home.service.HomeProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** 인기 순위 수집 설정(app.popularity.*)과 홈 공통 목록 설정(app.home.*) 등록 */
@Configuration
@EnableConfigurationProperties({PopularityProperties.class, HomeProperties.class})
public class PopularityConfig {
}
