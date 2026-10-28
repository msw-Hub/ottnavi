package com.ottnavi.plan.service;

/**
 * 플랜을 현재 플랜(ACTIVE)으로 저장했다는 사건. 저장 트랜잭션이 커밋된 뒤에 가입 안내 알림 리스너(SubscribeGuideListener)가 받는다.
 *
 * @param userId 저장한 사용자
 * @param planId 새로 ACTIVE가 된 플랜
 */
public record PlanActivatedEvent(Long userId, Long planId) {
}
