package io.finready.session;

/**
 * consultation_session.status — TRD §6 Canonical Enum Contract.
 * V1__init.sql 의 ck_session_status 와 값이 정확히 일치해야 한다.
 *
 * <p>전이는 common.StateMachine 단일 지점을 통과한다(CLAUDE.md 규칙 7).
 * 서비스 코드에 상태 분기를 흩뿌리지 않는다.
 */
public enum SessionStatus {
	DRAFT,
	COVERAGE_ANALYZED,
	GATE_BLOCKED,
	UNDERSTANDING_IN_PROGRESS,
	AWAITING_STAFF_REVIEW,
	SESSION_CLOSED_BY_STAFF,
	SESSION_CLOSED_WITH_UNRESOLVED
}
