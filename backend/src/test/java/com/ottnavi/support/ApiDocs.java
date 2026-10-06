package com.ottnavi.support;

import java.nio.file.Path;

/** 저장소의 docs/api 파일 경로를 찾는 테스트 도우미 */
public final class ApiDocs {

	// Gradle이 넘기는 절대 경로. IDE에서 직접 실행하면 속성이 없으므로 backend/ 기준 상대 경로를 쓴다
	private static final String API_DOCS_DIR = System.getProperty("ottnavi.docs.api-dir", "../docs/api");

	private ApiDocs() {
	}

	/** docs/api 아래 파일 경로를 반환한다 */
	public static Path resolve(String fileName) {
		return Path.of(API_DOCS_DIR).resolve(fileName);
	}
}
