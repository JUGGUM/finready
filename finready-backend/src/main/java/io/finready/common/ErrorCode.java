package io.finready.common;

import org.springframework.http.HttpStatus;

/**
 * openapi.yml v1.4.2 의 Error.code 목록. 값은 계약이므로 임의로 늘리지 않는다.
 *
 * <p>HTTP 상태와 recoverable 을 코드마다 붙여둔다. 계약이 두 가지를 코드 단위로 규정하므로
 * 던지는 쪽마다 정하게 두면 같은 코드가 엔드포인트별로 다른 상태로 나가게 된다.
 *
 * <p>상태코드는 계약이 실제로 쓰는 것만 있다 — 200/201/400/404/409/503.
 * LLM 실패는 502/504 가 아니라 <b>503</b> 이다 (openapi "LLM 1회 재시도 후에도 실패").
 */
public enum ErrorCode {

	INVALID_REQUEST(HttpStatus.BAD_REQUEST),
	TRANSCRIPT_EMPTY(HttpStatus.BAD_REQUEST),
	TRANSCRIPT_TOO_LONG(HttpStatus.BAD_REQUEST),
	OVERRIDE_REASON_REQUIRED(HttpStatus.BAD_REQUEST),
	STAFF_EXPLANATION_CONFIRMATION_REQUIRED(HttpStatus.BAD_REQUEST),
	UNRESOLVED_REASON_REQUIRED(HttpStatus.BAD_REQUEST),
	WARNING_ACKNOWLEDGEMENT_REQUIRED(HttpStatus.BAD_REQUEST),

	SESSION_NOT_FOUND(HttpStatus.NOT_FOUND),
	PRODUCT_NOT_FOUND(HttpStatus.NOT_FOUND),
	RISK_NOT_FOUND(HttpStatus.NOT_FOUND),

	GATE_NOT_OPEN(HttpStatus.CONFLICT),
	INVALID_STATE_TRANSITION(HttpStatus.CONFLICT),
	ATTEMPT_LIMIT_EXCEEDED(HttpStatus.CONFLICT),
	RISK_ALREADY_FINALIZED(HttpStatus.CONFLICT),
	CONCURRENT_SESSION_UPDATE(HttpStatus.CONFLICT, true),

	AI_TIMEOUT(HttpStatus.SERVICE_UNAVAILABLE, true),
	AI_PARSING_FAILED(HttpStatus.SERVICE_UNAVAILABLE, true),

	INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR);

	private final HttpStatus status;

	/** true 면 프론트가 재시도 버튼을 노출한다 (openapi Error.recoverable) */
	private final boolean recoverable;

	ErrorCode(HttpStatus status) {
		this(status, false);
	}

	ErrorCode(HttpStatus status, boolean recoverable) {
		this.status = status;
		this.recoverable = recoverable;
	}

	public HttpStatus status() {
		return status;
	}

	public boolean recoverable() {
		return recoverable;
	}
}
