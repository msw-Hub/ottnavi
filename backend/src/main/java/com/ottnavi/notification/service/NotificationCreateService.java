package com.ottnavi.notification.service;

import com.ottnavi.notification.domain.enums.NotificationType;
import com.ottnavi.notification.domain.repository.NotificationOutboxRepository;
import com.ottnavi.user.service.UserQueryService;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * 알림 행(outbox)을 만드는 유일한 관문(Task 073). 알림 설정 확인, payload 직렬화, 중복 차단 삽입을 한곳에서 맡는다.
 * 모든 유형(SUBSCRIBE_GUIDE, MONTHLY, PLAN_CHANGE)이 이 관문을 지난다.
 *
 * <pre>
 * 알림이 꺼진 사용자(또는 설정 행이 없는 사용자) → 행을 만들지 않음(빈 값)
 * 같은 dedupe_key가 이미 있음 → 행을 만들지 않음(빈 값, 예외 없음)
 * 그 외 → PENDING 행을 만들고 그 ID를 돌려줌
 * </pre>
 *
 * 켜 둔 알림만 새로 만들어진다. 이미 만들어 둔 PENDING 행은 사용자가 알림을 꺼도 그대로 발송되고, 다시 켜도 지난 달 몫을 소급해 만들지 않는다.
 */
@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class NotificationCreateService {

	private final NotificationOutboxRepository outboxRepository;
	private final UserQueryService userQueryService;
	private final Clock clock;
	private final ObjectMapper objectMapper = JsonMapper.builder().build();

	/**
	 * 호출한 쪽의 쓰기 트랜잭션에 합류해 알림 행을 만든다(재계산 쓰기와 같은 트랜잭션).
	 * 여기서 예외가 나면 호출한 쪽의 트랜잭션도 되돌아간다.
	 *
	 * @param payload 메일 재료 record. JSON으로 저장된다
	 * @return 새로 만든 행의 ID. 알림이 꺼져 있거나 이미 있으면 빈 값
	 */
	@Transactional
	public Optional<Long> create(Long userId, NotificationType type, String dedupeKey, Object payload) {
		return insert(userId, type, dedupeKey, payload);
	}

	/**
	 * 새 트랜잭션에서 알림 행을 만들고 바로 커밋한다. 저장 커밋 뒤(AFTER_COMMIT)에는 원래 트랜잭션이 끝나 있어
	 * 그 안에서는 쓰기가 반영되지 않으므로 가입 안내가 이 메서드를 쓴다.
	 *
	 * <p>users 행을 잠그지 않는 이유: 알림 행의 user_id FK가 ON DELETE CASCADE(V7)이고 INSERT가 FK를 확인하므로
	 * 탈퇴 삭제와 줄이 선다. 탈퇴 뒤에는 설정 행이 없어 isNotificationEnabled가 false라 고아 행이 생기지 않는다.
	 * (재계산 경로는 PlanRecalcWriter.write가 이미 users 행을 잠가 create를 호출한다.)
	 */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public Optional<Long> createInNewTransaction(Long userId, NotificationType type, String dedupeKey, Object payload) {
		return insert(userId, type, dedupeKey, payload);
	}

	private Optional<Long> insert(Long userId, NotificationType type, String dedupeKey, Object payload) {
		// 1. 알림을 끈 사용자는 만들지 않는다
		if (!userQueryService.isNotificationEnabled(userId)) {
			log.info("알림이 꺼져 있어 만들지 않는다: userId={}, type={}", userId, type);
			return Optional.empty();
		}

		// 2. payload를 JSON 문자열로 바꾼다
		String payloadJson = objectMapper.writeValueAsString(payload);

		// 3. 같은 키가 없을 때만 넣는다(충돌은 DB가 조용히 넘긴다)
		Instant now = Instant.now(clock);
		int inserted = outboxRepository.insertIfAbsent(userId, type.name(), dedupeKey, payloadJson, now);
		if (inserted == 0) {
			log.info("이미 만들어진 알림이라 건너뛴다: dedupeKey={}", dedupeKey);
			return Optional.empty();
		}

		// 4. 방금 넣은 행의 ID를 찾아 돌려준다
		log.info("알림을 만들었다: userId={}, type={}, dedupeKey={}", userId, type, dedupeKey);
		return outboxRepository.findIdByDedupeKey(dedupeKey);
	}
}
