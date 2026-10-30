package com.ottnavi.global.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.SocketTimeoutException;
import org.junit.jupiter.api.Test;

/**
 * 예외 체인에서 가장 안쪽 원인의 클래스 이름을 찾는 도구를 확인한다.
 * 막으려는 사고: 래핑된 예외의 진짜 원인이 로그에 안 남는 일, 순환 cause에서 무한 반복하는 일, null에서 NPE가 나는 일.
 */
class ExceptionRootCauseTest {

	@Test
	void cause가_없으면_자기_클래스_전체_이름을_돌려준다() {
		// given
		Exception exception = new IllegalStateException("실패");

		// when
		String name = ExceptionRootCause.rootCauseName(exception);

		// then
		assertThat(name).isEqualTo("java.lang.IllegalStateException");
	}

	@Test
	void 깊은_체인이면_맨_아래_원인의_이름을_돌려준다() {
		// given
		Exception exception = new RuntimeException("바깥", new IOException("중간", new SocketTimeoutException("안쪽")));

		// when
		String name = ExceptionRootCause.rootCauseName(exception);

		// then
		assertThat(name).isEqualTo("java.net.SocketTimeoutException");
	}

	@Test
	void cause가_서로를_가리키는_순환이어도_끝난다() {
		// given
		RuntimeException first = new RuntimeException("첫째");
		RuntimeException second = new RuntimeException("둘째", first);
		first.initCause(second);

		// when
		String name = ExceptionRootCause.rootCauseName(first);

		// then
		assertThat(name).isEqualTo("java.lang.RuntimeException");
	}

	@Test
	void null이면_없음을_돌려준다() {
		// given & when
		String name = ExceptionRootCause.rootCauseName(null);

		// then
		assertThat(name).isEqualTo("없음");
	}
}
