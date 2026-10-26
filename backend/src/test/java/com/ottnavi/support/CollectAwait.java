package com.ottnavi.support;

import com.ottnavi.collect.job.CollectLaunchConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * 비동기 수집 실행의 종료를 기다리는 시험 공통 도우미.
 * END_TIME이 DB에 보여도 Job 스레드는 종료 리스너(afterJob) 뒤에 마지막 저장을 한 번 더 한다.
 * 그 저장은 DB의 버전을 다시 읽어 맞춘 뒤 덮어쓰므로(synchronizeStatus), 시험이 중간에 DB를 고치면 되돌려진다.
 * 그래서 상태 조건뿐 아니라 수집 스레드(collect-N)가 끝날 때까지 함께 기다린다.
 */
public final class CollectAwait {

	private static final long POLL_MILLIS = 100;

	private CollectAwait() {
	}

	/** 상태 조건이 참이고 살아 있는 수집 스레드가 없을 때까지 기다린다. 시간이 지나면 남은 스레드 이름을 담아 예외를 던진다 */
	public static void until(BooleanSupplier statusDone, long timeoutMillis, String description)
			throws InterruptedException {
		long deadline = System.currentTimeMillis() + timeoutMillis;
		while (System.currentTimeMillis() < deadline) {
			if (statusDone.getAsBoolean() && liveCollectThreadNames().isEmpty()) {
				return;
			}
			Thread.sleep(POLL_MILLIS);
		}
		throw new IllegalStateException(description + " (" + timeoutMillis / 1000 + "초 초과, 남은 수집 스레드: "
				+ liveCollectThreadNames() + ")");
	}

	/** 살아 있는 수집 스레드가 모두 끝날 때까지만 기다린다 */
	public static void untilNoCollectThread(long timeoutMillis, String description) throws InterruptedException {
		until(() -> true, timeoutMillis, description);
	}

	private static List<String> liveCollectThreadNames() {
		List<String> names = new ArrayList<>();
		for (Thread thread : Thread.getAllStackTraces().keySet()) {
			if (thread.isAlive() && thread.getName().startsWith(CollectLaunchConfig.EXECUTOR_THREAD_PREFIX)) {
				names.add(thread.getName());
			}
		}
		return names;
	}
}
