package io.finready.coverage;

/**
 * openapi.yml v1.4.2 GateStatus. Backend 만 계산하며 클라이언트가 보낸 값은 무시한다.
 *
 * <p>{@code SessionStatus.GATE_BLOCKED} 과 문자열이 같지만 다른 개념이다. PRD §7.6이 두 곳에
 * 같은 문자열을 쓰기로 고정했고, 구현에서는 타입이 달라 혼동이 컴파일 단계에서 막힌다.
 * 그러니 두 enum 사이에 변환 헬퍼를 만들지 말 것 — 만드는 순간 그 방어가 사라진다.
 */
public enum GateStatus {

	/** Coverage 분석 전(DRAFT) */
	NOT_EVALUATED,

	GATE_BLOCKED,

	READY_FOR_UNDERSTANDING,

	/** 막고 있던 Risk 를 직원이 Override 해서 열린 상태. 그냥 열린 것과 구분해 기록한다 */
	READY_WITH_STAFF_OVERRIDE
}
